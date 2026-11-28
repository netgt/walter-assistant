package com.example.dispatcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionConfirmationTest {
    @Before
    fun reset() = ActionConfirmation.reset()

    @Test
    fun sendCannotConsumeWithoutRealConfirmation() {
        ActionConfirmation.prepare("tg_send", "chat|text", 1000)
        assertFalse(ActionConfirmation.consume("tg_send", "chat|text", 1001))
    }

    @Test
    fun confirmationIsPayloadBoundAndOneShot() {
        ActionConfirmation.prepare("tg_send", "chat|text", 1000)
        ActionConfirmation.notePrompt("Отправить?")
        ActionConfirmation.confirmPending("Отправить?", 1002)
        assertTrue(ActionConfirmation.consume("tg_send", "chat|text", 1003))
        assertFalse(ActionConfirmation.consume("tg_send", "chat|text", 1004))
    }

    @Test
    fun changedPayloadInvalidatesGrant() {
        ActionConfirmation.prepare("tg_send", "chat|text", 1000)
        ActionConfirmation.confirmPending("", 1001)
        assertFalse(ActionConfirmation.consume("tg_send", "chat|changed", 1002))
    }

    @Test
    fun normalizedUiPromptStillConfirmsPendingAction() {
        ActionConfirmation.prepare("tg_send_file", "file", 1000)
        ActionConfirmation.notePrompt("Отправить файл?")
        ActionConfirmation.confirmPending("Готово\n**Подтверди**", 1001)
        assertTrue(ActionConfirmation.consume("tg_send_file", "file", 1002))
    }

    @Test
    fun expiredGrantCannotBeUsed() {
        ActionConfirmation.prepare("tg_send", "chat|text", 1000)
        ActionConfirmation.confirmPending("", 1001)
        assertFalse(ActionConfirmation.consume("tg_send", "chat|text", 1001 + 120_001))
    }
}
