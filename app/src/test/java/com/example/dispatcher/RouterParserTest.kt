package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterParserTest {

    @Test
    fun parsesOpenApp() {
        val a = parseAction(
            """{"action":"open_app","app":"Сбербанк","title":null,"when":null,"address":null,"reply":null}"""
        )
        assertEquals("open_app", a.action)
        assertEquals("Сбербанк", a.app)
    }

    @Test
    fun nullsDoNotBecomeStrings() {
        val a = parseAction("""{"action":"open_app","app":null}""")
        assertNull(a.app)
    }

    @Test
    fun missingKeysDefaultToChat() {
        val a = parseAction("{}")
        assertEquals("chat", a.action)
        assertNull(a.reply)
    }

    @Test
    fun formatsSmsEvent() {
        val s = eventToText(EventInfo("sms", "Т-Банк", "", "Покупка 1 234,50 ₽", 0L))
        assertTrue(s.contains("SMS от «Т-Банк»"))
        assertTrue(s.contains("Покупка 1 234,50 ₽"))
    }

    @Test
    fun eventKeyUnique() {
        val a = EventInfo("sms", "Т-Банк", "", "x", 1L)
        val b = EventInfo("sms", "Т-Банк", "", "x", 2L)
        assertTrue(eventKey(a) != eventKey(b))
    }
}
