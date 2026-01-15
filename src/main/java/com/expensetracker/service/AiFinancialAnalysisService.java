package com.expensetracker.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.Income;
import com.expensetracker.entity.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.IncomeRepository;

@Service
public class AiFinancialAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AiFinancialAnalysisService.class);

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final AIService aiService;

    public AiFinancialAnalysisService(IncomeRepository incomeRepository,
                                     ExpenseRepository expenseRepository,
                                     AIService aiService) {
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.aiService = aiService;
    }

    /**
     * Анализирует общее финансовое положение пользователя (доходы и расходы) за последние 30 дней и возвращает текст рекомендаций от ИИ.
     */
    public String analyzeLastMonth(User user) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(30);

            List<Income> incomes = incomeRepository.findByUserAndDateBetween(user, from, to);
            List<Expense> expenses = expenseRepository.findByUserAndDateBetween(user, from, to);

            if (incomes.isEmpty() && expenses.isEmpty()) {
                return "За последние 30 дней у вас не зафиксировано финансовых операций. Как только появятся данные, я смогу дать рекомендации.";
            }

            BigDecimal totalIncome = incomes.stream()
                    .map(Income::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalExpense = expenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal balance = totalIncome.subtract(totalExpense);

            Map<String, BigDecimal> expensesByCategory = expenses.stream()
                    .collect(Collectors.groupingBy(
                            e -> {
                                Category c = e.getCategory();
                                return c != null ? c.getName() : "Без категории";
                            },
                            Collectors.mapping(Expense::getAmount,
                                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                    ));

            Map<String, BigDecimal> incomesByType = incomes.stream()
                    .collect(Collectors.groupingBy(
                            i -> i.getType() != null ? i.getType().name() : "Без типа",
                            Collectors.mapping(Income::getAmount,
                                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                    ));

            StringBuilder sb = new StringBuilder();
            sb.append("Проанализируй мое общее финансовое положение за последние 30 дней и дай комплексные рекомендации.\\n");
            sb.append("Общая сумма доходов: ").append(totalIncome).append(" RUB\\n");
            sb.append("Общая сумма расходов: ").append(totalExpense).append(" RUB\\n");
            sb.append("Баланс (доходы - расходы): ").append(balance).append(" RUB\\n");
            sb.append("\\nДоходы по типам (тип = сумма в RUB):\\n");
            incomesByType.forEach((name, amount) -> sb
                    .append("- ")
                    .append(name)
                    .append(" = ")
                    .append(amount)
                    .append(" RUB\\n"));
            sb.append("\\nРасходы по категориям (категория = сумма в RUB):\\n");
            expensesByCategory.forEach((name, amount) -> sb
                    .append("- ")
                    .append(name)
                    .append(" = ")
                    .append(amount)
                    .append(" RUB\\n"));

            sb.append("Сформируй краткие и практические советы: как улучшить финансовое положение, на чем можно сэкономить, как увеличить доходы, какие привычки изменить. Пиши по-русски, списком, без общих фраз и без упоминания того, что ты ИИ.");

            logger.info("Requesting AI financial analysis for user: {}", user.getUsername());
            String result = aiService.analyzeFinancialSituation(sb.toString());
            logger.info("AI financial analysis completed successfully");
            return result;
        } catch (Exception e) {
            logger.error("Error during AI financial analysis: {}", e.getMessage(), e);
            String userMessage = e.getMessage();
            if (userMessage != null && (userMessage.contains("недоступен") || userMessage.contains("Попробуйте позже") || userMessage.contains("Обратитесь"))) {
                return userMessage;
            }
            return "Не удалось получить рекомендации от ИИ. Попробуйте позже.";
        }
    }
}
