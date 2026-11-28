package com.example.dispatcher

/** Одна строка настроек инструмента: подпись + значение (пароли/ключи — открытым текстом).
 *  editKey — ключ сохранения: поле можно редактировать прямо в карточке. */
data class ToolSetting(val label: String, val value: String, val editKey: String? = null)

/** Карточка инструмента: id + описание + настройки + форма добавления (для аккаунтов). */
data class ToolCardView(
    val name: String,
    val settings: List<ToolSetting>,
    val addForm: List<ToolSetting> = emptyList()
)

/**
 * Карточки ВСЕХ инструментов (реестр передаётся из MainActivity):
 * у каждого — описание, у имеющих настройки — полные значения (редактируются в карточке).
 * Чистая функция — тестируется в JVM.
 */
object ToolSettings {

    fun build(
        readTools: List<Pair<String, String>>,
        terminalTools: List<Pair<String, String>>,
        localTools: List<Pair<String, String>>,
        searchProvider: String,
        yandexKey: String,
        yandexUrl: String,
        geoKey: String,
        geoUrl: String,
        locationGranted: Boolean,
        calendarGranted: Boolean,
        allFilesAccess: Boolean,
        mailAccounts: List<MailAccount>,
        caldavAccounts: List<CalDavAccount>,
        diskAccounts: List<DiskAccount>,
        oauthToken: String,
        telegramChannels: String = ""
    ): List<ToolCardView> {
        val cards = mutableListOf<ToolCardView>()
        fun card(id: String, desc: String, settings: List<ToolSetting>, addForm: List<ToolSetting> = emptyList()) {
            cards += ToolCardView(id, listOf(ToolSetting("Описание", desc)) + settings, addForm)
        }
        fun settingsFor(id: String): List<ToolSetting> = when (id) {
            "web_search" -> listOf(
                ToolSetting("Движки", "Яндекс + DuckDuckGo (всегда оба)"),
                ToolSetting("Ключ Яндекса", yandexKey.ifBlank { "(не задан)" }, "search.key"),
                ToolSetting("URL поиска", yandexUrl.ifBlank { "(по умолчанию)" }, "search.url")
            )
            "geocode", "reverse_geocode", "distance" -> listOf(
                ToolSetting("Ключ геокодера", geoKey.ifBlank { "(не задан)" }, "geo.key"),
                ToolSetting("URL геокодера", geoUrl.ifBlank { "(по умолчанию)" }, "geo.url")
            )
            "location_now" -> listOf(
                ToolSetting("Местоположение", if (locationGranted) "разрешено" else "не разрешено (Права)")
            )
            "route_info" -> listOf(
                ToolSetting("Ключ Яндекс API (поиск)", yandexKey.ifBlank { "(не задан)" }, "search.key"),
                ToolSetting("URL поиска", yandexUrl.ifBlank { "(по умолчанию)" }, "search.url")
            )
            "read_mail" -> mailSettings(mailAccounts)
            "read_calendar" -> calSettings(caldavAccounts, calendarGranted)
            "fetch_url" -> listOf(
                ToolSetting("Доступ", "прямой HTTPS GET произвольной HTTP/HTTPS-страницы по полному URL")
            )
            "disk_list", "disk_files", "disk_read", "disk_download",
            "disk_write", "disk_upload", "disk_delete", "disk_move" -> diskSettings(diskAccounts)
            "disk_publish" -> listOf(
                ToolSetting("OAuth-токен", oauthToken.ifBlank { "(не задан)" }, "oauth")
            )
            "read_files", "read_dirs", "read_dir", "find_files", "grep_files",
            "read_file", "write_file", "create_file" -> listOf(
                ToolSetting("Право «Все файлы»", if (allFilesAccess) "выдано" else "не выдано (Права)")
            )
            else -> emptyList()
        }
        val mailForm = listOf(
            ToolSetting("IMAP сервер", "imap.yandex.ru"),
            ToolSetting("IMAP порт", "993"),
            ToolSetting("Логин", "email"),
            ToolSetting("Пароль приложения", ""),
            ToolSetting("SMTP сервер", "smtp.yandex.ru"),
            ToolSetting("SMTP порт", "465")
        )
        val calForm = listOf(
            ToolSetting("Метка", "например, имя"),
            ToolSetting("URL", CalDavStore.DEFAULT_SERVER),
            ToolSetting("Логин", "email"),
            ToolSetting("Пароль приложения", "")
        )
        val diskForm = listOf(
            ToolSetting("Сервер", YandexDisk.WEBDAV_DEFAULT),
            ToolSetting("Логин", "email"),
            ToolSetting("Пароль приложения", "")
        )
        readTools.forEach { (id, desc) ->
            card(id, desc, settingsFor(id), when (id) {
                "read_mail" -> mailForm
                "read_calendar" -> calForm
                "disk_list" -> diskForm
                else -> emptyList()
            })
        }
        terminalTools.forEach { (id, desc) -> card(id, desc, settingsFor(id)) }
        localTools.forEach { (id, desc) -> card(id, desc, settingsFor(id)) }
        return cards.sortedBy { it.name.lowercase() }
    }

    private fun mailSettings(accounts: List<MailAccount>): List<ToolSetting> {
        if (accounts.isEmpty()) return listOf(ToolSetting("Аккаунты", "(нет)"))
        val out = mutableListOf<ToolSetting>()
        accounts.forEachIndexed { i, a ->
            out += ToolSetting("IMAP ${i + 1}", "${a.host}:${a.port}", "mail.$i.host")
            out += ToolSetting("SMTP ${i + 1}", "${a.smtpHost.ifBlank { "(авто из IMAP)" }}:${a.smtpPort}", "mail.$i.smtp")
            out += ToolSetting("Логин", a.user, "mail.$i.user")
            out += ToolSetting("Пароль приложения", a.pass, "mail.$i.pass")
        }
        return out
    }

    private fun calSettings(accounts: List<CalDavAccount>, systemGranted: Boolean): List<ToolSetting> {
        val out = mutableListOf<ToolSetting>()
        out += ToolSetting("Системный календарь", if (systemGranted) "разрешено" else "не разрешено (Права)")
        if (accounts.isEmpty()) {
            out += ToolSetting("CalDAV-аккаунты", "(нет)")
        } else {
            accounts.forEachIndexed { i, a ->
                out += ToolSetting("Аккаунт ${i + 1}", a.label.ifBlank { a.user }, "caldav.$i.label")
                out += ToolSetting("URL", a.url, "caldav.$i.url")
                out += ToolSetting("Логин", a.user, "caldav.$i.user")
                out += ToolSetting("Пароль приложения", a.pass, "caldav.$i.pass")
            }
        }
        return out
    }

    private fun diskSettings(accounts: List<DiskAccount>): List<ToolSetting> {
        if (accounts.isEmpty()) return listOf(ToolSetting("WebDAV-аккаунты", "(нет)"))
        val out = mutableListOf<ToolSetting>()
        accounts.forEachIndexed { i, a ->
            out += ToolSetting("Аккаунт ${i + 1}", a.baseUrl, "disk.$i.server")
            out += ToolSetting("Логин", a.user, "disk.$i.user")
            out += ToolSetting("Пароль приложения", a.pass, "disk.$i.pass")
        }
        return out
    }
}
