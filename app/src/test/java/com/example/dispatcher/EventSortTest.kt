package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Test

class EventSortTest {
    @Test
    fun latestKeepsAscendingOrderAndNewestLast() {
        val rows = listOf(
            Triple("B", "новое", 300L),
            Triple("A", "старое", 100L),
            Triple("C", "среднее", 200L)
        )
        val sorted = EventSort.latest(rows, 2) { it.third }
        assertEquals(listOf(200L, 300L), sorted.map { it.third })
        assertEquals(listOf("среднее", "новое"), sorted.map { it.second })
    }
}
