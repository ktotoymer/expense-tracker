package com.expensetracker.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
public class CaptchaService {
    
    private final Map<String, Integer> captchaStorage = new HashMap<>();
    private final Random random = new Random();
    
    /**
     * Генерирует новую математическую капчу
     * @return Map с id капчи, вопросом и правильным ответом
     */
    public Map<String, String> generateCaptcha() {
        int num1 = random.nextInt(10) + 1; // от 1 до 10
        int num2 = random.nextInt(10) + 1; // от 1 до 10
        int answer = num1 + num2;
        
        String captchaId = UUID.randomUUID().toString();
        captchaStorage.put(captchaId, answer);
        
        Map<String, String> result = new HashMap<>();
        result.put("id", captchaId);
        result.put("question", num1 + " + " + num2 + " = ?");
        result.put("answer", String.valueOf(answer));
        
        return result;
    }
    
    /**
     * Проверяет ответ на капчу
     * @param captchaId ID капчи
     * @param userAnswer Ответ пользователя
     * @return true если ответ правильный
     */
    public boolean validateCaptcha(String captchaId, String userAnswer) {
        if (captchaId == null || userAnswer == null) {
            return false;
        }
        
        Integer correctAnswer = captchaStorage.get(captchaId);
        if (correctAnswer == null) {
            return false;
        }
        
        try {
            int answer = Integer.parseInt(userAnswer.trim());
            boolean isValid = answer == correctAnswer;
            
            // Удаляем использованную капчу
            captchaStorage.remove(captchaId);
            
            return isValid;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Очищает старые капчи (можно вызывать периодически)
     */
    public void cleanupOldCaptchas() {
        // В реальном приложении можно добавить логику очистки старых капч
        // Для простоты оставляем как есть, так как капчи удаляются после использования
    }
}

