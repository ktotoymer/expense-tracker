package com.expensetracker.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Утилитный класс для форматирования денежных сумм с сокращениями.
 */
public class MoneyFormatter {
    
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.#", new DecimalFormatSymbols(Locale.US));
    
    /**
     * Форматирует сумму денег с сокращениями (тыс., млн).
     * 
     * @param amount сумма для форматирования
     * @return отформатированная строка (например, "10 тыс.", "100,5 тыс.", "1,2 млн")
     */
    public static String formatWithAbbreviation(BigDecimal amount) {
        if (amount == null) {
            return "₽0";
        }
        
        BigDecimal absAmount = amount.abs();
        boolean isNegative = amount.compareTo(BigDecimal.ZERO) < 0;
        String sign = isNegative ? "-" : "";
        
        // Если сумма меньше 1000, просто форматируем как есть
        if (absAmount.compareTo(BigDecimal.valueOf(1000)) < 0) {
            // Для сумм меньше 1000 используем целое число без десятичных знаков
            long intValue = absAmount.setScale(0, RoundingMode.HALF_UP).longValue();
            return sign + "₽" + intValue;
        }
        
        // Если сумма меньше 1 млн, форматируем в тысячах
        if (absAmount.compareTo(BigDecimal.valueOf(1000000)) < 0) {
            BigDecimal thousands = absAmount.divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
            String formatted = formatDecimal(thousands);
            return sign + "₽" + formatted + " тыс.";
        }
        
        // Если сумма больше или равна 1 млн, форматируем в миллионах
        BigDecimal millions = absAmount.divide(BigDecimal.valueOf(1000000), 2, RoundingMode.HALF_UP);
        String formatted = formatDecimal(millions);
        return sign + "₽" + formatted + " млн";
    }
    
    /**
     * Форматирует десятичное число, убирая ненужные нули.
     */
    private static String formatDecimal(BigDecimal value) {
        String formatted = DECIMAL_FORMAT.format(value.doubleValue());
        // Заменяем точку на запятую для русского формата
        return formatted.replace(".", ",");
    }
    
    /**
     * Форматирует сумму для использования в Thymeleaf шаблонах.
     * Это метод для обратной совместимости со старым форматированием.
     */
    public static String format(BigDecimal amount) {
        return formatWithAbbreviation(amount);
    }
}

