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

import org.springframework.security.access.prepost.PreAuthorize;
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

import com.expensetracker.entity.AccountantRecommendation;
import com.expensetracker.entity.AccountantRequest;
import com.expensetracker.entity.AccountantUserRelationship;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.Income;
import com.expensetracker.entity.User;
import com.expensetracker.repository.AccountantRecommendationRepository;
import com.expensetracker.repository.AccountantRequestRepository;
import com.expensetracker.repository.AccountantUserRelationshipRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.IncomeRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.AiExpenseAnalysisService;
import com.expensetracker.service.StatisticsService;

@Controller
@RequestMapping("/user")
@PreAuthorize("hasRole('USER')")
public class UserController {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final PasswordEncoder passwordEncoder;
    private final AiExpenseAnalysisService aiExpenseAnalysisService;
    private final StatisticsService statisticsService;

    private final AccountantRequestRepository accountantRequestRepository;
    private final AccountantUserRelationshipRepository accountantUserRelationshipRepository;
    private final AccountantRecommendationRepository accountantRecommendationRepository;

    public UserController(UserRepository userRepository,
                          CategoryRepository categoryRepository,
                          IncomeRepository incomeRepository,
                          ExpenseRepository expenseRepository,
                          PasswordEncoder passwordEncoder,
                          AiExpenseAnalysisService aiExpenseAnalysisService,
                          StatisticsService statisticsService,
                          AccountantRequestRepository accountantRequestRepository,
                          AccountantUserRelationshipRepository accountantUserRelationshipRepository,
                          AccountantRecommendationRepository accountantRecommendationRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.passwordEncoder = passwordEncoder;
        this.aiExpenseAnalysisService = aiExpenseAnalysisService;
        this.statisticsService = statisticsService;
        this.accountantRequestRepository = accountantRequestRepository;
        this.accountantUserRelationshipRepository = accountantUserRelationshipRepository;
        this.accountantRecommendationRepository = accountantRecommendationRepository;
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
        LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        // Доходы за текущий месяц
        BigDecimal incomeTotal = statisticsService.calculateTotalIncome(user, startOfMonth);
        
        // Расходы за текущий месяц
        BigDecimal expenseTotal = statisticsService.calculateTotalExpense(user, startOfMonth);

        // Баланс за все время
        BigDecimal balance = statisticsService.calculateTotalBalance(user);

        // Последние расходы для отображения
        List<Expense> recentExpenses = expenseRepository.findByUserOrderByDateDesc(user)
                .stream()
                .limit(10)
                .collect(java.util.stream.Collectors.toList());

        // Статистика по категориям для диаграммы (топ-4 + остальные)
        List<StatisticsService.CategoryStatistics> categoryStatsList = 
                statisticsService.calculateCategoryStatisticsWithPercentages(user, startOfMonth, endOfMonth);
        
        // Сортируем по сумме (убывание) и берем топ-4
        List<StatisticsService.CategoryStatistics> sortedStats = categoryStatsList.stream()
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .collect(Collectors.toList());
        
        List<StatisticsService.CategoryStatistics> top4Stats = sortedStats.stream()
                .limit(4)
                .collect(Collectors.toList());
        
        // Считаем сумму остальных категорий
        BigDecimal otherAmount = sortedStats.stream()
                .skip(4)
                .map(StatisticsService.CategoryStatistics::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Считаем процент для остальных (от общего расхода за месяц)
        BigDecimal totalExpenseForPercentage = expenseTotal.compareTo(BigDecimal.ZERO) > 0 
                ? expenseTotal 
                : BigDecimal.ONE;
        int otherPercentage = otherAmount.compareTo(BigDecimal.ZERO) > 0
                ? otherAmount.divide(totalExpenseForPercentage, 2, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100))
                  .intValue()
                : 0;
        
        // Конвертируем топ-4 в CategoryStat
        List<CategoryStat> categoryStats = top4Stats.stream()
                .map(stat -> {
                    CategoryStat categoryStat = new CategoryStat(stat.getName(), stat.getColor());
                    categoryStat.setAmount(stat.getAmount());
                    categoryStat.setPercentage(stat.getPercentage());
                    return categoryStat;
                })
                .collect(Collectors.toList());
        
        // Добавляем категорию "Остальное" если есть категории вне топа
        if (otherAmount.compareTo(BigDecimal.ZERO) > 0) {
            CategoryStat otherStat = new CategoryStat("Остальное", "#95a5a6");
            otherStat.setAmount(otherAmount);
            otherStat.setPercentage(otherPercentage);
            categoryStats.add(otherStat);
        }

        model.addAttribute("user", user);
        model.addAttribute("incomeTotal", incomeTotal);
        model.addAttribute("expenseTotal", expenseTotal);
        model.addAttribute("balance", balance);
        model.addAttribute("recentExpenses", recentExpenses);
        model.addAttribute("categoryStats", categoryStats);

        return "user/dashboard";
    }

