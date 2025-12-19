package com.expensetracker.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expensetracker.dto.BudgetDto;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.User;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.AiExpenseAnalysisService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/user")
public class UserController {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final PasswordEncoder passwordEncoder;
    private final AiExpenseAnalysisService aiExpenseAnalysisService;

    public UserController(UserRepository userRepository,
                          TransactionRepository transactionRepository,
                          CategoryRepository categoryRepository,
                          BudgetRepository budgetRepository,
                          PasswordEncoder passwordEncoder,
                          AiExpenseAnalysisService aiExpenseAnalysisService) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.budgetRepository = budgetRepository;
        this.passwordEncoder = passwordEncoder;
        this.aiExpenseAnalysisService = aiExpenseAnalysisService;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        User user = getCurrentUser();
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);

        // Статистика
        BigDecimal incomeTotal = transactionRepository.sumIncomeByUserAndDateAfter(user, startOfMonth);
        BigDecimal expenseTotal = transactionRepository.sumExpenseByUserAndDateAfter(user, startOfMonth);
        
        if (incomeTotal == null) incomeTotal = BigDecimal.ZERO;
        if (expenseTotal == null) expenseTotal = BigDecimal.ZERO;
        
        BigDecimal balance = incomeTotal.subtract(expenseTotal);

        // Последние транзакции
        List<Transaction> recentTransactions = transactionRepository.findTop10ByUserOrderByDateDesc(user);

        // Активные бюджеты с расчетом потраченных средств
        List<Budget> activeBudgets = budgetRepository.findActiveBudgetsByUser(user, LocalDate.now());
        List<BudgetDto> budgetDtos = new ArrayList<>();
        
        for (Budget budget : activeBudgets) {
            BigDecimal spent = BigDecimal.ZERO;
            if (budget.getCategory() != null) {
                List<Transaction> categoryTransactions = transactionRepository
                        .findByUserAndDateBetweenOrderByDateDesc(user, budget.getStartDate(), budget.getEndDate())
                        .stream()
                        .filter(t -> t.getCategory() != null && t.getCategory().getId().equals(budget.getCategory().getId()))
                        .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                        .collect(Collectors.toList());
                spent = categoryTransactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            } else {
                // Если бюджет без категории, считаем все расходы за период
                List<Transaction> periodTransactions = transactionRepository
                        .findByUserAndDateBetweenOrderByDateDesc(user, budget.getStartDate(), budget.getEndDate())
                        .stream()
                        .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                        .collect(Collectors.toList());
                spent = periodTransactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            budgetDtos.add(new BudgetDto(budget, spent));
        }

        // Категории для формы
        List<Category> categories = categoryRepository.findByUserOrderByNameAsc(user);

        // Расчет использования бюджета
        BigDecimal budgetTotal = activeBudgets.stream()
                .map(Budget::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int budgetUsage = 0;
        if (!activeBudgets.isEmpty() && budgetTotal.compareTo(BigDecimal.ZERO) > 0) {
            budgetUsage = expenseTotal.divide(budgetTotal, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).intValue();
        }

        model.addAttribute("user", user);
        model.addAttribute("incomeTotal", incomeTotal);
        model.addAttribute("expenseTotal", expenseTotal);
        model.addAttribute("balance", balance);
        model.addAttribute("recentTransactions", recentTransactions);
        model.addAttribute("activeBudgets", budgetDtos);
        model.addAttribute("categories", categories);
        model.addAttribute("budgetTotal", budgetTotal);
        model.addAttribute("budgetUsage", budgetUsage);

        return "user/dashboard";
    }

    @GetMapping("/transactions")
    public String transactions(Model model) {
        User user = getCurrentUser();
        List<Transaction> transactions = transactionRepository.findByUserOrderByDateDesc(user);
        List<Category> categories = categoryRepository.findByUserOrderByNameAsc(user);

        model.addAttribute("user", user);
        model.addAttribute("transactions", transactions);
        model.addAttribute("categories", categories);
        return "user/transactions";
    }

    @PostMapping("/transactions/add")
    public String addTransaction(@RequestParam BigDecimal amount,
                                @RequestParam String type,
                                @RequestParam(required = false) String description,
                                @RequestParam LocalDate date,
                                @RequestParam(required = false) Long categoryId,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser();
            Transaction transaction = new Transaction();
            transaction.setAmount(amount);
            transaction.setType(Transaction.TransactionType.valueOf(type));
            transaction.setDescription(description);
            transaction.setDate(date != null ? date : LocalDate.now());
            transaction.setUser(user);

            if (categoryId != null) {
                Optional<Category> category = categoryRepository.findById(categoryId);
                category.ifPresent(transaction::setCategory);
            }

            transactionRepository.save(transaction);
            redirectAttributes.addFlashAttribute("success", "Транзакция успешно добавлена!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при добавлении транзакции: " + e.getMessage());
        }
        // Определяем, откуда пришел запрос, и перенаправляем соответственно
        String referer = request.getHeader("Referer");
        if (referer != null && referer.contains("/transactions")) {
            return "redirect:/user/transactions";
        }
        return "redirect:/user/dashboard";
    }

    @PostMapping("/transactions/{id}/delete")
    public String deleteTransaction(@PathVariable Long id, 
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Optional<Transaction> transaction = transactionRepository.findById(id);
        
        if (transaction.isPresent() && transaction.get().getUser().getId().equals(user.getId())) {
            transactionRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Транзакция удалена!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Транзакция не найдена!");
        }
        
        // Определяем, откуда пришел запрос
        String referer = request.getHeader("Referer");
        if (referer != null && referer.contains("/transactions")) {
            return "redirect:/user/transactions";
        }
        return "redirect:/user/dashboard";
    }

    @GetMapping("/categories")
    public String categories(Model model) {
        User user = getCurrentUser();
        List<Category> categories = categoryRepository.findByUserOrderByNameAsc(user);
        model.addAttribute("user", user);
        model.addAttribute("categories", categories);
        return "user/categories";
    }

    @PostMapping("/categories/add")
    public String addCategory(@RequestParam String name,
                            @RequestParam(required = false) String description,
                            @RequestParam(required = false) String color,
                            RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        category.setColor(color != null && !color.isEmpty() ? color : "#667eea");
        category.setUser(user);

        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("success", "Категория добавлена!");
        return "redirect:/user/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Optional<Category> category = categoryRepository.findById(id);
        
        if (category.isPresent() && category.get().getUser().getId().equals(user.getId())) {
            categoryRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Категория удалена!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Категория не найдена!");
        }
        
        return "redirect:/user/categories";
    }

    @GetMapping("/budgets")
    public String budgets(Model model) {
        User user = getCurrentUser();
        List<Budget> budgets = budgetRepository.findByUser(user);
        List<Category> categories = categoryRepository.findByUserOrderByNameAsc(user);
        
        List<BudgetDto> budgetDtos = new ArrayList<>();
        for (Budget budget : budgets) {
            BigDecimal spent = BigDecimal.ZERO;
            if (budget.getCategory() != null) {
                List<Transaction> categoryTransactions = transactionRepository
                        .findByUserAndDateBetweenOrderByDateDesc(user, budget.getStartDate(), budget.getEndDate())
                        .stream()
                        .filter(t -> t.getCategory() != null && t.getCategory().getId().equals(budget.getCategory().getId()))
                        .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                        .collect(Collectors.toList());
                spent = categoryTransactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            } else {
                List<Transaction> periodTransactions = transactionRepository
                        .findByUserAndDateBetweenOrderByDateDesc(user, budget.getStartDate(), budget.getEndDate())
                        .stream()
                        .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                        .collect(Collectors.toList());
                spent = periodTransactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            budgetDtos.add(new BudgetDto(budget, spent));
        }

        model.addAttribute("user", user);
        model.addAttribute("budgets", budgetDtos);
        model.addAttribute("categories", categories);
        return "user/budgets";
    }

    @PostMapping("/budgets/add")
    public String addBudget(@RequestParam String name,
                          @RequestParam BigDecimal amount,
                          @RequestParam LocalDate startDate,
                          @RequestParam LocalDate endDate,
                          @RequestParam(required = false) Long categoryId,
                          RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser();
            Budget budget = new Budget();
            budget.setName(name);
            budget.setAmount(amount);
            budget.setStartDate(startDate);
            budget.setEndDate(endDate);
            budget.setUser(user);

            if (categoryId != null) {
                Optional<Category> category = categoryRepository.findById(categoryId);
                category.ifPresent(budget::setCategory);
            }

            budgetRepository.save(budget);
            redirectAttributes.addFlashAttribute("success", "Бюджет создан!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при создании бюджета: " + e.getMessage());
        }
        return "redirect:/user/budgets";
    }

    @PostMapping("/budgets/{id}/delete")
    public String deleteBudget(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Optional<Budget> budget = budgetRepository.findById(id);
        
        if (budget.isPresent() && budget.get().getUser().getId().equals(user.getId())) {
            budgetRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Бюджет удален!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Бюджет не найден!");
        }
        
        return "redirect:/user/budgets";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        User user = getCurrentUser();
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        BigDecimal totalIncome = transactionRepository.sumIncomeByUserAndDateAfter(user, startOfMonth);
        BigDecimal totalExpense = transactionRepository.sumExpenseByUserAndDateAfter(user, startOfMonth);
        
        if (totalIncome == null) totalIncome = BigDecimal.ZERO;
        if (totalExpense == null) totalExpense = BigDecimal.ZERO;

        List<Transaction> allTransactions = transactionRepository.findByUserAndDateBetweenOrderByDateDesc(user, startOfMonth, endOfMonth);
        
        // Статистика по категориям
        Map<Long, CategoryStat> categoryStatsMap = new HashMap<>();
        for (Transaction transaction : allTransactions) {
            if (transaction.getType() == Transaction.TransactionType.EXPENSE && transaction.getCategory() != null) {
                Long categoryId = transaction.getCategory().getId();
                CategoryStat stat = categoryStatsMap.getOrDefault(categoryId, 
                    new CategoryStat(transaction.getCategory().getName(), transaction.getCategory().getColor()));
                stat.amount = stat.amount.add(transaction.getAmount());
                categoryStatsMap.put(categoryId, stat);
            }
        }

        List<CategoryStat> categoryStats = new ArrayList<>(categoryStatsMap.values());
        BigDecimal totalExpenseForPercentage = totalExpense.compareTo(BigDecimal.ZERO) > 0 ? totalExpense : BigDecimal.ONE;
        
        for (CategoryStat stat : categoryStats) {
            stat.percentage = stat.amount.divide(totalExpenseForPercentage, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).intValue();
        }

        model.addAttribute("user", user);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("totalTransactions", allTransactions.size());
        model.addAttribute("categoryStats", categoryStats);
        return "user/reports";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        User user = getCurrentUser();
        model.addAttribute("user", user);
        return "user/settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String firstName,
                              @RequestParam(required = false) String lastName,
                              @RequestParam String email,
                              RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("success", "Профиль обновлен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении профиля: " + e.getMessage());
        }
        return "redirect:/user/settings";
    }

    @PostMapping("/settings/password")
    public String changePassword(@RequestParam String currentPassword,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser();
            
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "Текущий пароль неверен!");
                return "redirect:/user/settings";
            }
            
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Новые пароли не совпадают!");
                return "redirect:/user/settings";
            }
            
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("success", "Пароль изменен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при изменении пароля: " + e.getMessage());
        }
        return "redirect:/user/settings";
    }

    @GetMapping("/help")
    public String help(Model model) {
        User user = getCurrentUser();
        model.addAttribute("user", user);
        return "user/help";
    }

    @GetMapping("/ai-analysis")
    @ResponseBody
    public Map<String, String> aiAnalysis() {
        User user = getCurrentUser();
        String text = aiExpenseAnalysisService.analyzeLastMonth(user);
        Map<String, String> result = new HashMap<>();
        result.put("recommendations", text);
        return result;
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
        public void setName(String name) { this.name = name; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public int getPercentage() { return percentage; }
        public void setPercentage(int percentage) { this.percentage = percentage; }
    }
}

