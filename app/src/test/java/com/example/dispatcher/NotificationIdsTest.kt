package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIdsTest {
    @Test
    fun idsAreDeduplicatedAndLimited() {
        var raw: String? = null
        repeat(105) { raw = NotificationIds.add(raw, "pkg|$it") }
        val ids = NotificationIds.fromJson(raw)
        assertEquals(100, ids.size)
        assertTrue("pkg|104" in ids)
        assertTrue("pkg|4" !in ids)
        raw = NotificationIds.add(raw, "pkg|104")
        assertEquals(100, NotificationIds.fromJson(raw).size)
    }
}
