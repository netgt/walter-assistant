package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchEngineTest {

    private fun alert(id: Long, tools: String, name: String = "Следилка") =
        AlertDef(id, name, tools, "Правило: сработай, если баланс ниже 100 000.", watchChannel = "")

    @Test
    fun matchesChannelByTools() {
        val sms = alert(1, "read_sms")
        val pushes = alert(2, "read_pushes")
        val multi = alert(3, "read_sms, read_pushes")
        assertTrue(WatchEngine.matchesChannel(sms, "sms"))
        assertFalse(WatchEngine.matchesChannel(sms, "push"))
        assertTrue(WatchEngine.matchesChannel(pushes, "push"))
        assertTrue(WatchEngine.matchesChannel(multi, "sms"))
        assertTrue(WatchEngine.matchesChannel(multi, "push"))
        assertFalse(WatchEngine.matchesChannel(sms, "mail"))
    }

    @Test
    fun logRoundtripAndRingBuffer() {
        val e1 = WatchEvent("sms", "Т-Банк", "Баланс 90 000", 1000L)
        val e2 = WatchEvent("push", "Telegram", "Привет", 2000L)
        val json = WatchEngine.logToJson(listOf(e1, e2))
        val back = WatchEngine.logFromJson(json)
        assertEquals(listOf(e1, e2), back)
        assertEquals(emptyList<WatchEvent>(), WatchEngine.logFromJson(null))
        assertEquals(emptyList<WatchEvent>(), WatchEngine.logFromJson("не json"))
    }

    @Test
    fun appendKeepsLastMax() {
        var list = emptyList<WatchEvent>()
        for (i in 1..(WatchEngine.MAX_LOG + 5)) {
            list = WatchEngine.append(list, WatchEvent("push", "x", "t$i", i.toLong()))
        }
        assertEquals(WatchEngine.MAX_LOG, list.size)
        assertEquals("t6", list.first().text)
    }

    @Test
    fun disabledRoundtripAndEnabled() {
        val d = setOf(1L, 3L)
        assertEquals(d, WatchEngine.disabledFromJson(WatchEngine.disabledToJson(d)))
        assertEquals(emptySet<Long>(), WatchEngine.disabledFromJson(null))
        assertTrue(WatchEngine.isEnabled(2L, d))
        assertFalse(WatchEngine.isEnabled(1L, d))
    }

    @Test
    fun parseFireStrictAndRepaired() {
        val (f1, t1) = WatchEngine.parseFire("""{"fire": true, "text": "Баланс ниже 100 000"}""")
        assertTrue(f1)
        assertEquals("Баланс ниже 100 000", t1)
        val (f2, t2) = WatchEngine.parseFire("""{"fire":false}""")
        assertFalse(f2)
        assertNull(t2)
        // ответ с мусором вокруг JSON
        val (f3, t3) = WatchEngine.parseFire("Проверил правило.\n{\"fire\": true, \"text\": \"карта 1234: остаток 99 500 ₽\"}\nГотово.")
        assertTrue(f3)
        assertNotNull(t3)
        assertTrue(t3!!.contains("1234"))
        // нет JSON — нет срабатывания
        val (f4, t4) = WatchEngine.parseFire("не сработало")
        assertFalse(f4)
        assertNull(t4)
    }

    @Test
    fun fileStateRuleHasStablePerAlertPath() {
        val a = AlertDef(42, "Долг", "", "Оповещать о новых порогах и записывать пройденные пороги в файл", watchChannel = "push")
        assertTrue(WatchEngine.usesFileState(a))
        assertEquals("Download/walter-alerts/alert-42.json", WatchEngine.stateRelativePath(a))
        val text = WatchEngine.buildUserText(a, listOf(WatchEvent("push", "Альфа-Банк", "Долг 450000", 1L)))
        assertTrue(text.contains("ФАЙЛ СОСТОЯНИЯ"))
        assertTrue(text.contains("passed_thresholds"))
        val ordinary = alert(7, "read_pushes")
        assertFalse(WatchEngine.usesFileState(ordinary))
    }

    @Test
    fun buildUserTextContainsRuleAndEvents() {
        val s = alert(1, "read_sms", name = "Лимит по карте")
        val text = WatchEngine.buildUserText(
            s,
            listOf(WatchEvent("sms", "Т-Банк", "Баланс карты *1234: 99 500 ₽", 1000L))
        )
        assertTrue(text.contains("ПРАВИЛО СЛЕДИЛКИ «Лимит по карте»"))
        assertTrue(text.contains("Баланс карты *1234"))
        assertTrue(text.contains("SMS от «Т-Банк»"))
        assertTrue(WatchEngine.buildUserText(s, emptyList()).contains("(данных канала пока нет)"))
    }
}
