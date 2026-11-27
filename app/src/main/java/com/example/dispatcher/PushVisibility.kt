package com.example.dispatcher

import android.app.Notification
import org.json.JSONArray

/** Видимость системных пушей: лончер скрывает все обычные, обычный режим — allowlist. */
object PushVisibility {
    fun isPersistent(flags: Int): Boolean =
        flags and Notification.FLAG_ONGOING_EVENT != 0 ||
            flags and Notification.FLAG_FOREGROUND_SERVICE != 0 ||
            flags and Notification.FLAG_NO_CLEAR != 0

    fun shouldSuppress(
        isHome: Boolean,
        settingEnabled: Boolean,
        allowedPackages: Set<String>,
        packageName: String,
        flags: Int
    ): Boolean {
        if (isPersistent(flags)) return false
        if (isHome) return true
        if (!settingEnabled) return false
        return packageName !in allowedPackages
    }

    fun allowedFromJson(raw: String?): Set<String> = runCatching {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
    }.getOrDefault(emptySet())

    fun allowedToJson(packages: Set<String>): String =
        JSONArray().apply { packages.sorted().forEach { put(it) } }.toString()
}
