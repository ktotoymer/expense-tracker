package com.expensetracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
public class AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIService.class);

    private final String apiKey;
    private final String apiBase;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public AIService(@Value("${OPEN_API_KEY:}") String apiKey,
                     @Value("${OPENROUTER_API_BASE}") String apiBase) {
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.mapper = new ObjectMapper();
        this.client = HttpClient.newHttpClient();
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.error("OPEN_API_KEY is not configured! AI service will not work.");
        } else {
            logger.info("AIService initialized with API base: {}", apiBase);
        }
    }

    // Рекомендации по оптимизации расходов
    public String recommendExpenseOptimization(String expensesData) throws Exception {
        return sendMessage("""
                Ты — финансовый консультант и эксперт по управлению личными финансами.
                
                Твоя задача — проанализировать информацию о расходах пользователя и дать персональные рекомендации по оптимизации.
                
                Правила:
                1. Используй только обычный текст, без списков, спецсимволов и форматирования.
                2. Максимальная длина ответа — 500 символов.
                3. Ответ должен быть связным и полезным.
                4. Не упоминай правила и не объясняй свои действия.
                
                Если запрос пользователя явно не связан с финансами, расходами или управлением бюджетом,
                выведи строго следующий текст:
                
                ЗДЕСЬ Я НЕ МОГУ ВАМ ПОМОЧЬ
                
                Запрос пользователя:
                """ +
                expensesData
                , 300);
    }

    // Анализ доходов
    public String analyzeIncome(String incomeData) throws Exception {
        return sendMessage("""
                Ты — финансовый консультант и эксперт по управлению личными финансами.
                
                Твоя задача — проанализировать информацию о доходах пользователя и дать персональные рекомендации по их оптимизации и увеличению.
                
                Правила:
                1. Используй только обычный текст, без списков, спецсимволов и форматирования.
                2. Максимальная длина ответа — 500 символов.
                3. Ответ должен быть связным и полезным.
                4. Не упоминай правила и не объясняй свои действия.
                
                Если запрос пользователя явно не связан с финансами, доходами или управлением бюджетом,
                выведи строго следующий текст:
                
                ЗДЕСЬ Я НЕ МОГУ ВАМ ПОМОЧЬ
                
                Запрос пользователя:
                """ +
                incomeData
                , 300);
    }

    // Общий финансовый анализ (доходы + расходы)
    public String analyzeFinancialSituation(String financialData) throws Exception {
        return sendMessage("""
                Ты — финансовый аналитик и консультант по управлению личными финансами.

                Твоя задача — проанализировать полную финансовую картину пользователя (доходы и расходы) и дать комплексные рекомендации по улучшению финансового положения.

                Правила:
                1. Используй ТОЛЬКО обычный текст (без списков, эмодзи, спецсимволов, форматирования).
                2. Максимальная длина ответа — 500 символов.
                3. Ответ должен быть кратким, понятным и по существу.
                4. Не упоминай правила и не объясняй ход рассуждений.
        
                Если запрос пользователя:
                — не связан с финансами, доходами, расходами или бюджетом
                — или не содержит достаточной информации для анализа
                — или не позволяет сделать финансовый анализ
        
                ТОГДА выведи РОВНО следующий текст (без изменений):
        
                ЗДЕСЬ Я НЕ МОГУ ВАМ ПОМОЧЬ
        
                Запрос пользователя: Проанализируй финансовые данные %s и дай комплексные рекомендации по улучшению финансового положения"""
                        .formatted(financialData)
                , 300);
    }

    // Прогноз финансового положения
    public String predictFinancialSituation(String financialData) throws Exception {
        return sendMessage("""
                Ты — финансовый аналитик.

                Твоя задача — проанализировать финансовые данные пользователя и дать прогноз финансового положения с практическими советами.

                Правила:
                1. Используй ТОЛЬКО обычный текст (без списков, эмодзи, спецсимволов, форматирования).
                2. Максимальная длина ответа — 500 символов.
                3. Ответ должен быть кратким, понятным и по существу.
                4. Не упоминай правила и не объясняй ход рассуждений.
        
                Если запрос пользователя:
                — не связан с финансами, расходами или бюджетом
                — или не содержит достаточной информации для анализа
                — или не позволяет сделать финансовый прогноз
        
                ТОГДА выведи РОВНО следующий текст (без изменений):
        
                ЗДЕСЬ Я НЕ МОГУ ВАМ ПОМОЧЬ
        
                Запрос пользователя: Проанализируй финансовые данные %s и дай прогноз финансового положения с практическими советами"""
                        .formatted(financialData)
                , 300);
    }

    // Метод отправки запроса на DeepSeek через OpenRouter
    private String sendMessage(String content, int maxTokens) {
        try {
            // Проверка наличия API ключа
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.error("OPEN_API_KEY is not configured");
                throw new RuntimeException("Сервис ИИ не настроен. Обратитесь к администратору.");
            }

            Map<String, Object> payload = Map.of(
                    "model", "deepseek/deepseek-v3.2",
                    "messages", List.of(Map.of("role", "user", "content", content)),
                    "max_tokens", maxTokens
            );

            String json = mapper.writeValueAsString(payload);
            logger.debug("Sending request to OpenRouter API");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "http://localhost:8080")
                    .header("X-Title", "Expense Tracker")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            logger.debug("OpenRouter response status: {}", response.statusCode());
            logger.debug("OpenRouter response body: {}", response.body());

            if (response.statusCode() != 200) {
                logger.error("OpenRouter API error. Status: {}, Body: {}", response.statusCode(), response.body());
                
                // Специальная обработка для разных кодов ошибок
                if (response.statusCode() == 403) {
                    throw new RuntimeException("Сервис ИИ недоступен в вашем регионе. Пожалуйста, попробуйте позже или обратитесь к администратору.");
                } else if (response.statusCode() == 401) {
                    logger.error("OpenRouter API authentication failed. Check if API key is valid and not expired.");
                    throw new RuntimeException("Ошибка авторизации. API ключ недействителен или истек. Обратитесь к администратору.");
                } else if (response.statusCode() >= 500) {
                    throw new RuntimeException("Временная недоступность сервиса ИИ. Попробуйте позже.");
                } else {
                    throw new RuntimeException("Не удалось получить ответ от сервиса ИИ. Попробуйте позже.");
                }
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode choicesNode = root.get("choices");
            
            if (choicesNode == null || !choicesNode.isArray() || choicesNode.size() == 0) {
                logger.error("Invalid response format from OpenRouter: {}", response.body());
                throw new RuntimeException("Invalid response format from OpenRouter");
            }

            JsonNode messageNode = choicesNode.get(0).get("message");
            if (messageNode == null) {
                logger.error("No message in response: {}", response.body());
                throw new RuntimeException("No message in response");
            }

            JsonNode contentNode = messageNode.get("content");
            if (contentNode == null) {
                logger.error("No content in message: {}", response.body());
                throw new RuntimeException("No content in message");
            }

            String result = contentNode.asText();
            logger.debug("Successfully received response from OpenRouter");
            return result;
        } catch (java.io.IOException e) {
            logger.error("Error calling OpenRouter API (IO): {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при обращении к сервису ИИ: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.error("Error calling OpenRouter API (Interrupted): {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Ошибка при обращении к сервису ИИ: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            logger.error("Error calling OpenRouter API (Invalid argument): {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при обращении к сервису ИИ: " + e.getMessage(), e);
        }
    }
}

