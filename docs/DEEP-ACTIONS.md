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

---

## 5. Полное покрытие всех 103 приложений

Ниже — все launcher-пакеты из `APP-ACTIONS.md`. «Глубокие» — параметризованные действия из открытых данных (нужна проверка на устройстве). «Root» — только открытие приложения/главного экрана, публичных глубоких ссылок нет. irreversible — только чекпоинт.

### 5.1 Соцсети, мессенджеры, видео

| Приложение | Пакет | Глубокие действия | Риск |
|---|---|---|---|
| Telegram | org.telegram.messenger | `tg://resolve?domain={u}&post={id}&text={t}`, `tg://join?invite={h}` (отправка — TDLib) | OPEN/CONFIRM |
| MAX | ru.oneme.app | `https://max.ru/{name}`, бот `https://max.ru/{bot}?start={payload}` ([MAX](https://dev.max.ru/help/deeplinks)) | OPEN/CONFIRM |
| Tumblr | com.tumblr | `tumblr://x-callback-url/blog?blogName={b}&postID={id}`, `…/tag?tag={t}` ([Tumblr](https://github.com/TumblrArchive/TumblrLaunchSample)) | OPEN |
| VK Видео | com.vk.vkvideo | `https://vk.com/video-{oid}_{id}` (app links) | OPEN |
| Loop | ru.loop.app | root | OPEN |
| Meshtastic | com.geeksville.mesh | `https://meshtastic.org/e/#{base64ChannelSet}` (шаринг канала) ([wiki](https://wiki.meshamerica.com/books/meshtastic/page/sharing-channels-via-qr-code)) | CONFIRM |
| YouTube | com.google.android.youtube | `vnd.youtube:{id}`, `https://www.youtube.com/results?search_query={q}` | OPEN |
| Яндекс Музыка | ru.yandex.music | `https://music.yandex.ru/album/{a}/track/{t}`, `…/search?text={q}` | OPEN |
| Music (vivo) | com.android.bbkmusic | root (`vivomusic://`) | OPEN |
| Video (vivo) | com.android.VideoPlayer | `rtsp://{url}` (поток) | OPEN |

### 5.2 Карты, навигация, такси

| Приложение | Пакет | Глубокие действия | Риск |
|---|---|---|---|
| Яндекс Карты | ru.yandex.yandexmaps | `?text={q}`, `?ll={lon},{lat}&z={z}`, `?pt=`, `?rtext=…&rtt=auto` ([Яндекс](https://www.yandex.ru/dev/yandex-apps-launch-maps/doc/ru)) | OPEN |
| Карты Google | com.google.android.apps.maps | `google.navigation://?q={lat},{lon}&mode=d`, `geo:` | OPEN |
| Яндекс Go | ru.yandex.taxi | `https://taxi.yandex.ru/…?gfrom=…&gto=…&tariff=…` ([Яндекс Go](https://www.yandex.ru/support/taxi-distr/ru/api/deeplinks)) | CONFIRM |
| Браузер (vivo) | com.vivo.browser | `geo.action://`, открытие URL | OPEN |

### 5.3 Магазины и товары

| Приложение | Пакет | Глубокие действия | Риск |
|---|---|---|---|
| OZON | ru.ozon.app.android | `https://www.ozon.ru/product/{sku}`, `…/search/?text={q}` ([OZON](https://dev.ozon.ru/community/937-Poluchenie-ssylki-na-tovar)) | OPEN |
| Авито | com.avito.android | `https://www.avito.ru/all?q={q}`, `https://www.avito.ru/{path}/{id}` (root FAIL, app links) | OPEN |
| ВкусВилл | ru.vkusvill | root (`vkusvill://`); глубокие — нужна проверка | OPEN |
| Google Play | com.android.vending | `market://details?id={pkg}`, `market://search?q={q}` ([Android](https://developer.android.com/guide/play-core)) | OPEN |
| RuStore | ru.vk.store | `rustore://`, `market://details?id={pkg}` | OPEN |
| AppGallery | com.huawei.appmarket | `hiapplink://com.huawei.appmarket?appId=C{id}&detailType=0&callType=AGDLINK`, `store://appgallery.huawei.com/app/detail?id={pkg}` ([Huawei](https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-Guides/agdlink-getlink-manual-0000001117762114)) | OPEN |
| V-Appstore | com.vivo.appstore | `market://details?id={pkg}` | OPEN |
| iQOO Store | com.iqoo.website | root (`website://`) | OPEN |

### 5.4 Почта, сообщения, звонки

| Приложение | Пакет | Глубокие действия | Риск |
|---|---|---|---|
| Gmail | com.google.android.gm | `mailto:{to}?subject={s}&body={b}` (отправка — SMTP) | CONFIRM |
| Яндекс Почта | ru.yandex.mail | `mailto:` (отправка — SMTP) | CONFIRM |
| Сообщения | com.android.mms | `smsto:{n}?body={t}` | CONFIRM |
| Phone/Contacts | com.android.contacts | `tel:{n}` | CONFIRM |
| Meet | com.google.android.apps.tachyon | `mailto:`, `tel:` | CONFIRM |
| EasyShare | com.vivo.easyshare | `smsto:` | CONFIRM |

### 5.5 Финансы (только чекпоинт, автономно нельзя)

| Приложение | Пакет | Глубокие действия | Риск |
|---|---|---|---|
| СберБанк | ru.sberbankmobile | `sberbankonline://`, `perevod://` (экран); SberPay app2app — только от продавца | IRREVERSIBLE |
| Т-Банк | com.idamob.tinkoff.android | `tbank://`, `tinkoffbank://` (root) | IRREVERSIBLE |
| Альфа-Банк | ru.alfabank.mobile.android | root FAIL → главный экран | IRREVERSIBLE |
| Ozon Банк | ru.ozon.fintech.finance | `ozonbank://`, `tel:` | IRREVERSIBLE |
| РСХБ | ru.rshb.dbo | root (`rshbmbfl://`) | IRREVERSIBLE |
| МТС Деньги | ru.lewis.dbo | root (`mtsmoney://`) | IRREVERSIBLE |
| Цифра банк | com.bankffin.portfolio | root (`cifra://`) | IRREVERSIBLE |
| Freedom Banker | kz.bankffin.app | root (`ffbankapp://`) | IRREVERSIBLE |
| Altyn Wallet | com.altyn.wallet | root (`altynwallet://`) | IRREVERSIBLE |
| INSNC | by.alfabank.insync3 | root (`insnc://`) | IRREVERSIBLE |
| KWIKPAY | com.kwikpay.transfers | root (`kwikpay://`) | IRREVERSIBLE |
| Mir Pay | ru.nspk.mirpay | root (`mirpay://`); оплата NFC — не агентом | IRREVERSIBLE |

### 5.6 Умный дом и камеры (root; управление устройством — не публичный deep link)

| Приложение | Пакет | Глубокие действия | Риск |
|---|---|---|---|
| Xiaomi Home | com.xiaomi.smarthome | root (`mihome://`); сцены — через облачный API с подписью, не deep link | CONFIRM |
| Tapo | com.tplink.iot | root (`tapo://`); управление — облако/голос | CONFIRM |
| KOJIMA HOME | com.kojima.home | root (`kojimahome://`) | CONFIRM |
| Warmme | com.thermtouch.smart | root (`warmme://`) | CONFIRM |
| Hommyn | com.hommyn.app | root (`climaton://`); recaptcha — callback | CONFIRM |
| iFEEL Safe+Smart | com.ifeelsystems.safesmart | root (`ifeelsystemssafesmart://`) | CONFIRM |
| CloudEdge | com.cloudedge.smarteye | root (`meari_cloudedge://`) | CONFIRM |
| Hik-Connect | com.connect.enduser | root (`hikconnect://`); growing.* — callback | CONFIRM |
| Smart Remote | com.vivo.vhome | root (`vhome://`) | CONFIRM |

### 5.7 VPN и сеть

| Приложение | Пакет | Глубокие действия | Риск |
|---|---|---|---|
| OpenVPN Connect | net.openvpn.openvpn | `openvpn://import-profile/{url}`, token-URL импорт профиля ([OpenVPN](https://openvpn.net/as-docs/v3/token-url/)) | CONFIRM |
| AmneziaVPN | org.amnezia.vpn | root (`vpn://`); импорт конфига | CONFIRM |
| AdGuard Mini | com.adguard.android.contentblocker | `sdns://{base64stamp}` (импорт DNS-сервера) | CONFIRM |
| Zapret KVN | io.github.zapretkvn.android | root | OPEN |

### 5.8 Инструменты, безопасность, документы

| Приложение | Пакет | Глубокие действия | Риск |
|---|---|---|---|
| Authenticator | com.google.android.apps.authenticator2 | `otpauth://totp/{issuer}:{account}?secret={base32}&issuer={issuer}` (импорт ключа) | CONFIRM |
| Яндекс ID (Ключ) | ru.yandex.key | `otpauth://` (импорт); root | CONFIRM |
| Пароли (Kaspersky) | com.kaspersky.passwordmanager | root (`kpm://`); публичных глубоких нет | OPEN |
| Документы/Таблицы/Презентации | com.google.android.apps.docs.editors.* | открытие файла через `content://`/SAF (не deep link) | OPEN |
| Files | com.google.android.apps.nbu.files | root (`fbg-app://`); открытие файлов SAF | OPEN |
| Фото | com.google.android.apps.photos | открытие изображения через SAF | OPEN |
| Albums (vivo) | com.vivo.gallery | `vivogallery://`, `photoeditor://` (открыть/редактор) | OPEN |
| Snapseed | com.niksoftware.snapseed | открытие изображения через SAF/share | OPEN |
| Календарь Google | com.google.android.calendar | `content://com.android.calendar/events` (чтение/создание) | CONFIRM |
| Я Календарь | com.yandex.calendar.app | root; CalDAV — через API | OPEN |
| Яндекс Диск | ru.yandex.disk | root (`yandexdisk://`); файлы — WebDAV API | OPEN |
| File Manager (vivo) | com.android.filemanager | root; SAF | OPEN |
| vivo Cloud | com.bbk.cloud | root (`vivocloud://`) | OPEN |
| Calculator/Clock/Compass/Notes/Weather/Recorder | com.vivo.* | root (`calculator://`, `bbkclock://`, `bbknotes://`, `weather://`…) | OPEN |
| Camera | com.android.camera | `shortcut://` (запуск камеры); `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` | OPEN |
| Themes/iManager/Feedback/Lock/Simple View | com.bbk.theme / com.vivo.* | root | OPEN |

### 5.9 Сервисы и операторы (преимущественно root)

| Приложение | Пакет | Глубокие действия | Риск |
|---|---|---|---|
| Госуслуги | ru.rostel | root (`gosuslugi://`); Биометрия — deeplink `InitiatorSystem/ResultUrl` (от банка) | CONFIRM |
| Профи.ру | ru.profi.client | root (`profiapp://`) | OPEN |
| Docmed / docmed online | ru.docmed.app / ru.docmedonline.app | root (`docmed-app://`, `telemeddocmed://`); sbp — callback | OPEN |
| SmartMed Medsi | ru.mts.smartmed | root; spaysdk — callback | OPEN |
| Здоровье | com.core.team.renins | root (`renhealth://`) | OPEN |
| Россети МР | com.rosseti.rossetimr | root | OPEN |
| МегаФон | ru.megafon.mlk | root (`megafonmlk://`) | OPEN |
| Т-Мобайл | ru.tinkoff.mvno | root (`tinkoffmobile://`, `tmobile://`) | OPEN |
| Настройки | com.android.settings | `settings://`, `package://{pkg}` (экран приложения) | OPEN |
| Безопасность | com.google.android.apps.safetyhub | `tel:` | CONFIRM |

### 5.10 AI-чаты и браузеры (root / открытие URL)

| Приложение | Пакет | Глубокие действия | Риск |
|---|---|---|---|
| DeepSeek | com.deepseek.chat | root (`dpsk://`) | OPEN |
| Kimi | com.moonshot.kimichat | root (`kimi://`) | OPEN |
| Qwen | com.tongyi.intl | root (`qwen://`) | OPEN |
| Алиса AI | com.yandex.aliceapp | root (`aliceapp://`) | OPEN |
| Chrome | com.android.chrome | `googlechrome://{url}`, открытие URL | OPEN |
| Яндекс Браузер | com.yandex.browser | `https://yandex.ru/search/?text={q}`, открытие URL | OPEN |
| Google | com.google.android.googlequicksearchbox | `google://` поиск | OPEN |
| Яндекс Клавиатура | ru.yandex.androidkeyboard | root | OPEN |
| Walter | com.example.dispatcher | self | — |
| com.netgt.magicfield | com.netgt.magicfield | root | OPEN |

### 5.11 Вывод по покрытию

- **Реальные параметризованные deep links** (открытая документация): Яндекс Карты/Музыка/Go, Telegram, OZON, Авито, YouTube, Google Play, AppGallery, RuStore, mailto/smsto/tel, Tumblr, VK Видео, Meshtastic, OpenVPN, AdGuard, otpauth, MAX, market.
- **Только root / системные интенты**: банки (кроме экрана), операторы, умный дом (управление устройством — облачный API, не deep link), AI-чаты, большинство vivo-системных, сервисы.
- **Callback-схемы** (не действия): `auth`, `recaptcha`, `spaysdk`, `bank100…`, `fbconnect`, `esia`, `growing.*`, `passport`, `login`, `okauth`, `genericidp`, `tagmanager.*`, `vk…`, `yx…`, `ut.…`, `micoSdk`, `rnsdkqqmusicauth`.
- Управление умным домом и платежи **не делаются через deep link** автономно: либо облачный API с авторизацией (отдельный инструмент), либо человеческий чекпоинт.
