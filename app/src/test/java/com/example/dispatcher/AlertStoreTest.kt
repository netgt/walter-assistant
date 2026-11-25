package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertStoreTest {

    @Test
    fun presetsAreExactlyTwoAlerts() {
        assertEquals(2, AlertStore.PRESETS.size)
        assertEquals(listOf("Работа с документами из почты", "Лимит по карте"), AlertStore.PRESETS.map { it.name })
        assertEquals("push", AlertStore.PRESETS[0].watchChannel)
        assertEquals("read_sms", AlertStore.PRESETS[1].tools)
    }

    @Test
    fun jsonRoundtripKeepsOnlyAlertFields() {
        val alert = AlertDef(
            id = 7,
            name = "Проверка",
            tools = "read_sms",
            instructions = "Инструкция",
            watchChannel = "sms",
            watchTool = "read_sms",
            watchRule = "Правило",
            watchEvery = 15,
            watchParam = "параметр"
        )
        val json = AlertStore.toJson(listOf(alert))
        assertEquals(listOf(alert), AlertStore.fromJson(json))
        listOf("\"trigger\"", "\"aliases\"", "\"steps\"", "\"mode\"").forEach {
            assertFalse(json.contains(it))
        }
    }

    @Test
    fun legacyMigrationKeepsOnlyBackgroundRecords() {
        val raw = """
            [
              {"id":1,"name":"Обычный чат","mode":"chat","trigger":"фраза"},
              {"id":2,"name":"Банк","mode":"watch","tools":"read_sms","instructions":"Правило","watchChannel":"sms","watchEvery":0,"aliases":["лишнее"],"steps":["лишнее"]}
            ]
        """.trimIndent()
        val migrated = AlertStore.fromLegacyJson(raw)
        assertEquals(1, migrated.size)
        assertEquals(AlertDef(2, "Банк", "read_sms", "Правило", "sms"), migrated.single())
    }

    @Test
    fun invalidJsonIsEmpty() {
        assertTrue(AlertStore.fromJson(null).isEmpty())
        assertTrue(AlertStore.fromJson("мусор").isEmpty())
        assertTrue(AlertStore.fromLegacyJson("мусор").isEmpty())
    }

    @Test
    fun channelLabelsAndAliasesAreNormalized() {
        assertEquals("push", AlertStore.normalizeChannel("ПУШ"))
        assertEquals("sms", AlertStore.normalizeChannel("СМС"))
        assertEquals("tool", AlertStore.normalizeChannel("ФОН"))
        assertEquals("ПУШ", AlertStore.channelLabel(AlertStore.PRESETS[0]))
        assertEquals("СМС", AlertStore.channelLabel(AlertDef(1, "Банк", watchChannel = "sms")))
    }

    @Test
    fun formatListShowsFieldsDatesAndColoredStatuses() {
        val alerts = listOf(
            AlertDef(1, "Почта", "open_app", "Правило почты", "push", watchRule = "Вложение"),
            AlertDef(2, "Банк", "read_sms", "Правило банка", "tool", "read_sms", watchEvery = 15)
        )
        val text = AlertStore.formatList(alerts, disabled = setOf(2L)) { id -> if (id == 1L) 0L else 1763900000000L }
        assertTrue(text.contains("**Название:** Почта"))
        assertTrue(text.contains("**Канал:** ПУШ"))
        assertTrue(text.contains("**Правило:** Вложение"))
        assertTrue(text.contains("**Запуск:** ещё не запускалось"))
        assertTrue(text.contains("ФОН (read_sms, каждые 15 мин)"))
        assertTrue(text.contains("[[g:**включено**]]"))
        assertTrue(text.contains("[[r:**выключено**]]"))
    }
}
