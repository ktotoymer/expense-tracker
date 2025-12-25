package com.expensetracker.controller;

import com.expensetracker.dto.FinancialItem;
import com.expensetracker.entity.AccountantRequest;
import com.expensetracker.entity.AccountantUserRelationship;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.Income;
import com.expensetracker.entity.User;
import com.expensetracker.entity.AccountantRecommendation;
import com.expensetracker.repository.AccountantRecommendationRepository;
import com.expensetracker.repository.AccountantRequestRepository;
import com.expensetracker.repository.AccountantUserRelationshipRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.IncomeRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.StatisticsService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Контроллер для бухгалтера (шаблон Controller).
 * 
 * Согласно шаблону Controller, этот класс отвечает за обработку системных событий
 * (HTTP-запросов) для роли ACCOUNTANT. Контроллер делегирует бизнес-логику сервисам,
 * что обеспечивает низкую связанность (Low Coupling) и высокую связность (High Cohesion).
 * 
 * Рефакторинг применен для устранения антишаблона "раздутый контроллер":
 * - Бизнес-логика вынесена в StatisticsService (Expert pattern)
 * - Контроллер теперь отвечает только за обработку HTTP-запросов и координацию сервисов
 */
@Controller
@RequestMapping("/accountant")
@PreAuthorize("hasRole('ACCOUNTANT')")
public class AccountantController {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final StatisticsService statisticsService;
    private final AccountantRequestRepository accountantRequestRepository;
    private final AccountantUserRelationshipRepository accountantUserRelationshipRepository;
    private final AccountantRecommendationRepository accountantRecommendationRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountantController(UserRepository userRepository,
                               CategoryRepository categoryRepository,
                               IncomeRepository incomeRepository,
                               ExpenseRepository expenseRepository,
                               StatisticsService statisticsService,
                               AccountantRequestRepository accountantRequestRepository,
                               AccountantUserRelationshipRepository accountantUserRelationshipRepository,
                               AccountantRecommendationRepository accountantRecommendationRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.statisticsService = statisticsService;
        this.accountantRequestRepository = accountantRequestRepository;
        this.accountantUserRelationshipRepository = accountantUserRelationshipRepository;
        this.accountantRecommendationRepository = accountantRecommendationRepository;
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
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        // Получаем только связанных пользователей (из активных связей)
        List<AccountantUserRelationship> relationships = accountantUserRelationshipRepository.findByAccountant(currentUser);
        List<User> connectedUsers = relationships.stream()
                .map(AccountantUserRelationship::getUser)
                .collect(Collectors.toList());

        // Рассчитываем общую статистику с учетом incomes и expenses
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;
        long totalTransactions = 0;

        // Статистика по пользователям
        Map<String, UserStats> userStatsMap = new HashMap<>();
        for (User user : connectedUsers) {
            // Доходы за текущий месяц
            BigDecimal userIncome = statisticsService.calculateTotalIncome(user, startOfMonth);

            // Расходы за текущий месяц
            BigDecimal userExpense = statisticsService.calculateTotalExpense(user, startOfMonth);

            // Баланс за все время
            BigDecimal userBalance = statisticsService.calculateTotalBalance(user);

            // Количество элементов (доходы + расходы)
            List<Income> allIncomes = incomeRepository.findByUserOrderByDateDesc(user);
            List<Expense> allExpenses = expenseRepository.findByUserOrderByDateDesc(user);
            long userItemsCount = allIncomes.size() + allExpenses.size();

            // Добавляем к общей статистике
            totalIncome = totalIncome.add(userIncome);
            totalExpense = totalExpense.add(userExpense);
            totalBalance = totalBalance.add(userBalance);
            totalTransactions += userItemsCount;

            userStatsMap.put(user.getUsername(), new UserStats(
                user.getFirstName() + " " + user.getLastName(),
                userIncome,
                userExpense,
                userItemsCount
            ));
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("totalUsers", connectedUsers.size());
        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("balance", totalBalance);
        model.addAttribute("userStats", userStatsMap);
        model.addAttribute("hasConnectedUsers", !connectedUsers.isEmpty());

        return "accountant/dashboard";
    }

    @GetMapping("/transactions")
    public String transactions(Model model,
                              @RequestParam(required = false) Long userId,
                              @RequestParam(required = false) String type,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate) {
        User currentUser = getCurrentUser();
        // Получаем только связанных пользователей
        List<AccountantUserRelationship> relationships = accountantUserRelationshipRepository.findByAccountant(currentUser);
        List<User> allUsers = relationships.stream()
                .map(AccountantUserRelationship::getUser)
                .collect(Collectors.toList());
        List<Long> connectedUserIds = allUsers.stream().map(User::getId).collect(Collectors.toList());
        
        List<FinancialItem> items = new ArrayList<>();

        // Получаем доходы и расходы только от связанных пользователей
        if (userId != null) {
            // Если выбран конкретный пользователь, проверяем, что он в списке связанных
            if (connectedUserIds.contains(userId)) {
                User targetUser = userRepository.findById(userId).orElse(null);
                if (targetUser != null) {
                    // Получаем доходы
                    List<Income> incomes = incomeRepository.findByUserOrderByDateDesc(targetUser);
                    for (Income income : incomes) {
                        items.add(new FinancialItem(income));
                    }
                    // Получаем расходы
                    List<Expense> expenses = expenseRepository.findByUserOrderByDateDesc(targetUser);
                    for (Expense expense : expenses) {
                        items.add(new FinancialItem(expense));
                    }
                }
            }
        } else {
            // Если не выбран конкретный пользователь, получаем доходы и расходы всех связанных пользователей
            for (User user : allUsers) {
                List<Income> incomes = incomeRepository.findByUserOrderByDateDesc(user);
                for (Income income : incomes) {
                    items.add(new FinancialItem(income));
                }
                List<Expense> expenses = expenseRepository.findByUserOrderByDateDesc(user);
                for (Expense expense : expenses) {
                    items.add(new FinancialItem(expense));
                }
            }
        }
        
        // Сортируем по дате (от новых к старым)
        items.sort((i1, i2) -> i2.getDate().compareTo(i1.getDate()));

        // Фильтрация по типу
        if (type != null && !type.isEmpty()) {
            items = items.stream()
                    .filter(i -> i.getType().equals(type))
                    .collect(Collectors.toList());
        }

        // Фильтрация по датам
        if (startDate != null && !startDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate);
            items = items.stream()
                    .filter(i -> !i.getDate().isBefore(start))
                    .collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isEmpty()) {
            LocalDate end = LocalDate.parse(endDate);
            items = items.stream()
                    .filter(i -> !i.getDate().isAfter(end))
                    .collect(Collectors.toList());
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("transactions", items); // Используем то же имя для совместимости с шаблоном
        model.addAttribute("allUsers", allUsers);
        model.addAttribute("selectedUserId", userId);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedStartDate", startDate);
        model.addAttribute("selectedEndDate", endDate);

        return "accountant/transactions";
    }


    @GetMapping("/reports")
    public String reports(Model model,
                         @RequestParam(required = false) String period,
                         @RequestParam(required = false) Long userId) {
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

        // Получаем только связанных пользователей
        List<AccountantUserRelationship> relationships = accountantUserRelationshipRepository.findByAccountant(currentUser);
        List<User> allUsers = relationships.stream()
                .map(AccountantUserRelationship::getUser)
                .collect(Collectors.toList());
        List<Long> connectedUserIds = allUsers.stream().map(User::getId).collect(Collectors.toList());

        // Определяем список пользователей для расчета
        List<User> usersToProcess = new ArrayList<>();
        if (userId != null && connectedUserIds.contains(userId)) {
            User targetUser = userRepository.findById(userId).orElse(null);
            if (targetUser != null) {
                usersToProcess.add(targetUser);
            }
        } else {
            usersToProcess = allUsers;
        }

        // Рассчитываем статистику с учетом incomes и expenses
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;
        long totalTransactions = 0;

        for (User user : usersToProcess) {
            // Доходы за период
            BigDecimal userIncome = statisticsService.calculateTotalIncome(user, startDate);

            // Расходы за период
            BigDecimal userExpense = statisticsService.calculateTotalExpense(user, startDate);

            // Баланс за все время
            BigDecimal userBalance = statisticsService.calculateTotalBalance(user);

            totalIncome = totalIncome.add(userIncome);
            totalExpense = totalExpense.add(userExpense);
            totalBalance = totalBalance.add(userBalance);

            // Количество элементов за период
            List<Income> incomesInPeriod = incomeRepository.findByUserAndDateBetween(user, startDate, endDate);
            List<Expense> expensesInPeriod = expenseRepository.findByUserAndDateBetween(user, startDate, endDate);
            totalTransactions += incomesInPeriod.size() + expensesInPeriod.size();
        }

        // Используем StatisticsService для расчета статистики по категориям (Expert pattern)
        // Для общей статистики по категориям собираем данные от выбранных пользователей
        Map<String, StatisticsService.CategoryStatistics> allCategoryStatsMap = new HashMap<>();
        for (User user : usersToProcess) {
            Map<String, StatisticsService.CategoryStatistics> userCategoryStats = 
                    statisticsService.calculateCategoryStatistics(user, startDate, endDate);
            
            for (Map.Entry<String, StatisticsService.CategoryStatistics> entry : userCategoryStats.entrySet()) {
                String categoryName = entry.getKey();
                StatisticsService.CategoryStatistics userStat = entry.getValue();
                
                StatisticsService.CategoryStatistics allStat = allCategoryStatsMap.getOrDefault(categoryName,
                        new StatisticsService.CategoryStatistics(userStat.getName(), userStat.getColor()));
                allStat.addAmount(userStat.getAmount());
                allCategoryStatsMap.put(categoryName, allStat);
            }
        }

        // Конвертируем в CategoryStat для совместимости с представлением
        final BigDecimal finalTotalExpense = totalExpense;
        List<CategoryStat> categoryStats = allCategoryStatsMap.values().stream()
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .map(stat -> {
                    CategoryStat categoryStat = new CategoryStat(stat.getName(), stat.getColor());
                    categoryStat.amount = stat.getAmount();
                    BigDecimal totalExpenseForPercentage = finalTotalExpense.compareTo(BigDecimal.ZERO) > 0 
                            ? finalTotalExpense 
                            : BigDecimal.ONE;
                    categoryStat.percentage = stat.getAmount()
                            .divide(totalExpenseForPercentage, 2, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .intValue();
                    return categoryStat;
                })
                .collect(Collectors.toList());

        model.addAttribute("user", currentUser);
        model.addAttribute("allUsers", allUsers);
        model.addAttribute("selectedUserId", userId);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("balance", totalBalance);
        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("categoryStats", categoryStats);
        model.addAttribute("period", period != null ? period : "month");
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "accountant/reports";
    }

    @GetMapping("/users")
    public String users(Model model) {
        User currentUser = getCurrentUser();
        // Получаем только связанных пользователей
        List<AccountantUserRelationship> relationships = accountantUserRelationshipRepository.findByAccountant(currentUser);
        List<User> allUsers = relationships.stream()
                .map(AccountantUserRelationship::getUser)
                .collect(Collectors.toList());

        // Рассчитываем статистику с учетом incomes и expenses
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        Map<Long, UserFinancialStats> userFinancialStats = new HashMap<>();

        for (User targetUser : allUsers) {
            // Доходы за текущий месяц
            BigDecimal userIncome = statisticsService.calculateTotalIncome(targetUser, startOfMonth);

            // Расходы за текущий месяц
            BigDecimal userExpense = statisticsService.calculateTotalExpense(targetUser, startOfMonth);

            // Баланс за все время
            BigDecimal userBalance = statisticsService.calculateTotalBalance(targetUser);

            // Количество элементов (доходы + расходы)
            List<Income> allIncomes = incomeRepository.findByUserOrderByDateDesc(targetUser);
            List<Expense> allExpenses = expenseRepository.findByUserOrderByDateDesc(targetUser);
            long userItemsCount = allIncomes.size() + allExpenses.size();

            userFinancialStats.put(targetUser.getId(), new UserFinancialStats(
                userIncome,
                userExpense,
                userItemsCount,
                userBalance
            ));
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("users", allUsers);
        model.addAttribute("userFinancialStats", userFinancialStats);

        return "accountant/users";
    }

    // Вспомогательные классы для статистики
    public static class UserStats {
        private String name;
        private BigDecimal income;
        private BigDecimal expense;
        private long transactionsCount;

        public UserStats(String name, BigDecimal income, BigDecimal expense, long transactionsCount) {
            this.name = name;
            this.income = income;
            this.expense = expense;
            this.transactionsCount = transactionsCount;
        }

        public String getName() { return name; }
        public BigDecimal getIncome() { return income; }
        public BigDecimal getExpense() { return expense; }
        public long getTransactionsCount() { return transactionsCount; }
    }

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

    public static class UserFinancialStats {
        private BigDecimal income;
        private BigDecimal expense;
        private long transactionsCount;
        private BigDecimal balance;

        public UserFinancialStats(BigDecimal income, BigDecimal expense, long transactionsCount, BigDecimal balance) {
            this.income = income;
            this.expense = expense;
            this.transactionsCount = transactionsCount;
            this.balance = balance;
        }

        public BigDecimal getIncome() { return income; }
        public BigDecimal getExpense() { return expense; }
        public long getTransactionsCount() { return transactionsCount; }
        public BigDecimal getBalance() { return balance; }
    }

    @GetMapping("/search-users")
    public String searchUsers(Model model, 
                              @RequestParam(required = false) String firstName,
                              @RequestParam(required = false) String lastName,
                              @RequestParam(required = false) String username) {
        User currentUser = getCurrentUser();
        
        List<User> connectedUserIds = accountantUserRelationshipRepository.findByAccountant(currentUser)
                .stream()
                .map(AccountantUserRelationship::getUser)
                .collect(Collectors.toList());
        List<Long> connectedUserIdsList = connectedUserIds.stream().map(User::getId).collect(Collectors.toList());
        
        List<AccountantRequest> pendingRequests = accountantRequestRepository
                .findByAccountantAndStatus(currentUser, AccountantRequest.RequestStatus.PENDING);
        List<Long> usersWithPendingRequests = pendingRequests.stream()
                .map(req -> req.getUser().getId())
                .collect(Collectors.toList());

        // Получаем всех пользователей с ролью USER, исключая текущего бухгалтера
        List<User> allUsers = userRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("ROLE_USER")))
                .filter(user -> !user.getId().equals(currentUser.getId()))
                .collect(Collectors.toList());

        // Применяем фильтры, если они указаны
        List<User> filteredUsers = allUsers;
        if ((firstName != null && !firstName.trim().isEmpty()) ||
            (lastName != null && !lastName.trim().isEmpty()) ||
            (username != null && !username.trim().isEmpty())) {
            
            filteredUsers = allUsers.stream()
                    .filter(user -> {
                        boolean matches = true;
                        
                        if (firstName != null && !firstName.trim().isEmpty()) {
                            String userFirstName = user.getFirstName() != null ? user.getFirstName().toLowerCase() : "";
                            matches = matches && userFirstName.contains(firstName.trim().toLowerCase());
                        }
                        
                        if (lastName != null && !lastName.trim().isEmpty()) {
                            String userLastName = user.getLastName() != null ? user.getLastName().toLowerCase() : "";
                            matches = matches && userLastName.contains(lastName.trim().toLowerCase());
                        }
                        
                        if (username != null && !username.trim().isEmpty()) {
                            String userUsername = user.getUsername() != null ? user.getUsername().toLowerCase() : "";
                            matches = matches && userUsername.contains(username.trim().toLowerCase());
                        }
                        
                        return matches;
                    })
                    .collect(Collectors.toList());
        }

        // Сортируем по алфавиту (сначала по фамилии, потом по имени)
        filteredUsers = filteredUsers.stream()
                .sorted((u1, u2) -> {
                    String lastName1 = u1.getLastName() != null ? u1.getLastName() : "";
                    String lastName2 = u2.getLastName() != null ? u2.getLastName() : "";
                    int lastNameCompare = lastName1.compareToIgnoreCase(lastName2);
                    if (lastNameCompare != 0) {
                        return lastNameCompare;
                    }
                    String firstName1 = u1.getFirstName() != null ? u1.getFirstName() : "";
                    String firstName2 = u2.getFirstName() != null ? u2.getFirstName() : "";
                    return firstName1.compareToIgnoreCase(firstName2);
                })
                .collect(Collectors.toList());

        model.addAttribute("user", currentUser);
        model.addAttribute("foundUsers", filteredUsers);
        model.addAttribute("firstName", firstName);
        model.addAttribute("lastName", lastName);
        model.addAttribute("username", username);
        model.addAttribute("connectedUserIds", connectedUserIdsList);
        model.addAttribute("usersWithPendingRequests", usersWithPendingRequests);

        return "accountant/search-users";
    }

    @PostMapping("/requests/send")
    public String sendRequest(@RequestParam Long userId, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<User> targetUserOpt = userRepository.findById(userId);
            
            if (targetUserOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден!");
                return "redirect:/accountant/search-users";
            }
            
            User targetUser = targetUserOpt.get();
            
            // Проверка: пользователь не должен быть самим бухгалтером
            if (targetUser.getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Вы не можете отправить запрос самому себе!");
                return "redirect:/accountant/search-users";
            }
            
            // Проверка: у пользователя не должно быть уже бухгалтера
            if (accountantUserRelationshipRepository.existsByUser(targetUser)) {
                redirectAttributes.addFlashAttribute("error", "У этого пользователя уже есть бухгалтер!");
                return "redirect:/accountant/search-users";
            }
            
            // Проверка: не должно быть активного запроса
            if (accountantRequestRepository.findPendingRequestByAccountantAndUser(currentUser, targetUser).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Запрос уже отправлен!");
                return "redirect:/accountant/search-users";
            }
            
            AccountantRequest request = new AccountantRequest();
            request.setAccountant(currentUser);
            request.setUser(targetUser);
            request.setStatus(AccountantRequest.RequestStatus.PENDING);
            request.setInitiator(AccountantRequest.InitiatorType.ACCOUNTANT);
            accountantRequestRepository.save(request);
            
            redirectAttributes.addFlashAttribute("success", "Запрос успешно отправлен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при отправке запроса: " + e.getMessage());
        }
        return "redirect:/accountant/search-users";
    }

