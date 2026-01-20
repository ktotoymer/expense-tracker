# Исправление ошибки HTTP 400 при push

## Проблема
Ошибка `RPC failed; HTTP 400` при попытке загрузить код на GitHub.

## Решение

### 1. Увеличьте буфер git и размер пакета

Выполните в терминале:

```bash
git config --global http.postBuffer 524288000
git config --global http.maxRequestBuffer 100M
git config --global core.compression 0
```

### 2. Удалите большие файлы из индекса (если они там есть)

```bash
# Удаляем CSV файлы из git (они теперь в .gitignore)
git rm --cached data.csv 2>/dev/null
git rm --cached public/data.csv 2>/dev/null
git rm --cached "*.csv" 2>/dev/null

# Создаем коммит с удалением
git commit -m "Remove large CSV files from repository"
```

### 3. Попробуйте загрузить снова

```bash
# Увеличиваем буфер для этого push
git config http.postBuffer 524288000

# Пробуем загрузить
git push -u origin main
```

### 4. Альтернатива: загрузка по частям

Если файлы все еще слишком большие, можно загрузить по частям:

```bash
# Загружаем только последний коммит
git push -u origin main --verbose
```

### 5. Если не помогает - используйте SSH

```bash
# Измените remote на SSH
git remote set-url origin git@github.com:ktotoymer/expense-tracker.git

# Попробуйте снова
git push -u origin main
```

### 6. Проверьте размер репозитория

```bash
# Проверьте размер файлов
du -sh .git
du -sh *

# Найдите большие файлы
find . -type f -size +1M -not -path "./.git/*" -not -path "./target/*"
```

## Если ничего не помогает

Можно попробовать создать новый коммит с меньшим количеством файлов:

```bash
# Сбросите последний коммит (но сохраните изменения)
git reset --soft HEAD~1

# Добавьте файлы по частям
git add src/
git add pom.xml
git add README.md
git add .gitignore
git commit -m "Initial commit: core application files"

# Добавьте остальные файлы отдельным коммитом
git add .
git commit -m "Add additional files and documentation"

# Загрузите
git push -u origin main
```
