package com.example.dispatcher

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushVisibilityTest {
    @Test
    fun launcherSuppressesAllOrdinaryAndIgnoresAllowlist() {
        assertTrue(PushVisibility.shouldSuppress(true, false, setOf("app.selected"), "app.other", 0))
        assertTrue(PushVisibility.shouldSuppress(true, true, setOf("app.selected"), "app.selected", 0))
    }

    @Test
    fun regularModeUsesAllowlistAndNeverSuppressesPersistent() {
        assertFalse(PushVisibility.shouldSuppress(false, false, emptySet(), "app.other", 0))
        assertFalse(PushVisibility.shouldSuppress(false, true, setOf("app.selected"), "app.selected", 0))
        assertTrue(PushVisibility.shouldSuppress(false, true, setOf("app.selected"), "app.other", 0))
        assertFalse(PushVisibility.shouldSuppress(false, true, emptySet(), "app.other", Notification.FLAG_ONGOING_EVENT))
        assertFalse(PushVisibility.shouldSuppress(true, false, emptySet(), "app.other", Notification.FLAG_FOREGROUND_SERVICE))
        assertFalse(PushVisibility.shouldSuppress(false, true, emptySet(), "app.other", Notification.FLAG_NO_CLEAR))
    }
}
