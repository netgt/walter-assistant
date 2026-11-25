package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustParseTest {

    @Test
    fun repairsTruncatedJson() {
        val raw = "{\"action\":\"list_sms\",\"app\":null,\"title\":null,\"when\":null,\"address\":null,\"reply\":null"
        val a = extractAction(raw)
        assertNotNull(a)
        assertEquals("list_sms", a!!.action)
    }

    @Test
    fun extractsReplyWithBrokenEscapes() {
        val raw = "{\"action\":\"chat\",\"app\":null,\"title\":null,\"when\":null,\"address\":null,\"reply\":\"Последние SMS:\n• Т-Банк: покупка\""
        val a = extractAction(raw)
        assertNotNull(a)
        assertEquals("chat", a!!.action)
        assertTrue(a.reply!!.contains("Т-Банк"))
    }

    @Test
    fun garbageReturnsNull() {
        assertNull(extractAction("здесь нет никакого действия"))
    }




}
