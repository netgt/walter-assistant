package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySearchTest {
    @Test
    fun searchesAllMainMessagesAndThreadsNotOnlyContextTail() {
        val main = listOf(
            Msg(true, "прочитай TODO-3 и добавь последнюю строку в начало"),
            Msg(false, "Готово", toolsChain = listOf("write_file")),
            Msg(true, "погода на завтра")
        )
        val threads = mapOf(
            "push|MAX|123" to listOf(
                Msg(true, "что было в пуше?"),
                Msg(false, "Там была ссылка на отчёт")
            )
        )
        val hits = HistorySearch.search(main, threads, "TODO-3")
        assertEquals(1, hits.size)
        assertEquals(1, hits.single().index)
        assertTrue(hits.single().text.contains("последнюю строку"))
        val threadHits = HistorySearch.search(main, threads, "отчёт")
        assertEquals("Событие push|MAX|123", threadHits.single().source)
    }

    @Test
    fun formatReportsScannedFullHistoryAndEmptyResult() {
        val main = (1..30).map { Msg(true, "обычное сообщение $it") }
        val formatted = HistorySearch.format(main, emptyMap(), "редкий_термин")
        assertTrue(formatted.contains("Проверено сообщений: 30"))
        assertTrue(formatted.contains("Лексических совпадений"))
    }
}
