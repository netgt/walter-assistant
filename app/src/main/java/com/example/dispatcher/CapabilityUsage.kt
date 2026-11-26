package com.example.dispatcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Локальная и системная статистика запусков для обучения capability-роутера. */
object CapabilityUsage {
    private const val KEY_LAUNCH_STATS = "capability_launch_stats"

    fun recordLaunch(prefs: SharedPreferences, packageName: String, at: Long = System.currentTimeMillis()) {
        if (packageName.isBlank()) return
        val raw = prefs.getString(KEY_LAUNCH_STATS, null)
        prefs.edit().putString(KEY_LAUNCH_STATS, recordJson(raw, packageName, at)).apply()
    }

    fun recordJson(raw: String?, packageName: String, at: Long): String {
        val root = runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())
        val item = root.optJSONObject(packageName) ?: JSONObject()
        item.put("count", item.optInt("count", 0) + 1)
        item.put("lastAt", at)
        root.put(packageName, item)
        return root.toString()
    }

    fun localCounts(raw: String?): Map<String, Int> {
        val root = runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())
        return buildMap {
            root.keys().forEach { pkg ->
                val count = root.optJSONObject(pkg)?.optInt("count", 0) ?: 0
                if (count > 0) put(pkg, count)
            }
        }
    }

    fun localCounts(prefs: SharedPreferences): Map<String, Int> =
        localCounts(prefs.getString(KEY_LAUNCH_STATS, null))

    fun hasSystemAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return runCatching {
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                ctx.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    /** Число переходов приложений на передний план за последние дни. */
    fun systemCounts(ctx: Context, days: Int = 30): Map<String, Int> {
        if (!hasSystemAccess(ctx)) return emptyMap()
        val manager = ctx.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val start = end - days * 24L * 3600_000L
        val out = mutableMapOf<String, Int>()
        runCatching {
            val events = manager.queryEvents(start, end)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    val pkg = event.packageName ?: continue
                    out[pkg] = (out[pkg] ?: 0) + 1
                }
            }
        }
        return out
    }

    /** Система уже учитывает запуски через Walter, поэтому берём max, а не сумму. */
    fun mergeCounts(system: Map<String, Int>, local: Map<String, Int>): Map<String, Int> =
        (system.keys + local.keys).associateWith { pkg -> maxOf(system[pkg] ?: 0, local[pkg] ?: 0) }

    fun counts(ctx: Context, prefs: SharedPreferences): Map<String, Int> =
        mergeCounts(systemCounts(ctx), localCounts(prefs))
}