    @GetMapping("/expenses")
    public String expenses(Model model) {
        try {
            User user = getCurrentUser();
            List<Expense> expenses = expenseRepository.findByUserOrderByDateDesc(user);
            List<Category> categories = categoryRepository.findByUserOrderByNameAsc(user);

            model.addAttribute("user", user);
            model.addAttribute("expenses", expenses);
            model.addAttribute("categories", categories);
            return "user/expenses";
        } catch (Exception e) {
            model.addAttribute("error", "Ошибка при загрузке расходов: " + e.getMessage());
            return "user/expenses";
        }
    }

    @PostMapping("/expenses/add")
    public String addExpense(@RequestParam String name,
                          @RequestParam BigDecimal amount,
                          @RequestParam LocalDate date,
                          @RequestParam String type,
                          @RequestParam(required = false) String recurrencePeriod,
                          @RequestParam(required = false) Long categoryId,
                          RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser();
            
            // Валидация
            if (name == null || name.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Название расхода не может быть пустым!");
                return "redirect:/user/expenses";
            }
            
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("error", "Сумма расхода должна быть больше нуля!");
                return "redirect:/user/expenses";
            }
            
            if (date == null) {
                redirectAttributes.addFlashAttribute("error", "Дата должна быть указана!");
                return "redirect:/user/expenses";
            }
            
            Expense expense = new Expense();
            expense.setName(name.trim());
            expense.setAmount(amount);
            expense.setDate(date);
            expense.setUser(user);
            
            Expense.ExpenseType expenseType = Expense.ExpenseType.valueOf(type.toUpperCase());
            expense.setType(expenseType);
            
            if (expenseType == Expense.ExpenseType.RECURRING) {
                if (recurrencePeriod == null || recurrencePeriod.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Период повторения должен быть указан для периодического расхода!");
                    return "redirect:/user/expenses";
                }
                expense.setRecurrencePeriod(Expense.RecurrencePeriod.valueOf(recurrencePeriod.toUpperCase()));
            }

            if (categoryId != null && categoryId > 0) {
                Optional<Category> category = categoryRepository.findById(categoryId);
                if (category.isPresent() && category.get().getUser().getId().equals(user.getId())) {
                    expense.setCategory(category.get());
                }
            }

