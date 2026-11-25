package com.example.dispatcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherModeTest {
    @Test
    fun defaultHomeComponentIsMatchedByExactPackage() {
        assertTrue(LauncherMode.ownsHomeComponent("com.example.dispatcher", "com.example.dispatcher/.MainActivity"))
        assertFalse(LauncherMode.ownsHomeComponent("com.example.dispatcher", "com.other.launcher/.Home"))
        assertFalse(LauncherMode.ownsHomeComponent("com.example.dispatcher", null))
        assertFalse(LauncherMode.ownsHomeComponent("com.example.dispatcher", "com.example.dispatcher2/.MainActivity"))
    }
}
