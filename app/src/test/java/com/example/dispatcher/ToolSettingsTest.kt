package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSettingsTest {

    private val read = listOf(
        "read_sms" to "последние SMS",
        "read_mail" to "письма",
        "fetch_url" to "читает страницы и каналы",
        "read_calendar" to "события",
        "web_search" to "поиск в интернете",
        "geocode" to "геокодинг",
        "location_now" to "местоположение",
        "route_info" to "маршрут",
        "disk_list" to "папки Диска",
        "disk_publish" to "публичная ссылка",
        "read_file" to "чтение файла",
        "open_app" to "открыть приложение"
    )
    private val terminal = listOf("open_app" to "открыть приложение", "chat" to "ответ")
    private val local = listOf("remember" to "запомнить")

    private fun build(
        mail: List<MailAccount> = listOf(MailAccount(1, "imap.yandex.ru", 993, "a@yandex.ru", "mail-pass")),
        cal: List<CalDavAccount> = listOf(CalDavAccount(1, "Я", "https://caldav.yandex.ru", "a@yandex.ru", "cal-pass")),
        disk: List<DiskAccount> = listOf(DiskAccount(1, "https://webdav.yandex.ru", "a@yandex.ru", "disk-pass")),
        oauth: String = "oauth-token",
    ) = ToolSettings.build(
        read, terminal, local,
        "yandex", "sk-search", "https://search.url",
        "geo-key", "https://geo.url", true, true, true,
        mail, cal, disk, oauth
    )

    @Test
    fun cardsAreSortedAlphabetically() {
        val names = build().map { it.name }
        assertEquals(names.sortedBy { it.lowercase() }, names)
    }

    @Test
    fun cardsCoverAllRegistryTools() {
        val cards = build()
        val ids = cards.map { it.name }
        // каждый инструмент реестра — своя карточка
        (read + terminal + local).forEach { (id, _) -> assertTrue("нет карточки $id", id in ids) }
    }

    @Test
    fun everyCardHasDescription() {
        build().forEach { c ->
            assertTrue("нет описания у ${c.name}", c.settings.any { it.label == "Описание" && it.value.isNotBlank() })
        }
    }

    @Test
    fun settingsVisibleInPlainText() {
        val byId = build().associateBy { it.name }
        assertTrue(byId["read_mail"]!!.settings.any { it.value == "mail-pass" })
        assertTrue(byId["read_mail"]!!.settings.any { it.value == "a@yandex.ru" })
        assertTrue(byId["read_calendar"]!!.settings.any { it.value == "cal-pass" })
        assertTrue(byId["web_search"]!!.settings.any { it.value == "sk-search" })
        assertTrue(byId["geocode"]!!.settings.any { it.value == "geo-key" })
        assertTrue(byId["route_info"]!!.settings.any { it.value == "sk-search" })
        assertTrue(byId["disk_list"]!!.settings.any { it.value == "disk-pass" })
        assertTrue(byId["disk_publish"]!!.settings.any { it.value == "oauth-token" })
    }

    @Test
    fun fetchUrlSettings() {
        val ch = build().associateBy { it.name }["fetch_url"]!!
        assertEquals(2, ch.settings.size)
        assertEquals("Описание", ch.settings.first().label)
        assertEquals("Доступ", ch.settings[1].label)
        assertTrue(ch.settings[1].value.contains("HTTPS GET"))
        assertTrue(ch.settings.none { it.label.contains("Telegram") })
    }

    @Test
    fun toolsWithoutSettingsHaveOnlyDescription() {
        val open = build().associateBy { it.name }["open_app"]!!
        assertEquals(1, open.settings.size)
        assertEquals("Описание", open.settings.single().label)
        val chat = build().associateBy { it.name }["chat"]!!
        assertEquals(1, chat.settings.size)
    }

    @Test
    fun emptyStateShowsHints() {
        val byId = ToolSettings.build(
            read, terminal, local,
            "ddg", "", "", "", "", false, false, false,
            emptyList(), emptyList(), emptyList(), ""
        ).associateBy { it.name }
        assertTrue(byId["read_mail"]!!.settings.any { it.value == "(нет)" })
        assertTrue(byId["read_calendar"]!!.settings.any { it.value == "(нет)" })
        assertTrue(byId["disk_list"]!!.settings.any { it.value == "(нет)" })
        assertTrue(byId["disk_publish"]!!.settings.any { it.value == "(не задан)" })
        assertTrue(byId["read_file"]!!.settings.any { it.value == "не выдано (Права)" })
    }

    @Test
    fun editableFieldsHaveEditKeys() {
        val byId = build().associateBy { it.name }
        assertEquals("search.key", byId["web_search"]!!.settings.first { it.label == "Ключ Яндекса" }.editKey)
        assertTrue(byId["web_search"]!!.settings.any { it.label == "Движки" && it.value.contains("оба") })
        assertEquals("geo.key", byId["geocode"]!!.settings.first { it.label == "Ключ геокодера" }.editKey)
        assertEquals("oauth", byId["disk_publish"]!!.settings.first { it.label == "OAuth-токен" }.editKey)
        // аккаунты редактируются по ключам mail.N.* / caldav.N.* / disk.N.*
        assertTrue(byId["read_mail"]!!.settings.any { it.editKey == "mail.0.host" })
        assertTrue(byId["read_mail"]!!.settings.any { it.editKey == "mail.0.smtp" })
        assertTrue(byId["read_mail"]!!.settings.any { it.editKey == "mail.0.user" })
        assertTrue(byId["read_mail"]!!.settings.any { it.editKey == "mail.0.pass" })
        assertTrue(byId["read_calendar"]!!.settings.any { it.editKey == "caldav.0.url" })
        assertTrue(byId["disk_list"]!!.settings.any { it.editKey == "disk.0.pass" })
        // у инструментов без настроек — ничего редактируемого
        assertTrue(byId["open_app"]!!.settings.all { it.editKey == null })
    }

    @Test
    fun accountAddFormsPresent() {
        val byId = build().associateBy { it.name }
        assertEquals(6, byId["read_mail"]!!.addForm.size)
        assertEquals(4, byId["read_calendar"]!!.addForm.size)
        assertEquals(3, byId["disk_list"]!!.addForm.size)
        assertTrue(byId["read_sms"]!!.addForm.isEmpty())
    }
}
