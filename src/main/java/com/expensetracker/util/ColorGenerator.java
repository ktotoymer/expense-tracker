package com.expensetracker.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилитный класс для генерации уникальных цветов для категорий.
 */
public class ColorGenerator {
    
    // Предопределенная палитра ярких и различимых цветов
    private static final String[] COLOR_PALETTE = {
        "#667eea", // Фиолетовый (по умолчанию)
        "#f093fb", // Розовый
        "#4facfe", // Голубой
        "#43e97b", // Зеленый
        "#fa709a", // Розово-красный
        "#fee140", // Желтый
        "#30cfd0", // Бирюзовый
        "#a8edea", // Светло-бирюзовый
        "#ff9a9e", // Коралловый
        "#fecfef", // Светло-розовый
        "#fad0c4", // Персиковый
        "#ffd1ff", // Светло-фиолетовый
        "#a1c4fd", // Светло-голубой
        "#c2e9fb", // Небесно-голубой
        "#ffecd2", // Светло-оранжевый
        "#fcb69f", // Оранжевый
        "#ff8a80", // Красный
        "#b2fab4", // Светло-зеленый
        "#81c784", // Зеленый
        "#64b5f6", // Синий
        "#ba68c8", // Фиолетовый
        "#f06292", // Розовый
        "#4db6ac", // Бирюзовый
        "#ffb74d", // Оранжевый
        "#90caf9", // Светло-синий
        "#ce93d8", // Светло-фиолетовый
        "#a5d6a7", // Светло-зеленый
        "#ffcc80", // Светло-оранжевый
        "#b39ddb", // Светло-фиолетовый
        "#ef5350"  // Красный
    };
    
    /**
     * Генерирует цвет для категории на основе её имени.
     * Гарантирует, что одинаковые имена всегда получат одинаковый цвет.
     * 
     * @param categoryName имя категории
     * @return цвет в формате HEX (#RRGGBB)
     */
    public static String generateColorForCategory(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return COLOR_PALETTE[0];
        }
        
        // Используем хеш имени для выбора цвета из палитры
        int hash = categoryName.toLowerCase().trim().hashCode();
        int index = Math.abs(hash) % COLOR_PALETTE.length;
        
