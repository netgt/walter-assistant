package com.example.dispatcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramAlertWorkerTest {
    @Test
    fun tdlibHistoryAlertUsesPersistentWorker() {
        val alert = AlertDef(
            id = 1, name = "Тест", watchChannel = "tool", watchTool = "tg_history",
            watchEvery = 15, watchParam = "{\"chat\":\"me\",\"count\":5}"
        )
        assertTrue(TelegramAlertScheduler.isTelegramAlert(alert))
    }

    @Test
    fun pushOrImmediateToolsAreNotPeriodicTelegramAlerts() {
        assertFalse(TelegramAlertScheduler.isTelegramAlert(AlertDef(1, "Пуш", watchChannel = "push", watchTool = "tg_history", watchEvery = 15)))
        assertFalse(TelegramAlertScheduler.isTelegramAlert(AlertDef(2, "Без периода", watchChannel = "tool", watchTool = "tg_history", watchEvery = 0)))
    }
}
