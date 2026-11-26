package com.example.dispatcher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventTimeTest {
    @Test
    fun pushAndSmsShowFullDateAndSeconds() {
        val at = 1795685573646L
        val expected = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(at))
        assertTrue(eventTime(EventInfo("push", "Gmail", "Тема", "Текст", at)) == expected)
        assertTrue(eventTime(EventInfo("sms", "900", "", "Код", at)) == expected)
    }
}
