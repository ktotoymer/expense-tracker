package com.expensetracker.service;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.Income;
import com.expensetracker.entity.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.IncomeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для расчета финансовой статистики (шаблон Expert).
 * 
 * Согласно шаблону Expert, класс, обладающий информацией, необходимой для
 * выполнения функциональности, должен отвечать за эту функциональность.
 * StatisticsService является экспертом по финансовым расчетам и статистике,
 * так как он имеет доступ ко всем доходам и расходам и знает, как их обрабатывать.
 * 
 * Преимущества:
 * - Инкапсуляция логики расчета статистики
 * - Высокая связность (High Cohesion) - все методы связаны с расчетом статистики
 * - Низкая связанность (Low Coupling) - контроллеры не зависят от деталей расчетов
 */
@Service
public class StatisticsService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;

    public StatisticsService(IncomeRepository incomeRepository, ExpenseRepository expenseRepository) {
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
    }

    /**
     * Рассчитывает общий доход пользователя за период.
     * 
     * @param user пользователь
     * @param startDate начальная дата периода
     * @return общий доход (или BigDecimal.ZERO, если доходов нет)
     */
    public BigDecimal calculateTotalIncome(User user, LocalDate startDate) {
        List<Income> incomes = incomeRepository.findByUserOrderByDateDesc(user);
        LocalDate endDate = LocalDate.now();
        return incomes.stream()
                .filter(i -> !i.getDate().isBefore(startDate) && !i.getDate().isAfter(endDate))
                .map(Income::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Рассчитывает общий расход пользователя за период.
     * 
     * @param user пользователь
     * @param startDate начальная дата периода
     * @return общий расход (или BigDecimal.ZERO, если расходов нет)
     */
    public BigDecimal calculateTotalExpense(User user, LocalDate startDate) {
        List<Expense> expenses = expenseRepository.findByUserOrderByDateDesc(user);
        LocalDate endDate = LocalDate.now();
        return expenses.stream()
                .filter(e -> !e.getDate().isBefore(startDate) && !e.getDate().isAfter(endDate))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Рассчитывает баланс (доходы минус расходы) за период.
     * 
     * @param user пользователь
     * @param startDate начальная дата периода
     * @return баланс
     */
    public BigDecimal calculateBalance(User user, LocalDate startDate) {
        BigDecimal income = calculateTotalIncome(user, startDate);
        BigDecimal expense = calculateTotalExpense(user, startDate);
        return income.subtract(expense);
    }

    /**
     * Рассчитывает общий баланс (доходы минус расходы) за всё время.
     * 
     * @param user пользователь
     * @return общий баланс
     */
    public BigDecimal calculateTotalBalance(User user) {
        List<Income> allIncomes = incomeRepository.findByUserOrderByDateDesc(user);
        BigDecimal totalIncome = allIncomes.stream()
                .map(Income::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        List<Expense> allExpenses = expenseRepository.findByUserOrderByDateDesc(user);
        BigDecimal totalExpense = allExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return totalIncome.subtract(totalExpense);
    }

    /**
     * Рассчитывает статистику по категориям расходов за период (включая расходы без категории).
     * 
     * @param user пользователь
     * @param startDate начальная дата периода
     * @param endDate конечная дата периода
     * @return карта статистики по категориям (имя категории -> сумма расходов)
     */
    public Map<String, CategoryStatistics> calculateCategoryStatistics(User user, 
                                                                        LocalDate startDate, 
                                                                        LocalDate endDate) {
        List<Expense> expenses = expenseRepository.findByUserAndDateBetween(user, startDate, endDate);

        Map<String, CategoryStatistics> statsMap = new HashMap<>();
        
        // Сумма расходов без категории
        BigDecimal noCategoryAmount = BigDecimal.ZERO;
        
        for (Expense expense : expenses) {
            if (expense.getCategory() != null) {
                Category category = expense.getCategory();
                String categoryName = category.getName();
                
                CategoryStatistics stat = statsMap.getOrDefault(categoryName,
                        new CategoryStatistics(categoryName, category.getColor()));
                stat.addAmount(expense.getAmount());
                statsMap.put(categoryName, stat);
            } else {
                // Считаем расходы без категории
                noCategoryAmount = noCategoryAmount.add(expense.getAmount());
            }
        }
        
        // Добавляем категорию "Без категории", если есть расходы без категории
        if (noCategoryAmount.compareTo(BigDecimal.ZERO) > 0) {
            statsMap.put("Без категории", new CategoryStatistics("Без категории", "#95a5a6"));
            statsMap.get("Без категории").addAmount(noCategoryAmount);
        }

        return statsMap;
    }

    /**
     * Рассчитывает статистику по категориям с процентами от общего расхода.
     * 
     * @param user пользователь
     * @param startDate начальная дата периода
     * @param endDate конечная дата периода
     * @return список статистики по категориям, отсортированный по сумме (убывание)
     */
    public List<CategoryStatistics> calculateCategoryStatisticsWithPercentages(User user,
                                                                                LocalDate startDate,
                                                                                LocalDate endDate) {
        Map<String, CategoryStatistics> statsMap = calculateCategoryStatistics(user, startDate, endDate);
        
        // Рассчитываем общий расход за указанный период (для правильного расчета процентов)
        List<Expense> expensesInPeriod = expenseRepository.findByUserAndDateBetween(user, startDate, endDate);
        BigDecimal totalExpense = expensesInPeriod.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalForPercentage = totalExpense.compareTo(BigDecimal.ZERO) > 0 
                ? totalExpense 
                : BigDecimal.ONE;

        List<CategoryStatistics> stats = statsMap.values().stream()
                .peek(stat -> {
                    int percentage = stat.getAmount()
                            .divide(totalForPercentage, 2, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .intValue();
                    stat.setPercentage(percentage);
                })
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .collect(Collectors.toList());

        return stats;
    }

    /**
     * Рассчитывает общую статистику для всех пользователей с ролью USER.
     * 
     * @param users список пользователей
     * @param startDate начальная дата периода
     * @return объект с общей статистикой
     */
    public OverallStatistics calculateOverallStatistics(List<User> users, LocalDate startDate) {
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        long totalItems = 0;

        for (User user : users) {
            BigDecimal userIncome = calculateTotalIncome(user, startDate);
            BigDecimal userExpense = calculateTotalExpense(user, startDate);
            totalIncome = totalIncome.add(userIncome);
            totalExpense = totalExpense.add(userExpense);
            
            List<Income> userIncomes = incomeRepository.findByUserOrderByDateDesc(user);
            List<Expense> userExpenses = expenseRepository.findByUserOrderByDateDesc(user);
            totalItems += userIncomes.size() + userExpenses.size();
        }

        return new OverallStatistics(totalIncome, totalExpense, totalItems);
    }

    /**
     * Внутренний класс для хранения статистики по категории.
     */
    public static class CategoryStatistics {
        private String name;
        private String color;
        private BigDecimal amount = BigDecimal.ZERO;
        private int percentage = 0;

        public CategoryStatistics(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public void addAmount(BigDecimal amount) {
            this.amount = this.amount.add(amount);
        }

        public String getName() { return name; }
        public String getColor() { return color; }
        public BigDecimal getAmount() { return amount; }
        public int getPercentage() { return percentage; }
        public void setPercentage(int percentage) { this.percentage = percentage; }
    }

    /**
     * Внутренний класс для хранения общей статистики.
     */
    public static class OverallStatistics {
        private final BigDecimal totalIncome;
        private final BigDecimal totalExpense;
        private final long totalItems;

        public OverallStatistics(BigDecimal totalIncome, BigDecimal totalExpense, long totalItems) {
            this.totalIncome = totalIncome;
            this.totalExpense = totalExpense;
            this.totalItems = totalItems;
        }

        public BigDecimal getTotalIncome() { return totalIncome; }
        public BigDecimal getTotalExpense() { return totalExpense; }
        public long getTotalTransactions() { return totalItems; } // Для обратной совместимости
        public long getTotalItems() { return totalItems; }
        public BigDecimal getBalance() {
            return totalIncome.subtract(totalExpense);
        }
    }
}

