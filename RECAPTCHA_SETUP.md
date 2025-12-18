# Настройка Google reCAPTCHA

## Шаги для получения ключей:

1. Перейдите на https://www.google.com/recaptcha/admin
2. Нажмите "+" для создания нового сайта
3. Заполните форму:
   - **Label**: Expense Tracker (или любое другое название)
   - **reCAPTCHA type**: Выберите "reCAPTCHA v2" → "I'm not a robot" Checkbox
   - **Domains**: Добавьте `localhost` (для разработки) и ваш домен (для продакшена)
4. Примите условия использования
5. Нажмите "Submit"

## Получение ключей:

После создания сайта вы получите:
- **Site Key** (публичный ключ) - используется на клиенте
- **Secret Key** (секретный ключ) - используется на сервере

## Настройка в приложении:

Откройте файл `src/main/resources/application.properties` и замените:

```properties
recaptcha.site.key=YOUR_SITE_KEY_HERE
recaptcha.secret.key=YOUR_SECRET_KEY_HERE
```

На ваши реальные ключи:

```properties
recaptcha.site.key=6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI
recaptcha.secret.key=6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe
```

**Примечание**: Ключи выше - это тестовые ключи от Google, которые всегда проходят проверку. Используйте их для разработки.

## Для продакшена:

- Используйте реальные ключи, полученные от Google
- Добавьте ваш домен в список разрешенных доменов в настройках reCAPTCHA
- Убедитесь, что Secret Key хранится в безопасности (не коммитьте в репозиторий)

## Проверка работы:

1. Запустите приложение
2. Перейдите на страницу регистрации
3. Вы должны увидеть виджет reCAPTCHA "Я не робот"
4. После прохождения проверки форма должна отправляться успешно

