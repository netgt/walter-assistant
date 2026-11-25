package com.example.dispatcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventFilterTest {
    private val bank = EventInfo("push", "Альфа-Банк", "Выписка", "Долг 550000", 1L, pkg = "bank.pkg")
    private val alert = EventInfo("watch", "Долг Альфа", "", "Новый порог", 2L)

    @Test
    fun sourceMatchWinsOverTextMatches() {
        val filtered = EventFilter.filterPushes(listOf(bank, alert), "Альфа-Банк")
        assertTrue(filtered.single() === bank)
        val sms = EventFilter.filterSms(listOf(
            Triple("Walter", "Альфа-Банк уведомил", 1L),
            Triple("Альфа-Банк", "Выписка", 2L)
        ), "Альфа-Банк")
        assertTrue(sms.single().first == "Альфа-Банк")
    }

    @Test
    fun walterQueryReturnsOnlyWalterAlerts() {
        assertFalse(EventFilter.matchesPush(bank, "Walter"))
        assertTrue(EventFilter.matchesPush(alert, "Walter"))
        assertTrue(EventFilter.matchesPush(alert, "уолтер"))
        assertFalse(EventFilter.matchesPush(bank, "уолтер"))
    }

    @Test
    fun pushFilterMatchesSourceTitleTextAndPackage() {
        assertTrue(EventFilter.matchesPush(bank, "Альфа"))
        assertTrue(EventFilter.matchesPush(bank, "Выписка"))
        assertTrue(EventFilter.matchesPush(bank, "550000"))
        assertTrue(EventFilter.matchesPush(bank, "bank.pkg"))
        assertFalse(EventFilter.matchesPush(bank, "Telegram"))
        assertTrue(EventFilter.matchesPush(bank, ""))
    }

    @Test
    fun smsFilterUsesSameQueryRules() {
        assertTrue(EventFilter.matchesSms("T-Bank", "Покупка 1000", "bank"))
        assertTrue(EventFilter.matchesSms("T-Bank", "Покупка 1000", "1000"))
        assertFalse(EventFilter.matchesSms("T-Bank", "Покупка 1000", "Walter"))
        assertTrue(EventFilter.matchesSms("Walter", "Алерт", "уолтер"))
    }
}
