# Публикация на GitHub

Локально уже есть коммит с **README.md**, **LICENSE**, **PRIVACY.md**.

## 1. Войти в GitHub (один раз)

В терминале:

```bash
gh auth login
```

Выберите: GitHub.com → HTTPS → Login with a web browser.

## 2. Создать репозиторий и отправить файлы

Если ваш логин GitHub **не** `golpom`, сначала откройте  
`app/src/main/java/com/tscan/scanertestov/legal/AppLegalUrls.kt`  
и замените `GITHUB_REPOSITORY` на свой URL.

Затем из папки проекта:

```bash
cd c:\golpom\scanerandroid
gh repo create scanerandroid --public --source=. --remote=origin --push
```

Или с явным владельцем:

```bash
gh repo create ВАШ_ЛОГИН/scanerandroid --public --source=. --remote=origin --push
```

## 3. Проверить ссылку политики

В браузере должна открываться страница:

`https://github.com/ВАШ_ЛОГИН/scanerandroid/blob/main/PRIVACY.md`

Та же ссылка — в Google Play Console и в приложении (**Настройки → Политика конфиденциальности**).

## 4. Полный исходный код (GPL)

Для лицензии GPL v3 со временем стоит добавить весь код приложения:

```bash
git add .
git commit -m "Add application source"
git push
```

Крупные файлы (модели `.onnx`) при необходимости вынесите в [Git LFS](https://git-lfs.com/) или релизы.
