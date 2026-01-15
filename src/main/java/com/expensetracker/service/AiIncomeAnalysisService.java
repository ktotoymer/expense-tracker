package com.expensetracker.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.expensetracker.entity.Income;
import com.expensetracker.entity.User;
import com.expensetracker.repository.IncomeRepository;

@Service
public class AiIncomeAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AiIncomeAnalysisService.class);

    private final IncomeRepository incomeRepository;
    private final AIService aiService;

    public AiIncomeAnalysisService(IncomeRepository incomeRepository,
                                    AIService aiService) {
        this.incomeRepository = incomeRepository;
        this.aiService = aiService;
    }

    /**
     * Анализирует доходы пользователя за последние 30 дней и возвращает текст рекомендаций от ИИ.
     */
    public String analyzeLastMonth(User user) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(30);

            List<Income> incomes = incomeRepository.findByUserAndDateBetween(user, from, to);

            if (incomes.isEmpty()) {
                return "За последние 30 дней у вас не зафиксировано доходов. Как только появятся данные по доходам, я смогу дать рекомендации.";
            }

            BigDecimal totalIncome = incomes.stream()
                    .map(Income::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, BigDecimal> byType = incomes.stream()
                    .collect(Collectors.groupingBy(
                            i -> i.getType() != null ? i.getType().name() : "Без типа",
                            Collectors.mapping(Income::getAmount,
                                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                    ));

            StringBuilder sb = new StringBuilder();
            sb.append("Проанализируй мои доходы за последние 30 дней и дай рекомендации по их оптимизации и увеличению.\\n");
            sb.append("Общая сумма доходов: ").append(totalIncome).append(" RUB\\n");
            sb.append("Доходы по типам (тип = сумма в RUB):\\n");
            byType.forEach((name, amount) -> sb
                    .append("- ")
                    .append(name)
                    .append(" = ")
                    .append(amount)
                    .append(" RUB\\n"));

            sb.append("Сформируй краткие и практические советы: как можно увеличить доходы, какие источники дохода развивать, как оптимизировать структуру доходов. Пиши по-русски, списком, без общих фраз и без упоминания того, что ты ИИ.");

            logger.info("Requesting AI income analysis for user: {}", user.getUsername());
            String result = aiService.analyzeIncome(sb.toString());
            logger.info("AI income analysis completed successfully");
            return result;
        } catch (Exception e) {
            logger.error("Error during AI income analysis: {}", e.getMessage(), e);
            String userMessage = e.getMessage();
            if (userMessage != null && (userMessage.contains("недоступен") || userMessage.contains("Попробуйте позже") || userMessage.contains("Обратитесь"))) {
                return userMessage;
            }
            return "Не удалось получить рекомендации от ИИ. Попробуйте позже.";
        }
    }
}
