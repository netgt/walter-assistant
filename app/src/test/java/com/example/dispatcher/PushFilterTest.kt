package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushFilterTest {

    @Test
    fun groupSummaryAndOngoingSkipped() {
        assertTrue(PushFilter.shouldSkip("Любой", "текст", isGroupSummary = true, ongoing = false))
        assertTrue(PushFilter.shouldSkip("Плеер", "Играет трек", isGroupSummary = false, ongoing = true))
    }

    @Test
    fun countersSkipped() {
        assertTrue(PushFilter.shouldSkip("Max", "1 сообщение от Марии", false, false))
        assertTrue(PushFilter.shouldSkip("Max", "3 новых сообщений", false, false))
        assertTrue(PushFilter.shouldSkip("Telegram", "2 сообщения", false, false))
        assertTrue(PushFilter.shouldSkip("", "1 новое сообщение", false, false))
        assertTrue(PushFilter.shouldSkip("", "5 новых уведомлений", false, false))
    }

    @Test
    fun informativePushPasses() {
        assertFalse(PushFilter.shouldSkip("Telegram", "Мария: привет, как дела?", false, false))
        assertFalse(PushFilter.shouldSkip("Банк", "Списание 450 ₽, остаток 12 000 ₽", false, false))
    }

    @Test
    fun emptySkipped() {
        assertTrue(PushFilter.shouldSkip("", "", false, false))
    }

    @Test
    fun tickerFallbackRestoresGmailLikeText() {
        assertEquals("Отправитель: тема письма", PushFilter.fallbackText("", "Отправитель: тема письма"))
        assertEquals("Обычный текст", PushFilter.fallbackText("Обычный текст", "Старый ticker"))
    }

    @Test
    fun loneInformativeGroupSummaryCanBeUsed() {
        assertTrue(PushFilter.useGroupSummary(true, false, "Письмо от Ивана", "Документ во вложении"))
        assertFalse(PushFilter.useGroupSummary(true, true, "Письмо от Ивана", "Документ во вложении"))
        assertFalse(PushFilter.useGroupSummary(true, false, "Max", "3 новых сообщений"))
        assertFalse(PushFilter.useGroupSummary(true, false, "", ""))
    }

    // ─── полный текст пуша ───

    @Test
    fun bigTextWinsOverSmall() {
        // короткая версия в EXTRA_TEXT, полная — в EXTRA_BIG_TEXT
        val t = PushFilter.pushText("Мария: привет, как…", "Мария: привет, как дела? Завтра встречаемся в 19:00 у метро, не опаздывай!", emptyList(), emptyList(), "")
        assertTrue(t.startsWith("Мария: привет, как дела?"))
        assertTrue(t.contains("не опаздывай"))
    }

    @Test
    fun textLinesJoinedWhenLongest() {
        val t = PushFilter.pushText(
            "Сводка", "",
            listOf("Иван: передай документы", "Иван: завтра до 12:00", "Иван: спасибо!"),
            emptyList(), ""
        )
        assertTrue(t.contains("Иван: передай документы"))
        assertTrue(t.contains("Иван: спасибо!"))
    }

    @Test
    fun messagingStyleMessagesUsed() {
        val t = PushFilter.pushText(
            "Новое сообщение", "Новое сообщение",
            emptyList(),
            listOf("Мария: я на месте", "Мария: жду у входа"),
            ""
        )
        assertTrue(t.contains("Мария: я на месте"))
        assertTrue(t.contains("жду у входа"))
    }

    @Test
    fun subTextAppendedIfMissing() {
        val t = PushFilter.pushText("", "Тело письма", emptyList(), emptyList(), "От кого-то важного")
        assertEquals("Тело письма\nОт кого-то важного", t)
    }

    @Test
    fun subTextNotDuplicated() {
        val t = PushFilter.pushText("", "Текст с подписью: Отправитель", emptyList(), emptyList(), "Отправитель")
        assertEquals("Текст с подписью: Отправитель", t)
    }

    @Test
    fun longTextCapped() {
        val big = "а".repeat(3000)
        val t = PushFilter.pushText("а", big, emptyList(), emptyList(), "")
        assertEquals(2000, t.length)
    }

    @Test
    fun titleBigWins() {
        assertEquals("Полный заголовок", PushFilter.pushTitle("Короткий", "Полный заголовок"))
        assertEquals("Только короткий", PushFilter.pushTitle("Только короткий", ""))
    }
}
