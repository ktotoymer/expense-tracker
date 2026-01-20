#!/bin/bash

# Скрипт для загрузки проекта на GitHub
# Использование: ./deploy-to-github.sh [REPO_NAME]
# По умолчанию использует username: ktotoymer

GITHUB_USERNAME="ktotoymer"
REPO_NAME=${1:-"expense-tracker"}

echo "🚀 Начинаем загрузку проекта на GitHub..."

# Проверяем, что мы в git репозитории
if [ ! -d ".git" ]; then
    echo "❌ Ошибка: это не git репозиторий. Инициализируйте git сначала."
    exit 1
fi

# Проверяем статус
echo "📋 Проверяем статус репозитория..."
git status

# Добавляем все файлы
echo "➕ Добавляем файлы в staging..."
git add .

# Проверяем, есть ли изменения для коммита
if git diff --staged --quiet; then
    echo "ℹ️  Нет изменений для коммита."
else
    # Создаем коммит
    echo "💾 Создаем коммит..."
    git commit -m "Initial commit: Expense Tracker application with security improvements"
fi

# Переименовываем ветку в main (если нужно)
echo "🌿 Настраиваем ветку main..."
git branch -M main 2>/dev/null || echo "Ветка уже main"

# Проверяем, есть ли уже remote
if git remote get-url origin >/dev/null 2>&1; then
    echo "⚠️  Удаленный репозиторий уже настроен:"
    git remote -v
    read -p "Хотите обновить URL? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git remote set-url origin "https://github.com/$GITHUB_USERNAME/$REPO_NAME.git"
    fi
else
    # Добавляем remote
    echo "🔗 Добавляем удаленный репозиторий..."
    git remote add origin "https://github.com/$GITHUB_USERNAME/$REPO_NAME.git"
fi

echo ""
echo "✅ Готово к загрузке!"
echo ""
echo "📝 Следующие шаги:"
echo "1. Создайте репозиторий на GitHub: https://github.com/new"
echo "   - Название: $REPO_NAME"
echo "   - НЕ добавляйте README, .gitignore или лицензию"
echo ""
echo "2. После создания репозитория выполните:"
echo "   git push -u origin main"
echo ""
echo "Или выполните команду сейчас (если репозиторий уже создан):"
read -p "Загрузить код сейчас? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "📤 Загружаем код на GitHub..."
    git push -u origin main
    if [ $? -eq 0 ]; then
        echo "✅ Проект успешно загружен на GitHub!"
        echo "🔗 URL: https://github.com/$GITHUB_USERNAME/$REPO_NAME"
    else
        echo "❌ Ошибка при загрузке. Убедитесь, что:"
        echo "   - Репозиторий создан на GitHub"
        echo "   - У вас есть права на запись"
        echo "   - Вы авторизованы в git (git config --global user.name и user.email)"
    fi
fi
