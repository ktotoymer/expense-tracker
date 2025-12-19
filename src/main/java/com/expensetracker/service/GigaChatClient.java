package com.expensetracker.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GigaChatClient {

    private static final Logger logger = LoggerFactory.getLogger(GigaChatClient.class);

    @Value("${gigachat.api.url:https://gigachat.devices.sberbank.ru/api/v1/chat/completions}")
    private String apiUrl;

    @Value("${gigachat.api.token:}")
    private String apiToken;

    @Value("${gigachat.model:GigaChat-2-Pro}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getRecommendations(String prompt) {
        if (apiToken == null || apiToken.isBlank()) {
            logger.warn("GigaChat API token is not configured. Returning fallback message.");
            return "ИИ‑рекомендации недоступны: не настроен токен GigaChat. Обратитесь к администратору приложения.";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiToken);

            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "Ты финансовый ассистент. Проанализируй траты пользователя и дай понятные, практические рекомендации по оптимизации расходов без крайних мер экономии.");

            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(systemMessage, userMessage));
            body.put("temperature", 0.4);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warn("GigaChat response status is not OK: {}", response.getStatusCode());
                return "Не удалось получить рекомендации от ИИ. Попробуйте позже.";
            }

            Object choicesObj = response.getBody().get("choices");
            if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
                Object first = choices.get(0);
                if (first instanceof Map<?, ?> firstMap) {
                    Object messageObj = firstMap.get("message");
                    if (messageObj instanceof Map<?, ?> messageMap) {
                        Object content = messageMap.get("content");
                        if (content != null) {
                            return content.toString();
                        }
                    }
                }
            }

            logger.warn("Unexpected GigaChat response format");
            return "Не удалось обработать ответ ИИ. Попробуйте позже.";
        } catch (Exception ex) {
            logger.error("Error while calling GigaChat API", ex);
            return "Произошла ошибка при обращении к ИИ‑ассистенту. Попробуйте позже.";
        }
    }
}
