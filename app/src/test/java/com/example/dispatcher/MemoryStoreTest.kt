package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStoreTest {

    @Test
    fun appendCapsJournal() {
        var s = MemoryStore.empty()
        repeat(350) { i -> s = MemoryStore.append(s, "user", "msg $i", "main") }
        assertEquals(300, s.journal.size)
        assertEquals("msg 349", s.journal.last().text)
    }

    @Test
    fun addFactDeduplicates() {
        var s = MemoryStore.empty()
        s = MemoryStore.addFact(s, "жена — Маша")
        s = MemoryStore.addFact(s, "жена — Маша")
        assertEquals(1, s.facts.size)
    }

    @Test
    fun removeFactMatchesCaseInsensitive() {
        var s = MemoryStore.empty()
        s = MemoryStore.addFact(s, "мой паспорт: 4512 123456")
        s = MemoryStore.removeFact(s, "паспорт")
        assertTrue(s.facts.isEmpty())
    }

    @Test
    fun blockContainsFactsAndJournal() {
        var s = MemoryStore.empty()
        s = MemoryStore.addFact(s, "люблю фильмы Нолана")
        s = MemoryStore.append(s, "user", "напомни про стрижку", "main")
        val blk = MemoryStore.buildMemoryBlock(s, emptyMap())
        assertTrue(blk.contains("люблю фильмы Нолана"))
        assertTrue(blk.contains("напомни про стрижку"))
    }

    @Test
    fun blockIncludesThreadDigest() {
        var s = MemoryStore.empty()
        val thread = listOf(
            Msg(true, "что за сумма?"),
            Msg(false, "расход 500 ₽"),
            Msg(true, "запомни про неё")
        )
        val blk = MemoryStore.buildMemoryBlock(s, mapOf("sms|Т-Банк|123" to thread))
        assertTrue(blk.contains("Т-Банк"))
        assertTrue(blk.contains("что за сумма?"))
        assertTrue(blk.contains("запомни про неё"))
    }

    @Test
    fun jsonRoundtrip() {
        var s = MemoryStore.empty()
        s = MemoryStore.addFact(s, "факт")
        s = MemoryStore.append(s, "agent", "ответ", "main")
        val back = MemoryStore.fromJson(MemoryStore.toJson(s))
        assertEquals(s.facts, back.facts)
        assertEquals(s.journal, back.journal)
    }

}
