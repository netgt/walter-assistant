package com.example.dispatcher

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Диагностический лог модели. Пишется в Download/walter_debug.log —
 * пользователь может открыть его и прислать содержимое для диагностики.
 */
object DebugLog {
    private const val MAX_SIZE = 300_000L
    private val df = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(tag: String, msg: String) {
        try {
            val f = File(Environment.getExternalStorageDirectory(), "Download/walter_debug.log")
            if (f.exists() && f.length() > MAX_SIZE) f.delete()
            val line = "[${df.format(Date())}] $tag: ${msg.replace("\n", " ").take(400)}\n"
            f.appendText(line)
        } catch (e: Exception) { /* логирование не критично */ }
    }
}
