package com.expensetracker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CaptchaServiceTest {

    private CaptchaService captchaService;

    @BeforeEach
    void setUp() {
        captchaService = new CaptchaService();
    }

    @Test
    void testGenerateCaptcha() {
        Map<String, String> captcha = captchaService.generateCaptcha();

        assertNotNull(captcha);
        assertTrue(captcha.containsKey("id"));
        assertTrue(captcha.containsKey("question"));
        assertTrue(captcha.containsKey("answer"));
        assertNotNull(captcha.get("id"));
        assertNotNull(captcha.get("question"));
        assertNotNull(captcha.get("answer"));
    }

    @Test
    void testGenerateCaptchaMultipleTimes() {
        Map<String, String> captcha1 = captchaService.generateCaptcha();
        Map<String, String> captcha2 = captchaService.generateCaptcha();

        assertNotEquals(captcha1.get("id"), captcha2.get("id"));
    }

    @Test
    void testValidateCaptchaWithCorrectAnswer() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        String captchaId = captcha.get("id");
        String correctAnswer = captcha.get("answer");

        boolean isValid = captchaService.validateCaptcha(captchaId, correctAnswer);

        assertTrue(isValid);
    }

    @Test
    void testValidateCaptchaWithIncorrectAnswer() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        String captchaId = captcha.get("id");
        String wrongAnswer = "999";

        boolean isValid = captchaService.validateCaptcha(captchaId, wrongAnswer);

        assertFalse(isValid);
    }

    @Test
    void testValidateCaptchaWithNullId() {
        boolean isValid = captchaService.validateCaptcha(null, "5");

        assertFalse(isValid);
    }

    @Test
    void testValidateCaptchaWithNullAnswer() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        String captchaId = captcha.get("id");

        boolean isValid = captchaService.validateCaptcha(captchaId, null);

        assertFalse(isValid);
    }

    @Test
    void testValidateCaptchaWithNonExistentId() {
        boolean isValid = captchaService.validateCaptcha("non-existent-id", "5");

        assertFalse(isValid);
    }

    @Test
    void testValidateCaptchaWithInvalidNumberFormat() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        String captchaId = captcha.get("id");

        boolean isValid = captchaService.validateCaptcha(captchaId, "not-a-number");

        assertFalse(isValid);
    }

    @Test
    void testValidateCaptchaRemovesAfterValidation() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        String captchaId = captcha.get("id");
        String correctAnswer = captcha.get("answer");

        // Первая валидация должна быть успешной
        boolean firstValidation = captchaService.validateCaptcha(captchaId, correctAnswer);
        assertTrue(firstValidation);

        // Вторая валидация с тем же ID должна провалиться, так как капча удалена
        boolean secondValidation = captchaService.validateCaptcha(captchaId, correctAnswer);
        assertFalse(secondValidation);
    }

    @Test
    void testValidateCaptchaWithWhitespace() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        String captchaId = captcha.get("id");
        String correctAnswer = captcha.get("answer");

        // Ответ с пробелами должен быть обработан корректно
        boolean isValid = captchaService.validateCaptcha(captchaId, " " + correctAnswer + " ");

        assertTrue(isValid);
    }

    @Test
    void testCaptchaQuestionFormat() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        String question = captcha.get("question");

        assertNotNull(question);
        assertTrue(question.contains("+"));
        assertTrue(question.contains("="));
        assertTrue(question.contains("?"));
    }

    @Test
    void testCaptchaAnswerIsNumeric() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        String answer = captcha.get("answer");

        assertNotNull(answer);
        assertDoesNotThrow(() -> Integer.parseInt(answer));
    }
}

