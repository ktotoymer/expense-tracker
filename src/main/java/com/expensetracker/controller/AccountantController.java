package com.expensetracker.controller;

import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.User;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/accountant")
@PreAuthorize("hasRole('ACCOUNTANT')")
public class AccountantController {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public AccountantController(UserRepository userRepository,
                               TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
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

        // Получаем только пользователей с ролью USER
        List<User> allUsers = userRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("ROLE_USER")))
                .collect(Collectors.toList());

        // Общая статистика
        long totalUsers = allUsers.size();
        long totalTransactions = transactionRepository.count();

        // Подсчет общей статистики через всех пользователей с ролью USER
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (User user : allUsers) {
            BigDecimal userIncome = transactionRepository.sumIncomeByUserAndDateAfter(user, startOfMonth);
            BigDecimal userExpense = transactionRepository.sumExpenseByUserAndDateAfter(user, startOfMonth);
            if (userIncome != null) totalIncome = totalIncome.add(userIncome);
            if (userExpense != null) totalExpense = totalExpense.add(userExpense);
        }

        BigDecimal balance = totalIncome.subtract(totalExpense);

        // Статистика по пользователям
        Map<String, UserStats> userStatsMap = new HashMap<>();
        for (User user : allUsers) {
            BigDecimal userIncome = transactionRepository.sumIncomeByUserAndDateAfter(user, startOfMonth);
            BigDecimal userExpense = transactionRepository.sumExpenseByUserAndDateAfter(user, startOfMonth);
            if (userIncome == null) userIncome = BigDecimal.ZERO;
            if (userExpense == null) userExpense = BigDecimal.ZERO;

            long userTransactionsCount = transactionRepository.findByUserOrderByDateDesc(user).size();

            userStatsMap.put(user.getUsername(), new UserStats(
                user.getFirstName() + " " + user.getLastName(),
                userIncome,
                userExpense,
                userTransactionsCount
            ));
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("balance", balance);
        model.addAttribute("userStats", userStatsMap);

        return "accountant/dashboard";
    }

    @GetMapping("/transactions")
    public String transactions(Model model,
                              @RequestParam(required = false) Long userId,
                              @RequestParam(required = false) String type,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate) {
        User currentUser = getCurrentUser();
        // Получаем только пользователей с ролью USER
        List<User> allUsers = userRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("ROLE_USER")))
                .collect(Collectors.toList());
        List<Transaction> transactions;

        if (userId != null) {
            User targetUser = userRepository.findById(userId).orElse(null);
            if (targetUser != null) {
                transactions = transactionRepository.findByUserOrderByDateDesc(targetUser);
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

        return "accountant/transactions";
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

        // Общая статистика
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        long totalTransactions = 0;

        // Получаем только пользователей с ролью USER
        List<User> allUsers = userRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("ROLE_USER")))
                .collect(Collectors.toList());
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

        return "accountant/reports";
    }

    @GetMapping("/users")
    public String users(Model model) {
        User currentUser = getCurrentUser();
        // Получаем только пользователей с ролью USER
        List<User> allUsers = userRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("ROLE_USER")))
                .collect(Collectors.toList());

        // Статистика для каждого пользователя
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        Map<Long, UserFinancialStats> userFinancialStats = new HashMap<>();

        for (User targetUser : allUsers) {
            BigDecimal userIncome = transactionRepository.sumIncomeByUserAndDateAfter(targetUser, startOfMonth);
            BigDecimal userExpense = transactionRepository.sumExpenseByUserAndDateAfter(targetUser, startOfMonth);
            if (userIncome == null) userIncome = BigDecimal.ZERO;
            if (userExpense == null) userExpense = BigDecimal.ZERO;

            long userTransactionsCount = transactionRepository.findByUserOrderByDateDesc(targetUser).size();

            userFinancialStats.put(targetUser.getId(), new UserFinancialStats(
                userIncome,
                userExpense,
                userTransactionsCount
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

        public UserFinancialStats(BigDecimal income, BigDecimal expense, long transactionsCount) {
            this.income = income;
            this.expense = expense;
            this.transactionsCount = transactionsCount;
        }

        public BigDecimal getIncome() { return income; }
        public BigDecimal getExpense() { return expense; }
        public long getTransactionsCount() { return transactionsCount; }
    }
}

