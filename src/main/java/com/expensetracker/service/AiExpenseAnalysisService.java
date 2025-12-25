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
import com.expensetracker.entity.User;
import com.expensetracker.repository.ExpenseRepository;

@Service
public class AiExpenseAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AiExpenseAnalysisService.class);

    private final ExpenseRepository expenseRepository;
    private final AIService aiService;

    public AiExpenseAnalysisService(ExpenseRepository expenseRepository,
                                    AIService aiService) {
        this.expenseRepository = expenseRepository;
        this.aiService = aiService;
    }

    /**
     * Анализирует расходы пользователя за последние 30 дней и возвращает текст рекомендаций от ИИ.
     */
    public String analyzeLastMonth(User user) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(30);

            List<Expense> expenses = expenseRepository.findByUserAndDateBetween(user, from, to);

            if (expenses.isEmpty()) {
                return "За последние 30 дней у вас не зафиксировано расходов. Как только появятся данные по тратам, я смогу дать рекомендации.";
            }

            BigDecimal totalExpense = expenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, BigDecimal> byCategory = expenses.stream()
                    .collect(Collectors.groupingBy(
                            e -> {
                                Category c = e.getCategory();
                                return c != null ? c.getName() : "Без категории";
                            },
                            Collectors.mapping(Expense::getAmount,
                                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                    ));

            StringBuilder sb = new StringBuilder();
            sb.append("Проанализируй мои расходы за последние 30 дней и дай рекомендации по оптимизации.\\n");
            sb.append("Общая сумма расходов: ").append(totalExpense).append(" RUB\\n");
            sb.append("Расходы по категориям (категория = сумма в RUB):\\n");
            byCategory.forEach((name, amount) -> sb
                    .append("- ")
                    .append(name)
                    .append(" = ")
                    .append(amount)
                    .append(" RUB\\n"));

            sb.append("Сформируй краткие и практические советы: на чем можно сэкономить, какие лимиты по категориям стоит себе поставить и какие привычки поменять. Пиши по-русски, списком, без общих фраз и без упоминания того, что ты ИИ.");

            logger.info("Requesting AI analysis for user: {}", user.getUsername());
            String result = aiService.recommendExpenseOptimization(sb.toString());
            logger.info("AI analysis completed successfully");
            return result;
        } catch (Exception e) {
            logger.error("Error during AI analysis: {}", e.getMessage(), e);
            // Показываем пользователю понятное сообщение, без технических деталей
            String userMessage = e.getMessage();
            // Если сообщение уже содержит понятный текст для пользователя, используем его
            if (userMessage != null && (userMessage.contains("недоступен") || userMessage.contains("Попробуйте позже") || userMessage.contains("Обратитесь"))) {
                return userMessage;
            }
            // Иначе показываем общее сообщение
            return "Не удалось получить рекомендации от ИИ. Попробуйте позже.";
        }
    }
}
