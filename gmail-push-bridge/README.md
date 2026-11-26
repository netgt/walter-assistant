# Gmail Push Bridge для Walter

Небольшой Cloud Run/FastAPI bridge для настоящего Gmail push:

```
Gmail users.watch → Google Pub/Sub → этот bridge → Walter /gmail/events
```

Bridge хранит только `emailAddress` и последние `historyId` (до 100). Письма и OAuth refresh token **на него не передаются**. Walter сам скачивает письма через Gmail API.

## Endpoints

- `GET /healthz` — проверка.
- `POST /gmail/push` — приём Pub/Sub push. Заголовок `X-Bridge-Token` обязателен, если задан `BRIDGE_TOKEN`.
- `GET /gmail/events?email=<address>&after=<historyId>` — новые historyId после указанного. Тот же секрет.

## 1. Google Cloud

Включите **Gmail API**, **Cloud Pub/Sub**, **Cloud Run**.

Создайте topic, например `gmail-watch`, и выдайте `gmail-push@system.gserviceaccount.com` право **Pub/Sub Publisher** на него.

Полное имя topic для настроек Walter:

```
projects/<PROJECT_ID>/topics/gmail-watch
```

## 2. Deploy Cloud Run

```bash
cd gmail-push-bridge
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt

export BRIDGE_TOKEN='замените-на-длинный-случайный-секрет'
export STATE_FILE=/tmp/gmail-bridge.json

gcloud run deploy walter-gmail-bridge \
  --source . \
  --region=europe-west1 \
  --allow-unauthenticated \
  --set-env-vars BRIDGE_TOKEN="$BRIDGE_TOKEN",STATE_FILE="$STATE_FILE"
```

`--allow-unauthenticated` здесь означает доступ по HTTPS с секретом `X-Bridge-Token`. Для production рекомендуется дополнительно включить Pub/Sub OIDC.

Состояние `/tmp` эфемерно. Для постоянного хранения подключите Volume/Firestore и задайте `STATE_FILE` на постоянном томе.

## 3. Pub/Sub push subscription

Endpoint: `https://<CLOUD_RUN_URL>/gmail/push`.

В настройках push subscription добавьте custom header:

```
X-Bridge-Token: <тот же секрет>
```

## 4. Walter

В настройках Gmail укажите:

- OAuth Client ID типа **Android**;
- Pub/Sub topic полным именем;
- URL Cloud Run;
- секрет bridge.

Затем вызовите в чате `gmail_push` с `query="watch"` или попросите: «зарегистрируй Gmail push». Walter вызовет `users.watch` и сохранит `historyId`.

После этого `gmail_push` читает новые historyId через bridge и получает сами письма через `users.history` + `messages.get`.

## Локальный запуск

```bash
export BRIDGE_TOKEN='dev-secret'
uvicorn main:app --host 0.0.0.0 --port 8080
```

Проверка:

```bash
curl -H 'X-Bridge-Token: dev-secret' \
  'http://127.0.0.1:8080/gmail/events?email=test@example.com&after=0'
```

Pub/Sub payload можно проверить так:

```bash
DATA=$(printf '{"emailAddress":"test@example.com","historyId":12345}' | base64 -w0)
curl -X POST -H "X-Bridge-Token: dev-secret" -H 'Content-Type: application/json' \
  -d "{\"message\":{\"data\":\"$DATA\"}}" http://127.0.0.1:8080/gmail/push
```
