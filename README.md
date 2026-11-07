# Vorb

**Vorb** — это генератор WireGuard-конфигов для Cloudflare WARP, одностраничный сайт и Android-клиент на Kotlin + Jetpack Compose + Material 3.

- Сайт: [kirilltitov777z-dotcom.github.io/vorb-project](https://kirilltitov777z-dotcom.github.io/vorb-project/)
- Telegram: [t.me/VorbVpn](https://t.me/VorbVpn)
- Репозиторий: [github.com/kirilltitov777z-dotcom/vorb-project](https://github.com/kirilltitov777z-dotcom/vorb-project)

> Vorb не связан с Cloudflare. WARP — сервис Cloudflare. Конфиг содержит приватный ключ — не публикуйте его.

## Что внутри

| Часть | Технологии | Где лежит |
| --- | --- | --- |
| Backend | Node.js, Express, wgcf | `server.js`, `package.json` |
| Сайт | HTML, CSS, Vanilla JS, Material You | `index.html` |
| Android | Kotlin, Jetpack Compose, Material 3, WireGuard tunnel | `android/` |
| CI/CD | GitHub Actions | `.github/workflows/` |

## Backend

### Требования

- Node.js 18+
- [wgcf](https://github.com/ViRb3/wgcf) в `PATH` или путь в переменной `WGCF_PATH`

Установка wgcf (любой из способов):

```bash
# Go
go install github.com/ViRb3/wgcf@latest

# или готовый бинарник
# https://github.com/ViRb3/wgcf/releases
```

### Запуск

```bash
npm install
npm start
# http://localhost:3000
```

Переменные окружения:

| Переменная | По умолчанию | Назначение |
| --- | --- | --- |
| `PORT` | `3000` | Порт сервера |
| `WGCF_PATH` | `wgcf` | Путь к бинарнику wgcf |
| `WGCF_TIMEOUT_MS` | `35000` | Таймаут одной операции wgcf |
| `CORS_ORIGIN` | `*` | Origin для CORS (например, `https://kirilltitov777z-dotcom.github.io`) |

### API

```http
POST /api/generate
Content-Type: application/json

{}
```

Ответ:

```json
{
  "filename": "vorb-warp.conf",
  "config": "[Interface]\nPrivateKey = ...\n..."
}
```

Ограничение: **1 запрос в минуту с одного IP**. При превышении — `429`.

`GET /api/health` возвращает состояние сервиса.

Каждый запрос создаёт свежую временную папку, регистрирует новый WARP-профиль, генерирует конфиг и удаляет временные файлы. Профили не сохраняются на сервере.

## Сайт и GitHub Pages

Сайт — один файл `index.html`. Workflow `.github/workflows/deploy-pages.yml` копирует его в артефакт и публикует на GitHub Pages.

GitHub Pages — статический хостинг, поэтому для кнопки «Сгенерировать» нужен запущенный backend. Укажите его адрес одним из способов:

1. Нажмите кнопку **API** в генераторе и сохраните URL (хранится в `localStorage`).
2. Откройте сайт с параметром `?api=https://your-backend.example.com`.
3. Задайте `window.VORB_API_BASE = 'https://your-backend.example.com'` перед подключением `index.html`.

При локальном запуске через `npm start` backend и сайт работают на одном адресе — настройка не нужна.

## Android

### Сборка локально

```bash
cd android
./gradlew assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

Требования: JDK 17 и Android SDK с платформой 35. Gradle wrapper уже в репозитории.

### Экраны

- **Главная** — подключение/отключение WireGuard, статистика трафика, статус конфига.
- **Импорт** — вставка текста конфига или выбор `.conf` файла, проверка формата.
- **Настройки** — светлая/тёмная/системная тема, динамические цвета Material You, удаление конфига, ссылки.

VPN-разрешение запрашивается системным диалогом Android. Используется официальная библиотека `com.wireguard.android:tunnel`.

### Готовый APK из GitHub Actions

1. Откройте репозиторий → вкладка **Actions**.
2. Слева выберите **Build Android APK**.
3. Откройте последний успешный запуск (зелёная галочка).
4. Внизу страницы, в блоке **Artifacts**, скачайте **vorb-debug-apk** (zip-архив).
5. Распакуйте архив — внутри `app-debug.apk`.
6. Разрешите установку из неизвестных источников и установите APK на телефон.

Артефакт хранится 30 дней. Сборка запускается при пуше в `main`, при изменениях в `android/` и вручную через **Run workflow**.

## CI/CD

- `.github/workflows/deploy-pages.yml` — публикует `index.html` на GitHub Pages при пуше в `main`.
- `.github/workflows/build-android.yml` — собирает debug APK и загружает его в Artifacts.

Для Pages в настройках репозитория должен быть выбран источник **GitHub Actions** (это уже настроено через API).

## Безопасность

- Не коммитьте `.env`, `local.properties`, ключи и файлы `*.conf`.
- Конфиг WARP содержит приватный ключ. Не отправляйте его в чаты и не публикуйте.
- Rate limit снижает нагрузку на сервер и злоупотребление генерацией.
- Для продакшена задайте `CORS_ORIGIN` конкретным доменом и запускайте backend за reverse proxy с HTTPS.

## Лицензия

MIT. См. `LICENSE`.