        return COLOR_PALETTE[index];
    }
    
    /**
     * Генерирует цвет на основе хеша строки.
     * Используется для создания более разнообразных цветов.
     * 
     * @param text текст для генерации цвета
     * @return цвет в формате HEX (#RRGGBB)
     */
    public static String generateColorFromHash(String text) {
        if (text == null || text.trim().isEmpty()) {
            return COLOR_PALETTE[0];
        }
        
        int hash = text.toLowerCase().trim().hashCode();
        
        // Генерируем RGB значения на основе хеша
        int r = (Math.abs(hash) % 200) + 55; // 55-255 для достаточной яркости
        int g = (Math.abs(hash * 31) % 200) + 55;
        int b = (Math.abs(hash * 61) % 200) + 55;
        
        return String.format("#%02x%02x%02x", r, g, b);
    }
    
    /**
     * Получает следующий цвет из палитры по индексу.
     * 
     * @param index индекс цвета
     * @return цвет в формате HEX (#RRGGBB)
     */
    public static String getColorByIndex(int index) {
        return COLOR_PALETTE[Math.abs(index) % COLOR_PALETTE.length];
    }
    
    /**
     * Гарантирует уникальные цвета для списка категорий.
     * Если несколько категорий имеют одинаковый цвет, перегенерирует цвета для них.
     * 
     * @param categoryNames список имен категорий
     * @param existingColors список существующих цветов (может быть null)
     * @return карта: имя категории -> уникальный цвет
     */
    public static java.util.Map<String, String> ensureUniqueColors(
            java.util.List<String> categoryNames, 
            java.util.List<String> existingColors) {
        
        java.util.Map<String, String> colorMap = new java.util.HashMap<>();
        java.util.Set<String> usedColors = new java.util.HashSet<>();
        
        if (existingColors != null && existingColors.size() == categoryNames.size()) {
            // Если передан список существующих цветов, используем их
            for (int i = 0; i < categoryNames.size(); i++) {
                colorMap.put(categoryNames.get(i), existingColors.get(i));
            }
        } else {
            // Генерируем цвета на основе имен
            for (String categoryName : categoryNames) {
                String color = generateColorForCategory(categoryName);
                colorMap.put(categoryName, color);
            }
        }
        
        // Группируем категории по цветам
        java.util.Map<String, java.util.List<String>> colorToCategories = new java.util.HashMap<>();
        for (String categoryName : categoryNames) {
            String color = colorMap.get(categoryName);
            colorToCategories.computeIfAbsent(color, k -> new java.util.ArrayList<>()).add(categoryName);
        }
        
        // Сначала обрабатываем уникальные цвета (используются только для одной категории)
        for (java.util.Map.Entry<String, java.util.List<String>> entry : colorToCategories.entrySet()) {
            String color = entry.getKey();
            java.util.List<String> categoriesWithSameColor = entry.getValue();
            
            if (categoriesWithSameColor.size() == 1) {
                // Цвет уникален, добавляем его в использованные
                usedColors.add(color);
            }
        }
        
        // Теперь обрабатываем дубликаты - для каждой группы с одинаковым цветом
        int paletteIndex = 0;
        for (java.util.Map.Entry<String, java.util.List<String>> entry : colorToCategories.entrySet()) {
            String color = entry.getKey();
            java.util.List<String> categoriesWithSameColor = entry.getValue();
            
            // Если цвет используется только для одной категории, пропускаем
            if (categoriesWithSameColor.size() == 1) {
                continue;
            }
            
            // Для всех категорий с этим цветом назначаем уникальные цвета
            for (int i = 0; i < categoriesWithSameColor.size(); i++) {
                String categoryName = categoriesWithSameColor.get(i);
                String newColor;
                
                if (i == 0 && !usedColors.contains(color)) {
                    // Для первой категории оставляем исходный цвет, если он еще не использован
                    newColor = color;
                    usedColors.add(newColor);
                } else {
                    // Для остальных категорий ищем новый уникальный цвет
                    // Сначала пробуем найти свободный цвет из палитры
                    newColor = null;
                    int attempts = 0;
                    while (attempts < COLOR_PALETTE.length * 2 && newColor == null) {
                        String candidateColor = COLOR_PALETTE[paletteIndex % COLOR_PALETTE.length];
                        if (!usedColors.contains(candidateColor)) {
                            newColor = candidateColor;
                            usedColors.add(candidateColor);
                        }
                        paletteIndex++;
                        attempts++;
                    }
                    
                    // Если не нашли свободный цвет в палитре, генерируем уникальный
                    if (newColor == null) {
                        // Используем хеш имени + индекс для уникальности
                        int hash = (categoryName.hashCode() * 31 + paletteIndex + i) & 0xFFFFFF;
                        // Убеждаемся, что цвет достаточно яркий
                        int r = ((hash >> 16) & 0xFF);
                        int g = ((hash >> 8) & 0xFF);
                        int b = (hash & 0xFF);
                        // Нормализуем значения для достаточной яркости (55-255)
                        r = Math.max(55, Math.min(255, r));
                        g = Math.max(55, Math.min(255, g));
                        b = Math.max(55, Math.min(255, b));
                        newColor = String.format("#%02x%02x%02x", r, g, b);
                        
                        // Проверяем, что сгенерированный цвет уникален
                        int retryCount = 0;
                        while (usedColors.contains(newColor) && retryCount < 100) {
                            paletteIndex++;
                            hash = (categoryName.hashCode() * 31 + paletteIndex + i + retryCount) & 0xFFFFFF;
                            r = ((hash >> 16) & 0xFF);
                            g = ((hash >> 8) & 0xFF);
                            b = (hash & 0xFF);
                            r = Math.max(55, Math.min(255, r));
                            g = Math.max(55, Math.min(255, g));
                            b = Math.max(55, Math.min(255, b));
                            newColor = String.format("#%02x%02x%02x", r, g, b);
                            retryCount++;
                        }
                        usedColors.add(newColor);
                    }
                }
                
                colorMap.put(categoryName, newColor);
            }
        }
        
        return colorMap;
    }
}
