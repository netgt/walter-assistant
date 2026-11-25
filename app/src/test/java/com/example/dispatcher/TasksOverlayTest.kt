package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksOverlayTest {

    @Test
    fun finishLabels() {
        assertEquals("Готово: каналы", taskFinishLabel("Читаю каналы…"))
        assertEquals("Готово: поиск", taskFinishLabel("Ищу в Яндексе…"))
        assertEquals("Готово", taskFinishLabel("Думаю…"))
        assertTrue(taskFinishLabel("Готовлю ответ").startsWith("Готово"))
    }

    @Test
    fun splitToolsMarker() {
        val (text, tools) = splitTools("Думаю…[[tools:open_app,fetch_url]]")
        assertEquals("Думаю…", text)
        assertEquals(listOf("open_app", "fetch_url"), tools)
        val (plain, none) = splitTools("обычный ответ без инструментов")
        assertEquals("обычный ответ без инструментов", plain)
        assertTrue(none.isEmpty())
    }

    @Test
    fun taskEntriesRoundtrip() {
        val list = listOf(TaskEntry(11, "Чтение новостей", 3, 2, 3, false, true), TaskEntry(12, "Почта", 7, 3, 3, true, false))
        val back = taskEntriesFromJson(taskEntriesToJson(list))
        assertEquals(2, back.size)
        assertEquals(11L, back[0].uid)
        assertEquals("Чтение новостей", back[0].name)
        assertEquals(2, back[0].step)
        assertEquals(true, back[1].done)
        // running при восстановлении всегда false
        assertEquals(false, back[0].running)
    }
}
