package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventListTest {

    private fun sample() = EventInfo("sms", "Bank", "", "текст сообщения", 1000L)

    @Test
    fun eventListKeysAreUniqueAndScoped() {
        val e = sample()
        assertEquals("sms|Bank|1000|lst0", eventListKey(e, 0))
        assertEquals("sms|Bank|1000|lst1", eventListKey(e, 1))
        assertNotEquals(eventKey(e), eventListKey(e, 0))
        // одинаковые события на разных позициях не конфликтуют
        assertNotEquals(eventListKey(e, 0), eventListKey(e, 1))
    }

    @Test
    fun msgCarriesEventLists() {
        val items = listOf(sample(), EventInfo("push", "Telegram", "Заголовок", "текст", 2000L))
        val m = Msg(fromUser = false, text = "сводка", events = items)
        assertEquals(2, m.events.size)
        assertEquals("sms", m.events[0].kind)
        assertEquals("Telegram", m.events[1].source)
        assertTrue(Msg(false, "x").events.isEmpty())
    }

    @Test
    fun mailCardShape() {
        // письмо — карточка события: kind=mail, источник — отправитель, заголовок — тема
        val mail = EventInfo("mail", "банк@yandex.ru", "Выписка за месяц", "Текст письма…", 1700000000000L)
        assertEquals("mail", mail.kind)
        assertEquals("банк@yandex.ru", mail.source)
        assertEquals("Выписка за месяц", mail.title)
        assertEquals("Текст письма…", mail.text)
        assertEquals("mail|банк@yandex.ru|1700000000000|lst3", eventListKey(mail, 3))
    }
}
