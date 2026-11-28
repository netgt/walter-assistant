# Глубокие действия приложений и механизм автодобавления capabilities

Статус: проект (дизайн + список). Основано на:
- `docs/URI-VERIFY.md` / `URI-VERIFY-RESULTS.tsv` — 333 root URI, 52 пакета с проверенным открытием;
- `docs/APP-ACTIONS.md` — intent-filters с IQOO;
- `app/src/main/assets/capabilities.json` + `CapabilityRouter.kt` — текущая модель;
- открытая документация вендоров (ссылки ниже).

Важно: **root-схемы проверены на устройстве** (открывают приложение). Параметризованные форматы ниже взяты из открытых данных и **требуют проверки на IQOO** перед `verified=true`. irreversible-действия (платежи/переводы) — только через чекпоинт, никогда автономно.

---

## 1. Расширение модели capability

Сейчас `CapabilityTarget` хранит статический `deepLink` и используется только при `verified=true`. Для глубоких действий нужны параметры.

```kotlin
data class CapabilityTarget(
    val packageName: String = "",
    val label: String? = null,
    val deepLink: String? = null,        // статическая ссылка (как сейчас)
    val uriTemplate: String? = null,     // НОВОЕ: шаблон с {param}
    val params: List<ParamDef> = emptyList(),
    val risk: Risk = Risk.OPEN,          // OPEN | CONFIRM | IRREVERSIBLE
    val verified: Boolean = false,
    val source: String = "builtin"       // builtin | discovered | appfunctions
)

data class ParamDef(
    val name: String,        // query, sku, domain, ll, ...
    val from: String,        // query | geocode | memory | user
    val required: Boolean = true,
    val encode: Boolean = true   // URL-encode значения
)

enum class Risk { OPEN, CONFIRM, IRREVERSIBLE }
```

Правила маршрутизации:
- `OPEN` — открыть экран/поиск/карточку, без подтверждения.
- `CONFIRM` — действие с побочным эффектом (черновик письма, заказ в корзину) — карточка-подтверждение.
- `IRREVERSIBLE` — перевод/оплата — только человеческий чекпоинт (биометрия/подтверждение), агент не завершает сам.
- `uriTemplate` заполняется параметрами из запроса/геокодера/памяти; если обязательный параметр не получен — роутер задаёт уточняющий вопрос или делает фоллбэк на `deepLink`/главный экран.
- `verified=false` → ссылка не используется для автозапуска, только предлагается/тестируется.

Пример JSON:
```json
{
  "id": "maps.search",
  "aliases": ["найди на карте", "покажи на карте", "где находится"],
  "targets": [{
    "package": "ru.yandex.yandexmaps",
    "uriTemplate": "yandexmaps://maps.yandex.ru/?text={query}",
    "params": [{"name": "query", "from": "query"}],
    "risk": "OPEN",
    "verified": false,
    "source": "builtin"
  }]
}
```

---

## 2. Список глубоких действий

Колонка «Статус»: `root OK` — схема проверена; `формат док.` — формат из открытой документации, нужна проверка на устройстве.

### 2.1 Карты и навигация

