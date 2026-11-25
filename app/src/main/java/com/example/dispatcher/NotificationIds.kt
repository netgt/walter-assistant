package com.example.dispatcher

import org.json.JSONArray

/** Маленькое persistent-кольцо обработанных notification key+postTime, без дублей после reconnect. */
object NotificationIds {
    fun fromJson(raw: String?): Set<String> = runCatching {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
    }.getOrDefault(emptySet())

    fun add(raw: String?, id: String, limit: Int = 100): String {
        val ids = fromJson(raw).toMutableList()
        ids.remove(id)
        ids += id
        return JSONArray().apply { ids.takeLast(limit).forEach { put(it) } }.toString()
    }
}
