package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramChannelsLimitTest {
    @Test
    fun parserReturnsMoreThanThirtyPosts() {
        val html = buildString {
            repeat(40) { i ->
                append("<div class=\"tgme_widget_message_text\">Пост $i</div>")
                append("<time datetime=\"2026-11-27T12:00:00Z\">")
            }
        }
        assertEquals(40, TelegramChannels.parse(html).size)
    }
}