| Действие | URI-шаблон | Пакет | Риск | Статус / источник |
|---|---|---|---|---|
| Поиск места | `yandexmaps://maps.yandex.ru/?text={query}` | ru.yandex.yandexmaps | OPEN | формат док. [Яндекс](https://www.yandex.ru/dev/yandex-apps-launch-maps/doc/ru) |
| Точка по координатам | `yandexmaps://maps.yandex.ru/?ll={lon},{lat}&z={zoom}` | ru.yandex.yandexmaps | OPEN | формат док. |
| Метка | `yandexmaps://maps.yandex.ru/?pt={lon},{lat}` | ru.yandex.yandexmaps | OPEN | формат док. |
| Маршрут | `yandexmaps://maps.yandex.ru/?rtext={lat1},{lon1}~{lat2},{lon2}&rtt=auto` | ru.yandex.yandexmaps | OPEN | формат док. |
| Навигация Google | `google.navigation://?q={lat},{lon}&mode=d` | com.google.android.apps.maps | OPEN | root OK |
| Точка geo | `geo:{lat},{lon}?q={query}` | карты | OPEN | root OK |
| Заказ такси (маршрут) | `https://taxi.yandex.ru/...?gfrom={lon1},{lat1}&gto={lon2},{lat2}&tariff=...` | ru.yandex.taxi | CONFIRM | формат док. [Яндекс Go](https://www.yandex.ru/support/taxi-distr/ru/api/deeplinks) |

### 2.2 Telegram (через TDLib или deep link)

| Действие | URI-шаблон | Пакет | Риск | Статус |
|---|---|---|---|---|
| Открыть чат/канал | `tg://resolve?domain={username}` | org.telegram.messenger | OPEN | root OK, [Telegram](https://core.telegram.org/api/links) |
| Открыть пост | `tg://resolve?domain={username}&post={messageId}` | org.telegram.messenger | OPEN | формат док. |
| Черновик сообщения | `tg://resolve?domain={username}&text={text}` | org.telegram.messenger | CONFIRM | формат док. |
| Присоединиться | `tg://join?invite={hash}` | org.telegram.messenger | CONFIRM | формат док. |

Отправка от имени пользователя — уже через TDLib (`tg_send`/`tg_send_file`), не через deep link.

### 2.3 Магазины и товары

| Действие | URI-шаблон | Пакет | Риск | Статус |
|---|---|---|---|---|
| Карточка товара OZON | `https://www.ozon.ru/product/{sku}` | ru.ozon.app.android | OPEN | формат док. [OZON](https://dev.ozon.ru/community/937-Poluchenie-ssylki-na-tovar) |
| Поиск OZON | `https://www.ozon.ru/search/?text={query}` | ru.ozon.app.android | OPEN | нужна проверка |
| Открыть OZON | `ozon://` | ru.ozon.app.android | OPEN | root OK |
| Поиск Avito | `https://www.avito.ru/all?q={query}` | com.avito.android | OPEN | нужна проверка (root FAIL, app links) |
| Товар Avito | `https://www.avito.ru/{path}/{id}` | com.avito.android | OPEN | нужна проверка |
| Каталог VkusVill | `vkusvill://` | ru.vkusvill | OPEN | root OK; глубокие — нужна проверка |
| Страница приложения | `market://details?id={package}` | com.android.vending | OPEN | формат док. [Android](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/docs/html/guide/publishing/publishing.jd) |
| Поиск в Play | `market://search?q={query}` | com.android.vending | OPEN | формат док. |
| RuStore | `rustore://` | ru.vk.store | OPEN | root OK |

### 2.4 Почта и сообщения

| Действие | URI-шаблон | Пакет | Риск | Статус |
|---|---|---|---|---|
| Новое письмо | `mailto:{to}?subject={subject}&body={body}` | com.google.android.gm / ru.yandex.mail | CONFIRM | root OK |
| SMS | `smsto:{number}?body={text}` | com.android.mms | CONFIRM | root OK |
| Звонок (набор) | `tel:{number}` | com.android.contacts | CONFIRM | root OK |

Полная отправка почты — уже через SMTP (`mail_send`) с подтверждением.

### 2.5 Медиа

| Действие | URI-шаблон | Пакет | Риск | Статус |
|---|---|---|---|---|
| Видео YouTube | `vnd.youtube:{videoId}` | com.google.android.youtube | OPEN | формат док. |
| Поиск YouTube | `https://www.youtube.com/results?search_query={query}` | com.google.android.youtube | OPEN | формат док. |
| Трек/альбом Яндекс Музыки | `https://music.yandex.ru/album/{albumId}/track/{trackId}` | ru.yandex.music | OPEN | app links, нужна проверка |
| Поиск Яндекс Музыки | `https://music.yandex.ru/search?text={query}` | ru.yandex.music | OPEN | нужна проверка |
| Открыть Яндекс Музыку | `yandexmusic://` | ru.yandex.music | OPEN | root OK |

### 2.6 Браузер и поиск

| Действие | URI-шаблон | Пакет | Риск | Статус |
|---|---|---|---|---|
| Открыть URL | `https://{url}` / `googlechrome://{url}` | com.android.chrome | OPEN | root OK |
| Поиск в браузере | `https://yandex.ru/search/?text={query}` | com.yandex.browser | OPEN | app links |
| Яндекс Диск (папка) | `yandexdisk://` | ru.yandex.disk | OPEN | root OK; путь — нужна проверка |

### 2.7 Финансы (только чекпоинт)

| Действие | URI-шаблон | Пакет | Риск | Статус |
|---|---|---|---|---|
| Экран перевода Сбер | `sberbankonline://` / `perevod://` | ru.sberbankmobile | IRREVERSIBLE | root OK; параметры не публичны |
| Т-Банк | `tbank://` / `tinkoffbank://` | com.idamob.tinkoff.android | IRREVERSIBLE | root OK |
| Альфа-Банк | `alfabank://` | ru.alfabank.mobile.android | IRREVERSIBLE | root FAIL → только главный экран |
| Оплата SberPay | app2app/web2app (merchant) | ru.sberbankmobile | IRREVERSIBLE | только по инициативе продавца, не агентом |

Агент открывает экран и останавливается; завершение — только человеком. Автономные переводы/платежи исключены.

### 2.8 Умный дом и прочее (root OK, глубокие — нужна проверка)

`mihome://` (Xiaomi), `kojimahome://`, `hikconnect://`, `warmme://`, `openvpn-connect://` (импорт профиля `openvpn://import-profile/...`), `vpn://` (Amnezia), `gosuslugi://`, `otpauth://` (Authenticator). Глубокие сценарии (включить устройство, подключить VPN) требуют отдельной проверки и, как правило, CONFIRM.

---

## 3. Механизм автодобавления нового приложения

Цель: при установке нового приложения автоматически добавить его возможные действия в capabilities (как кандидаты, с последующей верификацией).

### 3.1 Источники сигналов

1. **`BroadcastReceiver`** на `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REPLACED` (с `dataScheme="package"`).
2. **Полный скан** при первом запуске и по команде «обнови приложения» (перебор `getInstalledPackages`).
3. **AppFunctions API** (Android 16+) — санкционированный источник функций приложения, когда доступен.

### 3.2 Извлечение действий (PackageManager)

```kotlin
val pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES or PackageManager.GET_RESOLVED_FILTER)
for (activity in pi.activities) {
    val filter = activity.filter ?: continue
    if (!filter.hasAction(Intent.ACTION_VIEW)) continue
    if (!filter.hasCategory(Intent.CATEGORY_BROWSABLE)) continue
    for (scheme in filter.schemes()) {
        for (host in filter.hosts()) {
            for (path in filter.paths()) {   // PatternMatcher
                // candidate: scheme://host/path
            }
        }
    }
}
```

Это то же, что дал `dumpsys package` для `APP-ACTIONS.md`, но программно и инкрементально.

### 3.3 Классификация и шаблоны

1. **Отсев callback-схем** — переиспользуем классификацию из `tools/verify_uris.py` (`auth`, `recaptcha`, `spaysdk`, `bank100…`, `fbconnect`, `esia`, `growing.*`, `passport`, `login`…). Они не становятся действиями.
2. **Библиотека шаблонов** `deeplink-templates.json`: известные форматы, привязанные к scheme/host (раздел 2). Если извлечённый scheme/host совпадает с шаблоном → генерируется `CapabilityDef` с `uriTemplate`/`params`/`risk`.
3. **Неизвестная схема** → создаётся generic-capability «открыть приложение» (`MainApp`) + запись схемы в журнал для будущего добавления шаблона. Никаких выдуманных параметров.

### 3.4 Хранение и слияние

- Встроенный каталог: `assets/capabilities.json` (как сейчас).
- Обнаруженный каталог: `files/discovered-capabilities.json` (или prefs), `source="discovered"`, `verified=false`.
- `CapabilityStore.loadCatalog` = builtin + discovered + пользовательские правила; discovered не перетирает builtin.

### 3.5 Верификация (очередь)

- Каждый discovered-кандидат попадает в очередь проверки.
- Автопроверка на устройстве: `am start -a VIEW -d <uri> -p <pkg>` + фактический top-task (как `tools/verify_uris.py`). Успех → `verified=true`; провал → `verified=false`, остаётся только `MainApp`.
- Параметризованные шаблоны проверяются с тестовыми значениями; irreversible — не автозапускаются, только помечаются.
- Пользователь видит новые действия в настройках и может подтвердить/удалить.

### 3.6 Безопасность

- Discovered-действия по умолчанию `verified=false` и не запускаются автоматически.
- `risk=IRREVERSIBLE` никогда не завершается агентом.
- Команды/содержимое из приложений — недоверенные данные (не инструкции).
- Никаких accessibility/FLAG_SECURE-обходов — только `ACTION_VIEW`/AppFunctions.

---

## 4. План внедрения

1. Расширить модель (`uriTemplate`/`params`/`risk`/`source`) + парсер + тесты.
2. Заполнить `uriTemplate` для проверенных форматов раздела 2; верифицировать на IQOO по одному.
3. `deeplink-templates.json` + классификатор callback.
4. `PackageDiscovery` (receiver + скан + извлечение filters) + discovered-каталог + слияние.
5. Очередь верификации на устройстве + UI новых действий в настройках.
6. AppFunctions-адаптер (по мере доступности API).
