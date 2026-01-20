# Быстрая загрузка на GitHub

## Ваш GitHub username: **ktotoymer**

## 🚀 Шаги для загрузки:

### 1. Создайте репозиторий на GitHub

Откройте в браузере: https://github.com/new

**Настройки:**
- Repository name: `expense-tracker` (или другое название)
- Description: (опционально) "Expense Tracker - система управления личными финансами"
- Visibility: выберите **Private** или **Public**
- ⚠️ **НЕ** ставьте галочки на:
  - ❌ Add a README file
  - ❌ Add .gitignore
  - ❌ Choose a license

Нажмите **"Create repository"**

---

### 2. Выполните команды в терминале

Откройте терминал и выполните:

```bash
cd "/Users/mikhailalexeev/Desktop/Обучение в вузе/3 курс/1 семестр /Разработка фронтенд-приложений управления телекоммуникациями/Java/kurs/expense-tracker"

# Добавляем все файлы
git add .

# Создаем коммит
git commit -m "Initial commit: Expense Tracker application"

# Добавляем удаленный репозиторий
git remote add origin https://github.com/ktotoymer/expense-tracker.git

# Переименовываем ветку в main (если нужно)
git branch -M main

# Загружаем на GitHub
git push -u origin main
```

---

### Или используйте автоматический скрипт:

```bash
cd "/Users/mikhailalexeev/Desktop/Обучение в вузе/3 курс/1 семестр /Разработка фронтенд-приложений управления телекоммуникациями/Java/kurs/expense-tracker"

chmod +x deploy-to-github.sh
./deploy-to-github.sh expense-tracker
```

---

## ✅ После успешной загрузки:

Ваш проект будет доступен по адресу:
**https://github.com/ktotoymer/expense-tracker**

---

## ⚠️ Если возникнут ошибки:

### "remote origin already exists"
```bash
git remote remove origin
git remote add origin https://github.com/ktotoymer/expense-tracker.git
```

### "Authentication failed"
Настройте git:
```bash
git config --global user.name "ktotoymer"
git config --global user.email "your-email@example.com"
```

Для HTTPS может потребоваться Personal Access Token вместо пароля.

### "Repository not found"
Убедитесь, что:
1. Репозиторий создан на GitHub
2. Название репозитория совпадает (`expense-tracker`)
3. Вы авторизованы в git
