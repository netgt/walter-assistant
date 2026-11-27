# Проверка URI на IQOO 13

Проверено через `adb -d shell am start -a android.intent.action.VIEW -d <URI> -p <package>`.

Важно: открытие root-схемы не означает выполнение операции внутри приложения.

| URI | Пакет | Что подтверждено | Использование в Walter |
|---|---|---|---|
| `mailto:` | `com.google.android.gm` | Открывается новое письмо Gmail | `mail.compose`, verified deep link |
| `mailto:` | `ru.yandex.mail` | Открывается новое письмо Яндекс Почты | `mail.compose`, verified deep link |
| `smsto:` | `com.android.mms` | Открывается новое SMS | `communication.sms`, verified deep link |
| `tel:` | `com.android.contacts` | Открывается набор номера | `communication.call`, verified deep link |
| `yandexmaps://` | `ru.yandex.yandexmaps` | Открывается Яндекс Карты | Только открытие, не маршрут |
| `yandextaxi://` | `ru.yandex.taxi` | Открывается ЯндексGo | Только открытие, не заказ поездки |
| `yandexmusic://` | `ru.yandex.music` | Открывается Яндекс Музыка | Только открытие, не запуск трека |
| `tg://` | `org.telegram.messenger` | Открывается Telegram | Только открытие, не отправка сообщения |

Служебные схемы вроде `auth`, `recaptcha`, `spaysdk`, `bank100…`, `fbconnect`, `esia` не считаются пользовательскими действиями и не добавляются как таковые.

Полная матрица launcher activities и схем: [APP-ACTIONS.md](APP-ACTIONS.md).