            expenseRepository.save(expense);
            redirectAttributes.addFlashAttribute("success", "Расход добавлен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при добавлении расхода: " + e.getMessage());
        }
        return "redirect:/user/expenses";
    }

    @PostMapping("/expenses/{id}/delete")
    public String deleteExpense(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Optional<Expense> expense = expenseRepository.findById(id);
        
        if (expense.isPresent() && expense.get().getUser().getId().equals(user.getId())) {
            expenseRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Расход удален!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Расход не найден!");
        }
        
        return "redirect:/user/expenses";
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

    @GetMapping("/incomes")
    public String incomes(Model model) {
        try {
            User user = getCurrentUser();
            List<Income> incomes = incomeRepository.findByUserOrderByDateDesc(user);

            model.addAttribute("user", user);
            model.addAttribute("incomes", incomes);
            return "user/incomes";
        } catch (Exception e) {
            model.addAttribute("error", "Ошибка при загрузке доходов: " + e.getMessage());
            return "user/incomes";
        }
    }

    @PostMapping("/incomes/add")
    public String addIncome(@RequestParam String name,
                          @RequestParam BigDecimal amount,
                          @RequestParam LocalDate date,
                          @RequestParam String type,
                          @RequestParam(required = false) String recurrencePeriod,
                          RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser();
            
            // Валидация
            if (name == null || name.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Название дохода не может быть пустым!");
                return "redirect:/user/incomes";
            }
            
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("error", "Сумма дохода должна быть больше нуля!");
                return "redirect:/user/incomes";
            }
            
            if (date == null) {
                redirectAttributes.addFlashAttribute("error", "Дата должна быть указана!");
                return "redirect:/user/incomes";
            }
            
            Income income = new Income();
            income.setName(name.trim());
            income.setAmount(amount);
            income.setDate(date);
            income.setUser(user);
            
            Income.IncomeType incomeType = Income.IncomeType.valueOf(type.toUpperCase());
            income.setType(incomeType);
            
            if (incomeType == Income.IncomeType.RECURRING) {
                if (recurrencePeriod == null || recurrencePeriod.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Период повторения должен быть указан для периодического дохода!");
                    return "redirect:/user/incomes";
                }
                income.setRecurrencePeriod(Income.RecurrencePeriod.valueOf(recurrencePeriod.toUpperCase()));
            }

            incomeRepository.save(income);
            redirectAttributes.addFlashAttribute("success", "Доход добавлен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при добавлении дохода: " + e.getMessage());
        }
        return "redirect:/user/incomes";
    }

    @PostMapping("/incomes/{id}/delete")
    public String deleteIncome(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Optional<Income> income = incomeRepository.findById(id);
        
        if (income.isPresent() && income.get().getUser().getId().equals(user.getId())) {
            incomeRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Доход удален!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Доход не найден!");
        }
        
        return "redirect:/user/incomes";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        User user = getCurrentUser();
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        // Доходы за текущий месяц
        BigDecimal totalIncome = statisticsService.calculateTotalIncome(user, startOfMonth);

        // Расходы за текущий месяц
        BigDecimal totalExpense = statisticsService.calculateTotalExpense(user, startOfMonth);

        // Баланс за все время
        BigDecimal balance = statisticsService.calculateTotalBalance(user);

        // Используем StatisticsService для расчета статистики по категориям
        List<StatisticsService.CategoryStatistics> categoryStatsList = 
                statisticsService.calculateCategoryStatisticsWithPercentages(user, startOfMonth, endOfMonth);

        // Конвертируем в CategoryStat для совместимости с представлением
        List<CategoryStat> categoryStats = categoryStatsList.stream()
                .map(stat -> {
                    CategoryStat categoryStat = new CategoryStat(stat.getName(), stat.getColor());
                    categoryStat.setAmount(stat.getAmount());
                    categoryStat.setPercentage(stat.getPercentage());
                    return categoryStat;
                })
                .collect(Collectors.toList());

        // Получаем общее количество элементов (доходы + расходы) за период
        List<Income> incomesInPeriod = incomeRepository.findByUserAndDateBetween(user, startOfMonth, endOfMonth);
        List<Expense> expensesInPeriod = expenseRepository.findByUserAndDateBetween(user, startOfMonth, endOfMonth);
        long totalItems = incomesInPeriod.size() + expensesInPeriod.size();

        model.addAttribute("user", user);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("balance", balance);
        model.addAttribute("totalTransactions", totalItems);
        model.addAttribute("categoryStats", categoryStats);
        model.addAttribute("startDate", startOfMonth);
        model.addAttribute("endDate", endOfMonth);
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

    @GetMapping("/search-accountants")
    public String searchAccountants(Model model, @RequestParam(required = false) String search) {
        User currentUser = getCurrentUser();
        
        List<User> foundAccountants = new ArrayList<>();
        Optional<AccountantUserRelationship> currentRelationshipOpt = 
                accountantUserRelationshipRepository.findByUser(currentUser);
        Long currentAccountantId = currentRelationshipOpt.map(rel -> rel.getAccountant().getId()).orElse(null);
        
        List<AccountantRequest> pendingRequests = accountantRequestRepository
                .findByUserAndStatus(currentUser, AccountantRequest.RequestStatus.PENDING);
        List<Long> accountantsWithPendingRequests = pendingRequests.stream()
                .map(req -> req.getAccountant().getId())
                .collect(Collectors.toList());

        if (search != null && !search.trim().isEmpty()) {
            String searchTerm = "%" + search.trim().toLowerCase() + "%";
            foundAccountants = userRepository.findAll().stream()
                    .filter(user -> user.getRoles().stream()
                            .anyMatch(role -> role.getName().equals("ROLE_ACCOUNTANT")))
                    .filter(user -> !user.getId().equals(currentUser.getId()))
                    .filter(user -> {
                        String firstName = user.getFirstName() != null ? user.getFirstName().toLowerCase() : "";
                        String lastName = user.getLastName() != null ? user.getLastName().toLowerCase() : "";
                        String email = user.getEmail() != null ? user.getEmail().toLowerCase() : "";
                        String username = user.getUsername() != null ? user.getUsername().toLowerCase() : "";
                        String searchLower = searchTerm.substring(1, searchTerm.length() - 1);
                        return firstName.contains(searchLower) ||
                               lastName.contains(searchLower) ||
                               email.contains(searchLower) ||
                               username.contains(searchLower);
                    })
                    .collect(Collectors.toList());
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("foundAccountants", foundAccountants);
        model.addAttribute("search", search);
        model.addAttribute("currentAccountantId", currentAccountantId);
        model.addAttribute("accountantsWithPendingRequests", accountantsWithPendingRequests);

        return "user/search-accountants";
    }

    @PostMapping("/requests/send")
    public String sendRequest(@RequestParam Long accountantId, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<User> targetAccountantOpt = userRepository.findById(accountantId);
            
            if (targetAccountantOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Бухгалтер не найден!");
                return "redirect:/user/search-accountants";
            }
            
            User targetAccountant = targetAccountantOpt.get();
            
            // Проверка: бухгалтер не должен быть самим пользователем
            if (targetAccountant.getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Вы не можете отправить запрос самому себе!");
                return "redirect:/user/search-accountants";
            }
            
            // Проверка: у пользователя не должно быть уже бухгалтера
            if (accountantUserRelationshipRepository.existsByUser(currentUser)) {
                redirectAttributes.addFlashAttribute("error", "У вас уже есть бухгалтер!");
                return "redirect:/user/search-accountants";
            }
            
            // Проверка: не должно быть активного запроса
            if (accountantRequestRepository.findPendingRequestByAccountantAndUser(targetAccountant, currentUser).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Запрос уже отправлен!");
                return "redirect:/user/search-accountants";
            }
            
            AccountantRequest request = new AccountantRequest();
            request.setAccountant(targetAccountant);
            request.setUser(currentUser);
            request.setStatus(AccountantRequest.RequestStatus.PENDING);
            request.setInitiator(AccountantRequest.InitiatorType.USER);
            accountantRequestRepository.save(request);
            
            redirectAttributes.addFlashAttribute("success", "Запрос успешно отправлен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при отправке запроса: " + e.getMessage());
        }
        return "redirect:/user/search-accountants";
    }

    @GetMapping("/requests")
    public String requests(Model model) {
        User currentUser = getCurrentUser();
        
        // Входящие запросы (от бухгалтеров)
        List<AccountantRequest> incomingRequests = accountantRequestRepository.findByUser(currentUser)
                .stream()
                .filter(req -> req.getInitiator() == AccountantRequest.InitiatorType.ACCOUNTANT)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        
        // Исходящие запросы (от пользователя)
        List<AccountantRequest> outgoingRequests = accountantRequestRepository.findByUser(currentUser)
                .stream()
                .filter(req -> req.getInitiator() == AccountantRequest.InitiatorType.USER)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        
        model.addAttribute("user", currentUser);
        model.addAttribute("incomingRequests", incomingRequests);
        model.addAttribute("outgoingRequests", outgoingRequests);
        
        return "user/requests";
    }

    @PostMapping("/requests/{id}/approve")
    public String approveRequest(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<AccountantRequest> requestOpt = accountantRequestRepository.findById(id);
            
            if (requestOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Запрос не найден!");
                return "redirect:/user/requests";
            }
            
            AccountantRequest request = requestOpt.get();
            
            // Проверка: запрос должен быть адресован текущему пользователю и от бухгалтера
            if (!request.getUser().getId().equals(currentUser.getId()) || 
                request.getInitiator() != AccountantRequest.InitiatorType.ACCOUNTANT) {
                redirectAttributes.addFlashAttribute("error", "Нет доступа к этому запросу!");
                return "redirect:/user/requests";
            }
            
            if (request.getStatus() != AccountantRequest.RequestStatus.PENDING) {
                redirectAttributes.addFlashAttribute("error", "Запрос уже обработан!");
                return "redirect:/user/requests";
            }
            
            User targetAccountant = request.getAccountant();
            
            // Если у пользователя уже есть другой бухгалтер, удаляем старую связь
            accountantUserRelationshipRepository.findByUser(currentUser).ifPresent(accountantUserRelationshipRepository::delete);
            
            // Отклоняем другие активные запросы от этого пользователя
            List<AccountantRequest> otherRequests = accountantRequestRepository
                    .findByUserAndStatus(currentUser, AccountantRequest.RequestStatus.PENDING);
            for (AccountantRequest otherReq : otherRequests) {
                if (!otherReq.getId().equals(request.getId())) {
                    otherReq.setStatus(AccountantRequest.RequestStatus.REJECTED);
                    accountantRequestRepository.save(otherReq);
                }
            }
            
            // Создаем связь
            AccountantUserRelationship relationship = new AccountantUserRelationship();
            relationship.setAccountant(targetAccountant);
            relationship.setUser(currentUser);
            accountantUserRelationshipRepository.save(relationship);
            
            // Обновляем статус запроса
            request.setStatus(AccountantRequest.RequestStatus.APPROVED);
            accountantRequestRepository.save(request);
            
            redirectAttributes.addFlashAttribute("success", "Запрос подтвержден!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при подтверждении запроса: " + e.getMessage());
        }
        return "redirect:/user/requests";
    }

    @PostMapping("/requests/{id}/reject")
    public String rejectRequest(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<AccountantRequest> requestOpt = accountantRequestRepository.findById(id);
            
            if (requestOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Запрос не найден!");
                return "redirect:/user/requests";
            }
            
            AccountantRequest request = requestOpt.get();
            
            // Проверка: запрос должен быть адресован текущему пользователю
            if (!request.getUser().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Нет доступа к этому запросу!");
                return "redirect:/user/requests";
            }
            
            if (request.getStatus() != AccountantRequest.RequestStatus.PENDING) {
                redirectAttributes.addFlashAttribute("error", "Запрос уже обработан!");
                return "redirect:/user/requests";
            }
            
            request.setStatus(AccountantRequest.RequestStatus.REJECTED);
            accountantRequestRepository.save(request);
            
            redirectAttributes.addFlashAttribute("success", "Запрос отклонен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при отклонении запроса: " + e.getMessage());
        }
        return "redirect:/user/requests";
    }

    @PostMapping("/relationships/remove")
    public String removeRelationship(RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<AccountantUserRelationship> relationshipOpt = 
                    accountantUserRelationshipRepository.findByUser(currentUser);
            
            if (relationshipOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Связь не найдена!");
                return "redirect:/user/dashboard";
            }
            
            User accountant = relationshipOpt.get().getAccountant();
            accountantUserRelationshipRepository.delete(relationshipOpt.get());
            
            // Удаляем все связанные запросы
            List<AccountantRequest> relatedRequests = accountantRequestRepository.findByUser(currentUser)
                    .stream()
                    .filter(req -> req.getAccountant().getId().equals(accountant.getId()))
                    .collect(Collectors.toList());
            accountantRequestRepository.deleteAll(relatedRequests);
            
            redirectAttributes.addFlashAttribute("success", "Связь разорвана!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при разрыве связи: " + e.getMessage());
        }
        return "redirect:/user/dashboard";
    }

    @GetMapping("/my-accountant")
    public String myAccountant(Model model) {
        User currentUser = getCurrentUser();
        
        // Находим активную связь с бухгалтером
        Optional<AccountantUserRelationship> relationshipOpt = 
                accountantUserRelationshipRepository.findByUser(currentUser);
        
        if (relationshipOpt.isEmpty()) {
            model.addAttribute("user", currentUser);
            model.addAttribute("hasAccountant", false);
            return "user/my-accountant";
        }
        
        AccountantUserRelationship relationship = relationshipOpt.get();
        User accountant = relationship.getAccountant();
        
        // Получаем все рекомендации от бухгалтера
        List<AccountantRecommendation> recommendations = 
                accountantRecommendationRepository.findByAccountantAndUser(accountant, currentUser);
        
        model.addAttribute("user", currentUser);
        model.addAttribute("accountant", accountant);
        model.addAttribute("recommendations", recommendations);
        model.addAttribute("hasAccountant", true);
        
        return "user/my-accountant";
    }
}

