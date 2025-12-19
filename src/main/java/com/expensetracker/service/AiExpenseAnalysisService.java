package com.expensetracker.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.User;
import com.expensetracker.repository.TransactionRepository;

@Service
public class AiExpenseAnalysisService {

    private final TransactionRepository transactionRepository;
    private final GigaChatClient gigaChatClient;

    public AiExpenseAnalysisService(TransactionRepository transactionRepository,
                                    GigaChatClient gigaChatClient) {
        this.transactionRepository = transactionRepository;
        this.gigaChatClient = gigaChatClient;
    }

    /**
     * Анализирует расходы пользователя за последние 30 дней и возвращает текст рекомендаций от ИИ.
     */
    public String analyzeLastMonth(User user) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);

        List<Transaction> transactions = transactionRepository
                .findByUserAndDateBetweenOrderByDateDesc(user, from, to);

        if (transactions.isEmpty()) {
            return "За последние 30 дней у вас нет данных по транзакциям. Добавьте расходы, чтобы получить персональные рекомендации.";
        }

        List<Transaction> expenses = transactions.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                .collect(Collectors.toList());

        if (expenses.isEmpty()) {
            return "За последние 30 дней у вас не зафиксировано расходов. Как только появятся данные по тратам, я смогу дать рекомендации.";
        }

        BigDecimal totalExpense = expenses.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> byCategory = expenses.stream()
                .collect(Collectors.groupingBy(
                        t -> {
                            Category c = t.getCategory();
                            return c != null ? c.getName() : "Без категории";
                        },
                        Collectors.mapping(Transaction::getAmount,
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

        return gigaChatClient.getRecommendations(sb.toString());
    }
}