    @GetMapping("/requests")
    public String requests(Model model) {
        User currentUser = getCurrentUser();
        
        // Входящие запросы (от пользователей)
        List<AccountantRequest> incomingRequests = accountantRequestRepository.findByAccountant(currentUser)
                .stream()
                .filter(req -> req.getInitiator() == AccountantRequest.InitiatorType.USER)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        
        // Исходящие запросы (от бухгалтера)
        List<AccountantRequest> outgoingRequests = accountantRequestRepository.findByAccountant(currentUser)
                .stream()
                .filter(req -> req.getInitiator() == AccountantRequest.InitiatorType.ACCOUNTANT)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        
        model.addAttribute("user", currentUser);
        model.addAttribute("incomingRequests", incomingRequests);
        model.addAttribute("outgoingRequests", outgoingRequests);
        
        return "accountant/requests";
    }

    @PostMapping("/requests/{id}/approve")
    public String approveRequest(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<AccountantRequest> requestOpt = accountantRequestRepository.findById(id);
            
            if (requestOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Запрос не найден!");
                return "redirect:/accountant/requests";
            }
            
            AccountantRequest request = requestOpt.get();
            
            // Проверка: запрос должен быть адресован текущему бухгалтеру и от пользователя
            if (!request.getAccountant().getId().equals(currentUser.getId()) || 
                request.getInitiator() != AccountantRequest.InitiatorType.USER) {
                redirectAttributes.addFlashAttribute("error", "Нет доступа к этому запросу!");
                return "redirect:/accountant/requests";
            }
            
            if (request.getStatus() != AccountantRequest.RequestStatus.PENDING) {
                redirectAttributes.addFlashAttribute("error", "Запрос уже обработан!");
                return "redirect:/accountant/requests";
            }
            
            User targetUser = request.getUser();
            
            // Если у пользователя уже есть другой бухгалтер, удаляем старую связь
            accountantUserRelationshipRepository.findByUser(targetUser).ifPresent(accountantUserRelationshipRepository::delete);
            
            // Отклоняем другие активные запросы от этого пользователя
            List<AccountantRequest> otherRequests = accountantRequestRepository
                    .findByUserAndStatus(targetUser, AccountantRequest.RequestStatus.PENDING);
            for (AccountantRequest otherReq : otherRequests) {
                if (!otherReq.getId().equals(request.getId())) {
                    otherReq.setStatus(AccountantRequest.RequestStatus.REJECTED);
                    accountantRequestRepository.save(otherReq);
                }
            }
            
            // Создаем связь
            AccountantUserRelationship relationship = new AccountantUserRelationship();
            relationship.setAccountant(currentUser);
            relationship.setUser(targetUser);
            accountantUserRelationshipRepository.save(relationship);
            
            // Обновляем статус запроса
            request.setStatus(AccountantRequest.RequestStatus.APPROVED);
            accountantRequestRepository.save(request);
            
            redirectAttributes.addFlashAttribute("success", "Запрос подтвержден!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при подтверждении запроса: " + e.getMessage());
        }
        return "redirect:/accountant/requests";
    }

    @PostMapping("/requests/{id}/reject")
    public String rejectRequest(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<AccountantRequest> requestOpt = accountantRequestRepository.findById(id);
            
            if (requestOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Запрос не найден!");
                return "redirect:/accountant/requests";
            }
            
            AccountantRequest request = requestOpt.get();
            
            // Проверка: запрос должен быть адресован текущему бухгалтеру
            if (!request.getAccountant().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Нет доступа к этому запросу!");
                return "redirect:/accountant/requests";
            }
            
            if (request.getStatus() != AccountantRequest.RequestStatus.PENDING) {
                redirectAttributes.addFlashAttribute("error", "Запрос уже обработан!");
                return "redirect:/accountant/requests";
            }
            
            request.setStatus(AccountantRequest.RequestStatus.REJECTED);
            accountantRequestRepository.save(request);
            
            redirectAttributes.addFlashAttribute("success", "Запрос отклонен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при отклонении запроса: " + e.getMessage());
        }
        return "redirect:/accountant/requests";
    }

    @PostMapping("/relationships/{userId}/remove")
    public String removeRelationship(@PathVariable Long userId, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<User> targetUserOpt = userRepository.findById(userId);
            
            if (targetUserOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден!");
                return "redirect:/accountant/dashboard";
            }
            
            User targetUser = targetUserOpt.get();
            Optional<AccountantUserRelationship> relationshipOpt = 
                    accountantUserRelationshipRepository.findByAccountantAndUser(currentUser, targetUser);
            
            if (relationshipOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Связь не найдена!");
                return "redirect:/accountant/dashboard";
            }
            
            accountantUserRelationshipRepository.delete(relationshipOpt.get());
            
            // Удаляем все связанные запросы
            List<AccountantRequest> relatedRequests = accountantRequestRepository.findByAccountant(currentUser)
                    .stream()
                    .filter(req -> req.getUser().getId().equals(targetUser.getId()))
                    .collect(Collectors.toList());
            accountantRequestRepository.deleteAll(relatedRequests);
            
            redirectAttributes.addFlashAttribute("success", "Связь разорвана!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при разрыве связи: " + e.getMessage());
        }
        return "redirect:/accountant/dashboard";
    }

    @GetMapping("/recommendations")
    public String recommendations(Model model) {
        User currentUser = getCurrentUser();
        List<AccountantUserRelationship> relationships = accountantUserRelationshipRepository.findByAccountant(currentUser);
        List<User> connectedUsers = relationships.stream()
                .map(AccountantUserRelationship::getUser)
                .collect(Collectors.toList());
        
        Map<User, List<AccountantRecommendation>> recommendationsByUser = new HashMap<>();
        for (User user : connectedUsers) {
            List<AccountantRecommendation> userRecommendations = 
                    accountantRecommendationRepository.findByAccountantAndUser(currentUser, user);
            recommendationsByUser.put(user, userRecommendations);
        }
        
        model.addAttribute("user", currentUser);
        model.addAttribute("connectedUsers", connectedUsers);
        model.addAttribute("recommendationsByUser", recommendationsByUser);
        
        return "accountant/recommendations";
    }

    @GetMapping("/recommendations/{userId}/create")
    public String showCreateRecommendationForm(@PathVariable Long userId, Model model) {
        User currentUser = getCurrentUser();
        Optional<User> targetUserOpt = userRepository.findById(userId);
        
        if (targetUserOpt.isEmpty()) {
            return "redirect:/accountant/recommendations";
        }
        
        User targetUser = targetUserOpt.get();
        Optional<AccountantUserRelationship> relationshipOpt = 
                accountantUserRelationshipRepository.findByAccountantAndUser(currentUser, targetUser);
        
        if (relationshipOpt.isEmpty()) {
            return "redirect:/accountant/recommendations";
        }
        
        model.addAttribute("user", currentUser);
        model.addAttribute("targetUser", targetUser);
        model.addAttribute("recommendation", new AccountantRecommendation());
        
        return "accountant/recommendation-form";
    }

    @PostMapping("/recommendations/create")
    public String createRecommendation(@RequestParam Long userId,
                                      @RequestParam String message,
                                      RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<User> targetUserOpt = userRepository.findById(userId);
            
            if (targetUserOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден!");
                return "redirect:/accountant/recommendations";
            }
            
            User targetUser = targetUserOpt.get();
            Optional<AccountantUserRelationship> relationshipOpt = 
                    accountantUserRelationshipRepository.findByAccountantAndUser(currentUser, targetUser);
            
            if (relationshipOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не является вашим клиентом!");
                return "redirect:/accountant/recommendations";
            }
            
            AccountantRecommendation recommendation = new AccountantRecommendation();
            recommendation.setAccountant(currentUser);
            recommendation.setUser(targetUser);
            recommendation.setMessage(message);
            
            accountantRecommendationRepository.save(recommendation);
            
            redirectAttributes.addFlashAttribute("success", "Рекомендация создана!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при создании рекомендации: " + e.getMessage());
        }
        return "redirect:/accountant/recommendations";
    }

    @GetMapping("/recommendations/{id}/edit")
    public String showEditRecommendationForm(@PathVariable Long id, Model model) {
        User currentUser = getCurrentUser();
        Optional<AccountantRecommendation> recommendationOpt = accountantRecommendationRepository.findById(id);
        
        if (recommendationOpt.isEmpty()) {
            return "redirect:/accountant/recommendations";
        }
        
        AccountantRecommendation recommendation = recommendationOpt.get();
        
        if (!recommendation.getAccountant().getId().equals(currentUser.getId())) {
            return "redirect:/accountant/recommendations";
        }
        
        model.addAttribute("user", currentUser);
        model.addAttribute("recommendation", recommendation);
        model.addAttribute("targetUser", recommendation.getUser());
        
        return "accountant/recommendation-form";
    }

    @PostMapping("/recommendations/{id}/update")
    public String updateRecommendation(@PathVariable Long id,
                                      @RequestParam String message,
                                      RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<AccountantRecommendation> recommendationOpt = accountantRecommendationRepository.findById(id);
            
            if (recommendationOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Рекомендация не найдена!");
                return "redirect:/accountant/recommendations";
            }
            
            AccountantRecommendation recommendation = recommendationOpt.get();
            
            if (!recommendation.getAccountant().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Нет доступа к этой рекомендации!");
                return "redirect:/accountant/recommendations";
            }
            
            recommendation.setMessage(message);
            accountantRecommendationRepository.save(recommendation);
            
            redirectAttributes.addFlashAttribute("success", "Рекомендация обновлена!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении рекомендации: " + e.getMessage());
        }
        return "redirect:/accountant/recommendations";
    }

    @PostMapping("/recommendations/{id}/delete")
    public String deleteRecommendation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            Optional<AccountantRecommendation> recommendationOpt = accountantRecommendationRepository.findById(id);
            
            if (recommendationOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Рекомендация не найдена!");
                return "redirect:/accountant/recommendations";
            }
            
            AccountantRecommendation recommendation = recommendationOpt.get();
            
            if (!recommendation.getAccountant().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Нет доступа к этой рекомендации!");
                return "redirect:/accountant/recommendations";
            }
            
            accountantRecommendationRepository.delete(recommendation);
            
            redirectAttributes.addFlashAttribute("success", "Рекомендация удалена!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при удалении рекомендации: " + e.getMessage());
        }
        return "redirect:/accountant/recommendations";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        User currentUser = getCurrentUser();
        model.addAttribute("user", currentUser);
        return "accountant/settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String firstName,
                               @RequestParam(required = false) String lastName,
                               @RequestParam String email,
                               RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            currentUser.setFirstName(firstName);
            currentUser.setLastName(lastName);
            currentUser.setEmail(email);
            userRepository.save(currentUser);
            redirectAttributes.addFlashAttribute("success", "Профиль обновлен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении профиля: " + e.getMessage());
        }
        return "redirect:/accountant/settings";
    }

    @PostMapping("/settings/password")
    public String changePassword(@RequestParam String currentPassword,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser();
            
            if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "Неверный текущий пароль!");
                return "redirect:/accountant/settings";
            }
            
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Новый пароль и подтверждение не совпадают!");
                return "redirect:/accountant/settings";
            }
            
            currentUser.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(currentUser);
            redirectAttributes.addFlashAttribute("success", "Пароль изменен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при изменении пароля: " + e.getMessage());
        }
        return "redirect:/accountant/settings";
    }
}