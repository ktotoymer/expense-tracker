package com.expensetracker.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.expensetracker.service.AIService;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    // Рекомендации по оптимизации расходов
    @GetMapping("/recommend")
    public String recommend(@RequestParam String expensesData) throws Exception {
        return aiService.recommendExpenseOptimization(expensesData);
    }

    // Прогноз финансового положения
    @GetMapping("/predict")
    public String predict(@RequestParam String financialData) throws Exception {
        return aiService.predictFinancialSituation(financialData);
    }
}

