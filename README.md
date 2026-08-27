# Демо уведомлений

Сервис уведомлений шлёт REST POST на бэкенд. Бэкенд сразу пушит сообщения в Android-приложение по WebSocket. Телефон и отправитель могут быть в разных сетях.

```text
Сервис уведомлений  --POST-->  Render (бэкенд)  --WebSocket-->  приложение на телефоне
```

На экране появляется карточка, в шторке — системное уведомление. Пока приложение подключено, соединение держит фоновый сервис (можно свернуть приложение).

## 1. Бэкенд на Render

Нужен аккаунт на [dashboard.render.com](https://dashboard.render.com/). Репозиторий должен быть на GitHub (Render забирает код оттуда).

**Важно:** не используйте Free Instance. Он засыпает через ~15 минут, WebSocket обрывается, и уведомление «само» уже не придёт. Для демо возьмите **Starter**.

### Через Blueprint

1. Запушьте этот проект на GitHub.
2. В Render: **New → Blueprint**.
3. Укажите репозиторий — подхватится `render.yaml`.
4. После деплоя скопируйте URL вида `https://demo-notifications-xxxx.onrender.com`.

### Через Dashboard вручную

1. **New → Web Service**, подключите GitHub-репозиторий.
2. Root Directory: `backend`
3. Runtime: Node
4. Build Command: `npm install`
5. Start Command: `npm start`
6. Health Check Path: `/health`
7. Instance: **Starter**

Проверка:

```bat
curl https://ВАШ-СЕРВИС.onrender.com/health
```

Ожидается `{"status":"ok", ...}`.

Локально, без Render:

```bat
cd backend
npm install
npm start
```

Сервер слушает `http://localhost:10000`. С эмулятора Android адрес: `http://10.0.2.2:10000`.

## 2. Сборка APK

Без Android Studio (Docker). Первый запуск качает Gradle и зависимости и занимает несколько минут.

**PowerShell:**

```powershell
docker run --rm -v "${PWD}:/project" -w /project cimg/android:2024.11.1 bash -lc "bash ./gradlew assembleDebug"
```

**Git Bash:** путь `/project` иначе превращается в `...\Git\project`, поэтому:

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v "${PWD}:/project" -w /project cimg/android:2024.11.1 bash -lc "bash ./gradlew assembleDebug"
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Либо откройте папку в [Android Studio](https://developer.android.com/studio): **Build → Build APK(s)**.

При первом запуске разрешите уведомления.

## 3. Как проводить демо

1. В приложении вставьте URL бэкенда, например `https://demo-notifications-xxxx.onrender.com`.
2. `customerId` можно оставить пустым (тогда приходят все сообщения) или указать конкретного клиента.
3. Нажмите **Подключиться** — индикатор станет зелёным.
4. Сервис уведомлений (или Postman) шлёт POST на адрес с карточки.

```bat
curl -X POST https://ВАШ-СЕРВИС.onrender.com/notifications -H "Content-Type: application/json; charset=utf-8" --data-binary @sample-payload.json
```

Ответ: `{"accepted":2,"clients":1}`. На телефоне сразу две карточки и два уведомления в шторке.

## API бэкенда

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/health` | Проверка, что сервис жив |
| `POST` | `/notifications` | Приём одного объекта или массива — сразу рассылается клиентам |
| `GET` | `/notifications` | Последние сообщения (для отладки) |
| `WS` | `/ws` | Подписка приложения. `?customerId=9305` — только этот клиент |

Тело POST без изменений, как присылает сервис уведомлений:

```json
[
  {
    "notificationType": "INCOMING_INTERBANK_TRANSFER",
    "customerId": 9305,
    "id": "111",
    "noteRu": "Ваш счет пополнен на 552030.00 KGS",
    "noteKg": "Сиздин эсебиңиз 552030.00 KGS толукталды",
    "noteEn": "Your account has been credited with 552030.00 KGS",
    "createdAt": "2026-08-17T11:37:00+06:00"
  }
]
```

История в памяти процесса, последние 200 сообщений. После рестарта инстанса Render список пустой — для демо этого достаточно.
