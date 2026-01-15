package com.expensetracker.controller;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.Income;
import com.expensetracker.entity.Role;
import com.expensetracker.entity.User;
import com.expensetracker.entity.Role;
import com.expensetracker.entity.User;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.IncomeRepository;
import com.expensetracker.repository.RoleRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.StatisticsService;
import com.expensetracker.dto.FinancialItem;
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
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final StatisticsService statisticsService;

    public AdminController(UserRepository userRepository,
                          RoleRepository roleRepository,
                          IncomeRepository incomeRepository,
                          ExpenseRepository expenseRepository,
                          CategoryRepository categoryRepository,
                          PasswordEncoder passwordEncoder,
                          StatisticsService statisticsService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.statisticsService = statisticsService;
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

        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        // Подсчет общей статистики через всех пользователей
        List<User> allUsers = userRepository.findAll();
        StatisticsService.OverallStatistics overallStats = statisticsService.calculateOverallStatistics(allUsers, startOfMonth);
        
        long totalItems = overallStats.getTotalItems();

        // Статистика по пользователям
        Map<String, Long> usersByRole = allUsers.stream()
                .flatMap(user -> user.getRoles().stream())
                .collect(Collectors.groupingBy(Role::getName, Collectors.counting()));

        model.addAttribute("user", currentUser);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalTransactions", totalItems);
        model.addAttribute("totalIncome", overallStats.getTotalIncome());
        model.addAttribute("totalExpense", overallStats.getTotalExpense());
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
        BigDecimal userIncome = statisticsService.calculateTotalIncome(targetUser.get(), startOfMonth);
        BigDecimal userExpense = statisticsService.calculateTotalExpense(targetUser.get(), startOfMonth);

        List<Income> userIncomes = incomeRepository.findByUserOrderByDateDesc(targetUser.get());
        List<Expense> userExpenses = expenseRepository.findByUserOrderByDateDesc(targetUser.get());
        long userTransactionsCount = userIncomes.size() + userExpenses.size();

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
            User targetUser = userOpt.get();
            
            if (targetUser.getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Вы не можете удалить самого себя!");
                return "redirect:/admin/users";
            }

            // Проверка, является ли удаляемый пользователь администратором
            boolean isTargetAdmin = targetUser.getRoles().stream()
                    .anyMatch(role -> "ROLE_ADMIN".equals(role.getName()));
            
            if (isTargetAdmin) {
                redirectAttributes.addFlashAttribute("error", "Вы не можете удалить другого администратора!");
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
            // Используем указанный цвет или генерируем уникальный на основе имени
            category.setColor(color != null && !color.isEmpty() 
                    ? color 
                    : com.expensetracker.util.ColorGenerator.generateColorForCategory(name));
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
            // Используем указанный цвет или генерируем уникальный на основе имени
            category.setColor(color != null && !color.isEmpty() 
                    ? color 
                    : com.expensetracker.util.ColorGenerator.generateColorForCategory(name));
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

    // Просмотр всех транзакций (доходы и расходы)
    @GetMapping("/transactions")
    public String transactions(Model model,
                              @RequestParam(required = false) Long userId,
                              @RequestParam(required = false) String type,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate) {
        User currentUser = getCurrentUser();
        List<User> allUsers = userRepository.findAll();
        List<FinancialItem> items = new ArrayList<>();

        // Определяем список пользователей для выборки
        List<User> targetUsers;
        if (userId != null) {
            Optional<User> targetUser = userRepository.findById(userId);
            targetUsers = targetUser.map(Collections::singletonList).orElse(allUsers);
        } else {
            targetUsers = allUsers;
        }

        // Собираем все доходы и расходы
        for (User user : targetUsers) {
            List<Income> incomes = incomeRepository.findByUserOrderByDateDesc(user);
            List<Expense> expenses = expenseRepository.findByUserOrderByDateDesc(user);
            
            for (Income income : incomes) {
                items.add(new FinancialItem(income));
            }
            
            for (Expense expense : expenses) {
                items.add(new FinancialItem(expense));
            }
        }

        // Фильтрация по типу
        if (type != null && !type.isEmpty()) {
            items = items.stream()
                    .filter(item -> item.getType().equals(type))
                    .collect(Collectors.toList());
        }

        // Фильтрация по датам
        if (startDate != null && !startDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate);
            items = items.stream()
                    .filter(item -> !item.getDate().isBefore(start))
                    .collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isEmpty()) {
            LocalDate end = LocalDate.parse(endDate);
            items = items.stream()
                    .filter(item -> !item.getDate().isAfter(end))
                    .collect(Collectors.toList());
        }

        // Сортировка по дате (новые сначала)
        items.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        model.addAttribute("user", currentUser);
        model.addAttribute("items", items);
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

        // Используем последние N дней вместо начала периода
        if (period == null || period.equals("month")) {
            startDate = endDate.minusDays(30); // Последние 31 день
        } else if (period.equals("quarter")) {
            startDate = endDate.minusDays(92); // Последние 93 дня
        } else { // year
            startDate = endDate.minusDays(364); // Последние 365 дней
        }

        // Общая статистика по всем пользователям
        List<User> allUsers = userRepository.findAll();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        long totalItems = 0;

        for (User user : allUsers) {
            List<Income> userIncomes = incomeRepository.findByUserAndDateBetween(user, startDate, endDate);
            List<Expense> userExpenses = expenseRepository.findByUserAndDateBetween(user, startDate, endDate);

            BigDecimal userIncome = userIncomes.stream()
                    .map(Income::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal userExpense = userExpenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalIncome = totalIncome.add(userIncome);
            totalExpense = totalExpense.add(userExpense);
            totalItems += userIncomes.size() + userExpenses.size();
        }

        // Статистика по категориям
        List<Expense> allExpenses = new ArrayList<>();
        for (User user : allUsers) {
            List<Expense> userExpenses = expenseRepository.findByUserAndDateBetween(user, startDate, endDate);
            allExpenses.addAll(userExpenses);
        }

        Map<String, CategoryStat> categoryStatsMap = new HashMap<>();
        BigDecimal noCategoryAmount = BigDecimal.ZERO;
        
        for (Expense expense : allExpenses) {
            if (expense.getCategory() != null) {
                String categoryName = expense.getCategory().getName();
                CategoryStat stat = categoryStatsMap.getOrDefault(categoryName,
                    new CategoryStat(categoryName, expense.getCategory().getColor()));
                stat.amount = stat.amount.add(expense.getAmount());
                categoryStatsMap.put(categoryName, stat);
            } else {
                noCategoryAmount = noCategoryAmount.add(expense.getAmount());
            }
        }
        
        // Добавляем категорию "Без категории", если есть расходы без категории
        if (noCategoryAmount.compareTo(BigDecimal.ZERO) > 0) {
            CategoryStat noCategoryStat = new CategoryStat("Без категории", "#95a5a6");
            noCategoryStat.amount = noCategoryAmount;
            categoryStatsMap.put("Без категории", noCategoryStat);
        }

        BigDecimal totalExpenseForPercentage = totalExpense.compareTo(BigDecimal.ZERO) > 0 ? totalExpense : BigDecimal.ONE;
        
        List<CategoryStat> categoryStats = categoryStatsMap.values().stream()
                .peek(stat -> {
                    stat.percentage = stat.amount.divide(totalExpenseForPercentage, 2, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).intValue();
                })
                .sorted((a, b) -> b.amount.compareTo(a.amount))
                .collect(Collectors.toList());

        // Гарантируем уникальные цвета для всех категорий на графике
        List<String> categoryNames = categoryStats.stream()
                .map(CategoryStat::getName)
                .collect(Collectors.toList());
        List<String> existingColors = categoryStats.stream()
                .map(CategoryStat::getColor)
                .collect(Collectors.toList());
        
        java.util.Map<String, String> uniqueColorMap = 
                com.expensetracker.util.ColorGenerator.ensureUniqueColors(categoryNames, existingColors);
        
        // Обновляем цвета в categoryStats
        for (CategoryStat stat : categoryStats) {
            String uniqueColor = uniqueColorMap.get(stat.getName());
            if (uniqueColor != null) {
                stat.setColor(uniqueColor);
            }
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("totalTransactions", totalItems);
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
        public void setColor(String color) { this.color = color; }
        public BigDecimal getAmount() { return amount; }
        public int getPercentage() { return percentage; }
    }
}

