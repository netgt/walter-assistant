package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityUsageTest {
    @Test
    fun launchStatsAccumulateAndMergeWithoutDoubleCounting() {
        var raw: String? = null
        raw = CapabilityUsage.recordJson(raw, "app.one", 100L)
        raw = CapabilityUsage.recordJson(raw, "app.one", 200L)
        raw = CapabilityUsage.recordJson(raw, "app.two", 300L)
        assertEquals(mapOf("app.one" to 2, "app.two" to 1), CapabilityUsage.localCounts(raw))
        val merged = CapabilityUsage.mergeCounts(
            mapOf("app.one" to 5, "app.two" to 1),
            CapabilityUsage.localCounts(raw)
        )
        assertEquals(5, merged["app.one"])
        assertEquals(1, merged["app.two"])
    }
}
