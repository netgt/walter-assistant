package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Test

class MailAccountsSmtpTest {
    @Test
    fun smtpFieldsRoundTrip() {
        val acc = MailAccount(1, "imap.yandex.ru", 993, "a@yandex.ru", "pass", true, "smtp.yandex.ru", 465)
        val restored = MailAccounts.fromJson(MailAccounts.toJson(listOf(acc))).single()
        assertEquals("smtp.yandex.ru", restored.smtpHost)
        assertEquals(465, restored.smtpPort)
    }

    @Test
    fun legacyAccountWithoutSmtpDefaults() {
        val restored = MailAccounts.fromJson(
            """[{"id":1,"host":"imap.gmail.com","port":993,"user":"a@gmail.com","pass":"p","ssl":true}]"""
        ).single()
        assertEquals("", restored.smtpHost)
        assertEquals(465, restored.smtpPort)
    }

    @Test
    fun addCarriesSmtp() {
        val list = MailAccounts.add(emptyList(), "imap.yandex.ru", 993, "a@yandex.ru", "p", true, "smtp.yandex.ru", 587)
        assertEquals("smtp.yandex.ru", list.single().smtpHost)
        assertEquals(587, list.single().smtpPort)
    }
}
