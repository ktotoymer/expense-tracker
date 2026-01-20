# Expense Tracker

Система управления личными финансами с поддержкой ролей пользователей, бухгалтеров и администраторов.

## Технологии

- Java 21
- Spring Boot 3.5.7
- Spring Security
- Spring Data JPA
- MySQL
- Thymeleaf
- Maven

## Функциональность

- Управление доходами и расходами
- Категоризация транзакций
- Бюджетирование
- Финансовая аналитика и отчеты
- Интеграция с AI для финансовых рекомендаций
- Роли: пользователь, бухгалтер, администратор
- Google reCAPTCHA для защиты регистрации

## Установка

1. Клонируйте репозиторий:
```bash
git clone https://github.com/YOUR_USERNAME/expense-tracker.git
cd expense-tracker
```

2. Настройте базу данных MySQL:
   - Создайте базу данных `expense_tracker`
   - Обновите настройки подключения в `application.properties`

3. Скопируйте шаблон конфигурации:
```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

4. Заполните `application.properties` своими значениями:
   - Пароль базы данных
   - Ключи reCAPTCHA
   - API ключ OpenRouter (для AI функций)

5. Запустите приложение:
```bash
mvn spring-boot:run
```

Приложение будет доступно по адресу: http://localhost:8080

## Структура проекта

- `src/main/java/com/expensetracker/` - исходный код приложения
- `src/main/resources/templates/` - Thymeleaf шаблоны
- `src/main/resources/static/` - статические ресурсы (CSS, JS)
- `src/test/` - тесты

## Лицензия

Этот проект создан в учебных целях.
