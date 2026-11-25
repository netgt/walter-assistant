package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailStoreTest {

    @Test
    fun htmlToTextBasic() {
        val t = MailStore.htmlToText(
            "<html><head><style>p{color:red}</style></head><body>" +
                "<p>Привет, <b>мир</b>!</p><br>Вторая&nbsp;строка &amp; финал" +
                "<script>evil()</script></body></html>"
        )
        assertTrue(t.contains("Привет, мир!"))
        assertTrue(t.contains("Вторая строка & финал"))
        assertFalse(t.contains("<"))
        assertFalse(t.contains("evil()"))
        assertFalse(t.contains("color:red"))
    }

    @Test
    fun htmlToTextEntitiesAndNewlines() {
        val t = MailStore.htmlToText("<div>строка один</div><div>строка два</div><p>абзац</p>")
        assertEquals("строка один\nстрока два\nабзац", t)
    }

    @Test
    fun htmlToTextPlainPassthrough() {
        assertEquals("просто текст", MailStore.htmlToText("просто текст"))
        assertEquals("", MailStore.htmlToText(""))
    }

    @Test
    fun cleanBodyStripsHtml() {
        // письмо только с HTML-телом: карточка не должна показывать теги
        val raw = "<!DOCTYPE html><html><body><p>Здравствуйте, это уведомление.</p><p>Подтвердите <b>операцию</b>.</p></body></html>"
        val t = MailStore.cleanBody(raw)
        assertTrue(t.contains("Здравствуйте, это уведомление."))
        assertTrue(t.contains("Подтвердите операцию."))
        assertFalse(t.contains("<"))
        assertFalse(t.contains("DOCTYPE"))
    }

    @Test
    fun cleanBodyStripsHtmlDisguisedAsPlain() {
        // часть «text/plain», в которой на самом деле HTML (бывает у массовых рассылок)
        val raw = "Скидка 50%<br><a href=\"https://x.ru\">перейти</a>&nbsp;до 30.11"
        val t = MailStore.cleanBody(raw)
        assertTrue(t.contains("Скидка 50%"))
        assertTrue(t.contains("перейти"))
        assertTrue(t.contains("до 30.11"))
        assertFalse(t.contains("href"))
    }

    @Test
    fun cleanBodyKeepsPlainText() {
        assertEquals("обычное письмо без разметки", MailStore.cleanBody("обычное письмо без разметки"))
        assertEquals("2 < 3 и всё", MailStore.cleanBody("2 < 3 и всё"))
    }

    @Test
    fun htmlToTextDecodesNumericEntities() {
        assertEquals("тире — дефис – многоточие …", MailStore.htmlToText("тире &#8212; дефис &#8211; многоточие &#8230;"))
        assertEquals("кавычки \"ёлочки\"", MailStore.htmlToText("кавычки &#34;ёлочки&#34;"))
    }
}
