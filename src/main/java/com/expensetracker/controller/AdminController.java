package com.expensetracker.controller;

import com.expensetracker.entity.*;
import com.expensetracker.entity.Role;
import com.expensetracker.entity.User;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.RoleRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository,
                          RoleRepository roleRepository,
                          TransactionRepository transactionRepository,
                          CategoryRepository categoryRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        User currentUser = getCurrentUser();
        long totalUsers = userRepository.count();
        long totalTransactions = transactionRepository.count();

        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        // Подсчет общей статистики через всех пользователей
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            BigDecimal userIncome = transactionRepository.sumIncomeByUserAndDateAfter(user, startOfMonth);
            BigDecimal userExpense = transactionRepository.sumExpenseByUserAndDateAfter(user, startOfMonth);
            if (userIncome != null) totalIncome = totalIncome.add(userIncome);
            if (userExpense != null) totalExpense = totalExpense.add(userExpense);
        }

        // Статистика по пользователям
        Map<String, Long> usersByRole = allUsers.stream()
                .flatMap(user -> user.getRoles().stream())
                .collect(Collectors.groupingBy(Role::getName, Collectors.counting()));

        model.addAttribute("user", currentUser);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("usersByRole", usersByRole);

        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(Model model) {
        User currentUser = getCurrentUser();
        List<User> allUsers = userRepository.findAll();
        List<Role> allRoles = roleRepository.findAll();

        model.addAttribute("user", currentUser);
        model.addAttribute("users", allUsers);
        model.addAttribute("allRoles", allRoles);
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        User currentUser = getCurrentUser();
        Optional<User> targetUser = userRepository.findById(id);

        if (targetUser.isEmpty()) {
            return "redirect:/admin/users";
        }

        // Статистика пользователя
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        BigDecimal userIncome = transactionRepository.sumIncomeByUserAndDateAfter(targetUser.get(), startOfMonth);
        BigDecimal userExpense = transactionRepository.sumExpenseByUserAndDateAfter(targetUser.get(), startOfMonth);

        if (userIncome == null) userIncome = BigDecimal.ZERO;
        if (userExpense == null) userExpense = BigDecimal.ZERO;

        long userTransactionsCount = transactionRepository.findByUserOrderByDateDesc(targetUser.get()).size();

        model.addAttribute("user", currentUser);
        model.addAttribute("targetUser", targetUser.get());
        model.addAttribute("userIncome", userIncome);
        model.addAttribute("userExpense", userExpense);
        model.addAttribute("userTransactionsCount", userTransactionsCount);
        model.addAttribute("allRoles", roleRepository.findAll());

        return "admin/user-detail";
    }

    @PostMapping("/users/{id}/update")
    public String updateUser(@PathVariable Long id,
                            @RequestParam String firstName,
                            @RequestParam(required = false) String lastName,
                            @RequestParam String email,
                            @RequestParam(required = false) String password,
                            @RequestParam(required = false) List<Long> roleIds,
                            RedirectAttributes redirectAttributes) {
        try {
            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден!");
                return "redirect:/admin/users";
            }

            User user = userOpt.get();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);

            if (password != null && !password.isEmpty()) {
                user.setPassword(passwordEncoder.encode(password));
            }

            if (roleIds != null && !roleIds.isEmpty()) {
                List<Role> roles = roleRepository.findAllById(roleIds);
                user.setRoles(roles);
            }

            userRepository.save(user);
            redirectAttributes.addFlashAttribute("success", "Пользователь обновлен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении: " + e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден!");
                return "redirect:/admin/users";
            }

            User currentUser = getCurrentUser();
            if (userOpt.get().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Вы не можете удалить самого себя!");
                return "redirect:/admin/users";
            }

            userRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Пользователь удален!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при удалении: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        // Здесь можно добавить логику блокировки/разблокировки пользователей
        // Пока просто заглушка
        redirectAttributes.addFlashAttribute("success", "Статус пользователя изменен!");
        return "redirect:/admin/users/" + id;
    }

    // Управление категориями
    @GetMapping("/categories")
    public String categories(Model model) {
        User currentUser = getCurrentUser();
        List<Category> allCategories = categoryRepository.findAll();
        List<User> allUsers = userRepository.findAll();

        model.addAttribute("user", currentUser);
        model.addAttribute("categories", allCategories);
        model.addAttribute("allUsers", allUsers);
        return "admin/categories";
    }

    @PostMapping("/categories/add")
    public String addCategory(@RequestParam String name,
                            @RequestParam(required = false) String description,
                            @RequestParam(required = false) String color,
                            @RequestParam Long userId,
                            RedirectAttributes redirectAttributes) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден!");
                return "redirect:/admin/categories";
            }

            Category category = new Category();
            category.setName(name);
            category.setDescription(description);
            category.setColor(color != null && !color.isEmpty() ? color : "#667eea");
            category.setUser(userOpt.get());

            categoryRepository.save(category);
            redirectAttributes.addFlashAttribute("success", "Категория добавлена!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при добавлении категории: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/update")
    public String updateCategory(@PathVariable Long id,
                               @RequestParam String name,
                               @RequestParam(required = false) String description,
                               @RequestParam(required = false) String color,
                               @RequestParam Long userId,
                               RedirectAttributes redirectAttributes) {
        try {
            Optional<Category> categoryOpt = categoryRepository.findById(id);
            if (categoryOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Категория не найдена!");
                return "redirect:/admin/categories";
            }

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден!");
                return "redirect:/admin/categories";
            }

            Category category = categoryOpt.get();
            category.setName(name);
            category.setDescription(description);
            if (color != null && !color.isEmpty()) {
                category.setColor(color);
            }
            category.setUser(userOpt.get());

            categoryRepository.save(category);
            redirectAttributes.addFlashAttribute("success", "Категория обновлена!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении категории: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<Category> categoryOpt = categoryRepository.findById(id);
            if (categoryOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Категория не найдена!");
                return "redirect:/admin/categories";
            }

            categoryRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Категория удалена!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при удалении категории: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    // Просмотр всех транзакций
    @GetMapping("/transactions")
    public String transactions(Model model,
                              @RequestParam(required = false) Long userId,
                              @RequestParam(required = false) String type,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate) {
        User currentUser = getCurrentUser();
        List<User> allUsers = userRepository.findAll();
        List<Transaction> transactions;

        if (userId != null) {
            Optional<User> targetUser = userRepository.findById(userId);
            if (targetUser.isPresent()) {
                transactions = transactionRepository.findByUserOrderByDateDesc(targetUser.get());
            } else {
                transactions = transactionRepository.findAll();
            }
        } else {
            transactions = transactionRepository.findAll();
        }

        // Фильтрация по типу
        if (type != null && !type.isEmpty()) {
            Transaction.TransactionType transactionType = Transaction.TransactionType.valueOf(type);
            transactions = transactions.stream()
                    .filter(t -> t.getType() == transactionType)
                    .collect(Collectors.toList());
        }

        // Фильтрация по датам
        if (startDate != null && !startDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate);
            transactions = transactions.stream()
                    .filter(t -> !t.getDate().isBefore(start))
                    .collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isEmpty()) {
            LocalDate end = LocalDate.parse(endDate);
            transactions = transactions.stream()
                    .filter(t -> !t.getDate().isAfter(end))
                    .collect(Collectors.toList());
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("transactions", transactions);
        model.addAttribute("allUsers", allUsers);
        model.addAttribute("selectedUserId", userId);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedStartDate", startDate);
        model.addAttribute("selectedEndDate", endDate);

        return "admin/transactions";
    }

    @GetMapping("/reports")
    public String reports(Model model,
                         @RequestParam(required = false) String period) {
        User currentUser = getCurrentUser();
        LocalDate startDate;
        LocalDate endDate = LocalDate.now();

        if (period == null || period.equals("month")) {
            startDate = LocalDate.now().withDayOfMonth(1);
        } else if (period.equals("quarter")) {
            int quarter = (LocalDate.now().getMonthValue() - 1) / 3;
            startDate = LocalDate.now().withMonth(quarter * 3 + 1).withDayOfMonth(1);
        } else { // year
            startDate = LocalDate.now().withDayOfYear(1);
        }

        // Общая статистика по всем пользователям
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        long totalTransactions = 0;

        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            List<Transaction> userTransactions = transactionRepository
                    .findByUserAndDateBetweenOrderByDateDesc(user, startDate, endDate);

            BigDecimal userIncome = userTransactions.stream()
                    .filter(t -> t.getType() == Transaction.TransactionType.INCOME)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal userExpense = userTransactions.stream()
                    .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalIncome = totalIncome.add(userIncome);
            totalExpense = totalExpense.add(userExpense);
            totalTransactions += userTransactions.size();
        }

        // Статистика по категориям
        List<Transaction> allTransactions = transactionRepository.findAll().stream()
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                .collect(Collectors.toList());

        Map<String, CategoryStat> categoryStatsMap = new HashMap<>();
        for (Transaction transaction : allTransactions) {
            if (transaction.getCategory() != null) {
                String categoryName = transaction.getCategory().getName();
                CategoryStat stat = categoryStatsMap.getOrDefault(categoryName,
                    new CategoryStat(categoryName, transaction.getCategory().getColor()));
                stat.amount = stat.amount.add(transaction.getAmount());
                categoryStatsMap.put(categoryName, stat);
            }
        }

        List<CategoryStat> categoryStats = categoryStatsMap.values().stream()
                .sorted((a, b) -> b.amount.compareTo(a.amount))
                .collect(Collectors.toList());

        BigDecimal totalExpenseForPercentage = totalExpense.compareTo(BigDecimal.ZERO) > 0 ? totalExpense : BigDecimal.ONE;
        for (CategoryStat stat : categoryStats) {
            stat.percentage = stat.amount.divide(totalExpenseForPercentage, 2, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).intValue();
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("categoryStats", categoryStats);
        model.addAttribute("period", period != null ? period : "month");
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "admin/reports";
    }

    // Вспомогательный класс для статистики категорий
    public static class CategoryStat {
        private String name;
        private String color;
        private BigDecimal amount = BigDecimal.ZERO;
        private int percentage = 0;

        public CategoryStat(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public String getColor() { return color; }
        public BigDecimal getAmount() { return amount; }
        public int getPercentage() { return percentage; }
    }
}

