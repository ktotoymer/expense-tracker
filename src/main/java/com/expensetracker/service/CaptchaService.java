package com.expensetracker.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class CaptchaService {

    private static final Logger logger = LoggerFactory.getLogger(CaptchaService.class);
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    @Value("${recaptcha.secret.key}")
    private String recaptchaSecretKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Проверка Google reCAPTCHA v2
     *
     * @param recaptchaResponse значение параметра g-recaptcha-response с формы
     * @return true, если проверка успешна
     */
    public boolean verifyRecaptcha(String recaptchaResponse) {
        if (recaptchaResponse == null || recaptchaResponse.isBlank()) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", recaptchaSecretKey);
            body.add("response", recaptchaResponse);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(VERIFY_URL, requestEntity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warn("Не удалось проверить reCAPTCHA: неверный статус ответа {}", response.getStatusCode());
                return false;
            }

            Object successObject = response.getBody().get("success");
            if (successObject instanceof Boolean) {
                return (Boolean) successObject;
            }

            logger.warn("Ответ reCAPTCHA не содержит корректного флага success");
            return false;
        } catch (Exception ex) {
            logger.error("Ошибка при проверке reCAPTCHA", ex);
            return false;
        }
    }
}