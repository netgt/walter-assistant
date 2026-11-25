package com.example.dispatcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/** Событие журнала оповещений: данные канала для оценки правил. */
data class WatchEvent(val kind: String, val source: String, val text: String, val at: Long)

/**
 * Движок оповещений Walter.
 * SMS и пуши оцениваются сразу; остальные каналы — периодически из открытого приложения.
 */
object WatchEngine {

    const val KEY_LOG = "watch_log"
    const val KEY_DISABLED = "watch_disabled"
    const val KEY_FIRED = "watch_fired"
    const val MIN_INTERVAL_MS = 15L * 60_000
    const val MAX_LOG = 60
    const val LOG_TTL_MS = 24L * 3600_000

    const val SYSTEM_PROMPT =
        "Ты — движок оповещений Walter. Проверяешь правила оповещений по переданным данным каналов. " +
            "Ничего не выдумывай: срабатывай только если данные канала явно подходят под правило. " +
            "Отвечай СТРОГО одним JSON без текста вне него: " +
            "{\"fire\":true,\"text\":\"...\",\"prompt\":\"...\"} или {\"fire\":false}. " +
            "text — короткое сообщение пользователю о событии; prompt — короткий промпт-продолжение, " +
            "который Walter может выполнить по нажатию пользователя (что сделать дальше). " +
            "Если правило требует хранить состояние в файле, доступны действия read_file, write_file и create_file " +
            "(JSON с action/path/new/query). Текущее состояние приложено к событию; сравни его с новыми данными, " +
            "если файла нет — создай через create_file. При новом срабатывании обнови файл через write_file и только затем верни fire. " +
            "Новый, ещё не пройденный порог нельзя пропускать: сначала write_file, затем fire=true. Не дублируй уже пройденные пороги."

    const val KEY_SNOOZE = "watch_snooze"
    const val KEY_PULLED = "watch_pulled"
    private val stateLocks = ConcurrentHashMap<Long, Mutex>()

    fun markPull(prefs: SharedPreferences, alertId: Long, at: Long) {
        val o = JSONObject(prefs.getString(KEY_PULLED, null) ?: "{}")
        o.put(alertId.toString(), at)
        prefs.edit().putString(KEY_PULLED, o.toString()).apply()
    }

    fun lastPull(prefs: SharedPreferences, alertId: Long): Long {
        val o = runCatching {
            JSONObject(prefs.getString(KEY_PULLED, null) ?: "{}")
        }.getOrNull() ?: return 0L
        return o.optLong(alertId.toString(), 0L)
    }

    fun snooze(prefs: SharedPreferences, alertId: Long, hours: Int) {
        val o = JSONObject(prefs.getString(KEY_SNOOZE, null) ?: "{}")
        o.put(alertId.toString(), System.currentTimeMillis() + hours * 3600_000L)
        prefs.edit().putString(KEY_SNOOZE, o.toString()).apply()
    }

    fun snoozed(prefs: SharedPreferences, alertId: Long): Boolean {
        val o = runCatching {
            JSONObject(prefs.getString(KEY_SNOOZE, null) ?: "{}")
        }.getOrNull() ?: return false
        val until = o.optLong(alertId.toString(), 0L)
        return System.currentTimeMillis() < until
    }

    /** Подходит ли событие под настроенный канал оповещения. */
    fun matchesChannel(alert: AlertDef, eventKind: String): Boolean {
        if (alert.watchChannel == "any") return true
        if (alert.watchChannel == "tool") return false
        if (alert.watchChannel == eventKind) return true
        val want = when (eventKind) {
            "sms" -> "read_sms"
            "push" -> "read_pushes"
            else -> null
        } ?: return false
        return alert.tools.split(Regex("[,;]")).map { it.trim() }.any { it == want }
    }

    fun ruleOf(alert: AlertDef): String = alert.watchRule.ifBlank { alert.instructions }

    fun logToJson(events: List<WatchEvent>): String = JSONArray().apply {
        events.forEach { e ->
            put(JSONObject().put("k", e.kind).put("s", e.source).put("t", e.text).put("at", e.at))
        }
    }.toString()

    fun logFromJson(raw: String?): List<WatchEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WatchEvent(o.optString("k"), o.optString("s"), o.optString("t"), o.optLong("at"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun append(events: List<WatchEvent>, e: WatchEvent): List<WatchEvent> =
        (events + e).takeLast(MAX_LOG)

    fun disabledToJson(ids: Set<Long>): String = JSONArray().apply { ids.forEach { put(it) } }.toString()

    fun disabledFromJson(raw: String?): Set<Long> {
        if (raw.isNullOrBlank()) return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optLong(it).takeIf { id -> id != 0L } }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun isEnabled(alertId: Long, disabled: Set<Long>): Boolean = alertId !in disabled

    fun buildToolText(alert: AlertDef, toolOutput: String): String =
        "ПРАВИЛО СЛЕДИЛКИ «${alert.name}»:\n${ruleOf(alert)}\n\n" +
            "ВЫВОД ИНСТРУМЕНТА ${alert.watchTool} (обрезка):\n${toolOutput.take(5000)}\n\n" +
            "Проверь, срабатывает ли правило. Отвечай СТРОГО одним JSON: {\"fire\":true,\"text\":\"...\"} " +
            "или {\"fire\":false}. Если сработало — text это короткое сообщение пользователю."

    fun parseFirePrompt(content: String): String? =
        Regex(""""prompt"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(content)?.groupValues?.get(1)
            ?.replace("\\\\", "\\")?.replace("\\\"", "\"")?.replace("\\n", "\n")

    fun parseFire(content: String): Pair<Boolean, String?> {
        val t = content.trim()
        val fire = Regex(""""fire"\s*:\s*(true|false)""").find(t)?.groupValues?.get(1) == "true"
        val text = Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(t)?.groupValues?.get(1)
            ?.replace("\\\\", "\\")?.replace("\\\"", "\"")?.replace("\\n", "\n")
        return fire to text
    }

    fun buildUserText(alert: AlertDef, events: List<WatchEvent>): String {
        val data = if (events.isEmpty()) "(данных канала пока нет)"
        else events.joinToString("\n") { e ->
            val head = if (e.kind == "sms") "SMS от «${e.source}»" else "Пуш из «${e.source}»"
            "$head [event_at=${e.at}]: ${e.text.take(300)}"
        }
        val stateHint = if (usesFileState(alert)) {
            "\nФАЙЛ СОСТОЯНИЯ: ${stateRelativePath(alert)}. Его текущее содержимое приложено ниже. " +
                "Если файла нет — подготовь ПОЛНЫЙ JSON и вызови create_file; если есть — используй его. " +
                "Храни пройденные пороги в passed_thresholds, последнее значение — в last_value, обработанные event_at — в processed_events. " +
                "После нового порога вызови write_file с ВСЕМ новым JSON, не удаляя прежнюю историю. " +
                "fire=true разрешён только после успешного создания/обновления файла состояния.\n"
        } else ""
        return "ПРАВИЛО СЛЕДИЛКИ «${alert.name}»:\n${ruleOf(alert)}\n$stateHint\n" +
            "ДАННЫЕ КАНАЛА:\n$data\n\n" +
            "Проверь, срабатывает ли правило. Отвечай СТРОГО одним JSON: {\"fire\":true,\"text\":\"...\"} " +
            "или {\"fire\":false}. Если сработало — text это короткое сообщение пользователю " +
            "(что случилось и какие данные об этом говорят)."
    }

    /** Правила, которым нужен персистентный файл состояния (пороги, прогресс, история срабатываний). */
    fun usesFileState(alert: AlertDef): Boolean =
        Regex("(?i)файл|записывать|сверяться|запоминать.*порог|пройденные пороги").containsMatchIn(ruleOf(alert))

    fun stateRelativePath(alert: AlertDef): String = "Download/walter-alerts/alert-${alert.id}.json"

    /** Доступ к файлам проверяется до цикла; начальный JSON готовит и создаёт модель. */
    private fun hasStateAccess(): Boolean = FileStore.hasAllFilesAccess()

    private fun absolutePath(path: String): String =
        if (java.io.File(path).isAbsolute) path else java.io.File(FileStore.storageRoot(), path).absolutePath

    /** Только state-файл конкретного алерта; содержимое всегда готовит модель. */
    private fun executeFileAction(alert: AlertDef, action: IntendedAction): String {
        if (!FileStore.hasAllFilesAccess()) return "Нет доступа «Все файлы» — включи его в настройках Walter."
        val expected = absolutePath(stateRelativePath(alert))
        val actual = absolutePath(action.path ?: expected)
        if (actual != expected) return "Запрещено: для этого алерта доступен только $expected"
        val file = java.io.File(expected)
        if (file.exists() && file.length() > 30_000) {
            return "Файл состояния больше 30000 байт: чтение и запись остановлены, чтобы не потерять данные."
        }
        return when (action.action) {
            "read_file" -> {
                if (!file.exists()) return "Файл не найден: $expected — подготовь полный JSON и вызови create_file."
                val text = FileStore.readText(expected, 30_000)
                if (text.isBlank()) "Файл пуст или не читается: $expected"
                else "СОДЕРЖИМОЕ ФАЙЛА $expected:\n$text"
            }
            "write_file" -> {
                if (!file.exists()) "Файл не найден: сначала create_file с полным JSON."
                else FileStore.writeText(expected, action.new ?: "")
            }
            "create_file" -> FileStore.createText(expected, action.new ?: "")
            else -> "В фоновом алерте разрешены только read_file, write_file и create_file для $expected"
        }
    }

    /** Модельный цикл алерта с файловым состоянием; максимум 6 действий на одно событие. */
    private suspend fun evaluateWithFileState(
        alert: AlertDef,
        event: WatchEvent,
        key: String,
        baseUrl: String,
        client: OkHttpClient
    ): Pair<String?, String?> {
        val initialState = executeFileAction(alert, IntendedAction(
            action = "read_file", app = null, title = null, whenStr = null, address = null, reply = null,
            path = stateRelativePath(alert)))
        val msgs = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content",
                buildUserText(alert, listOf(event)) + "\n\nТЕКУЩЕЕ СОСТОЯНИЕ (read_file):\n$initialState"))
        var stateChanged = false
        repeat(8) {
            val reply = ModelApi.rawMessages(msgs, key, baseUrl, client)
            DebugLog.log("watch-state", "${alert.name}: модель -> ${reply.take(140)}")
            msgs.put(JSONObject().put("role", "assistant").put("content", reply))
            val action = runCatching { parseAction(reply) }.getOrElse { extractAction(reply) }
            if (action != null && action.action in setOf("read_file", "write_file", "create_file")) {
                val result = executeFileAction(alert, action)
                if (result.startsWith("Файл записан") || result.startsWith("Файл создан")) stateChanged = true
                DebugLog.log("watch-state", "${alert.name}: ${action.action} -> ${result.take(100)}")
                msgs.put(JSONObject().put("role", "user").put("content", "ДАННЫЕ ИНСТРУМЕНТА ${action.action}:\n$result"))
            } else {
                val (fire, text) = parseFire(reply)
                val stateReady = java.io.File(absolutePath(stateRelativePath(alert))).let { it.exists() && it.length() > 0 }
                if (fire && (!stateReady || !stateChanged)) {
                    msgs.put(JSONObject().put("role", "user").put("content",
                        "Прежде чем fire=true, обнови файл состояния через write_file с ВСЕМ JSON: добавь новый порог, last_value и event_at. Без записи оповещение запрещено."))
                } else {
                    return (if (fire) text else null) to (if (fire) parseFirePrompt(reply) else null)
                }
            }
        }
        return null to null
    }

    fun logEvent(prefs: SharedPreferences, e: WatchEvent) {
        val now = System.currentTimeMillis()
        val cur = logFromJson(prefs.getString(KEY_LOG, null))
            .filter { now - it.at < LOG_TTL_MS }
        prefs.edit().putString(KEY_LOG, logToJson(append(cur, e))).apply()
    }

    fun recentEvents(prefs: SharedPreferences): List<WatchEvent> {
        val now = System.currentTimeMillis()
        return logFromJson(prefs.getString(KEY_LOG, null)).filter { now - it.at < LOG_TTL_MS }
    }

    fun lastFire(prefs: SharedPreferences, id: Long): Long {
        val raw = prefs.getString(KEY_FIRED, null) ?: return 0L
        return try {
            JSONObject(raw).optLong(id.toString(), 0L)
        } catch (e: Exception) {
            0L
        }
    }

    fun markFired(prefs: SharedPreferences, id: Long, at: Long) {
        val raw = prefs.getString(KEY_FIRED, null)
        val o = try {
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
        o.put(id.toString(), at)
        prefs.edit().putString(KEY_FIRED, o.toString()).apply()
    }

    /** Доставляет системное уведомление и карточку события в чат. */
    fun deliver(ctx: Context, alert: AlertDef, text: String, prompt: String? = null) {
        val p = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        logEvent(p, WatchEvent("push", "Walter: " + alert.name, text, System.currentTimeMillis()))
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel("walter_watch", "Умные алерты Walter", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val n = Notification.Builder(ctx, "walter_watch")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Walter: " + alert.name)
            .setContentText(text.take(200))
            .setStyle(Notification.BigTextStyle().bigText(text.take(500)))
            .setAutoCancel(true)
            .setColorized(true)
            .setColor(0xFF3B5F9E.toInt())
            .build()
        if (!LauncherMode.isDefaultHome(ctx)) {
            runCatching { nm.notify((alert.id.toInt()).xor(0x41A11), n) }
        } else {
            DebugLog.log("launcher-mode", "системный алерт показан только в чате: ${alert.name}")
        }
        EventHub.post(EventInfo("watch", alert.name, "", text, System.currentTimeMillis(), note = prompt), ctx)
        DebugLog.log("watch-fire", "пуш Walter: «${alert.name}» — ${text.take(80)}")
    }

    /** Оценивает все подходящие оповещения по одному новому событию. */
    suspend fun evaluateForEvent(
        prefs: SharedPreferences,
        client: OkHttpClient,
        alerts: List<AlertDef>,
        event: WatchEvent
    ): List<Triple<AlertDef, String, String?>> {
        val fired = mutableListOf<Triple<AlertDef, String, String?>>()
        val key = prefs.getString(Prefs.KEY_API, "")?.trim().orEmpty()
        if (key.isBlank()) return fired
        val baseUrl = prefs.getString(Prefs.KEY_BASE_URL, "https://api.deepseek.com/chat/completions")
            ?: "https://api.deepseek.com/chat/completions"
        val disabled = disabledFromJson(prefs.getString(KEY_DISABLED, null))
        val now = System.currentTimeMillis()
        for (alert in alerts) {
            if (!isEnabled(alert.id, disabled)) continue
            if (!matchesChannel(alert, event.kind)) continue
            if (snoozed(prefs, alert.id)) continue
            val stateful = usesFileState(alert)
            if (!stateful && now - lastFire(prefs, alert.id) < MIN_INTERVAL_MS) continue
            if (stateful && !hasStateAccess()) {
                DebugLog.log("watch-state", "${alert.name}: нет доступа к файлу состояния")
                continue
            }
            val reply = if (stateful) {
                stateLocks.getOrPut(alert.id) { Mutex() }.withLock {
                    runCatching { evaluateWithFileState(alert, event, key, baseUrl, client) }
                        .onFailure { DebugLog.log("watch-state", "${alert.name}: ошибка цикла: ${it.message ?: it.javaClass.simpleName}") }
                        .getOrNull()
                }
            } else {
                val raw = runCatching {
                    ModelApi.raw(SYSTEM_PROMPT, buildUserText(alert, listOf(event)), key, baseUrl, client)
                }.getOrNull()
                val (fire, text) = raw?.let { parseFire(it) } ?: (false to null)
                (if (fire) text else null) to (if (fire) raw?.let { parseFirePrompt(it) } else null)
            }
            val text = reply?.first
            if (!text.isNullOrBlank()) {
                markFired(prefs, alert.id, now)
                fired += Triple(alert, text, reply.second)
            }
        }
        return fired
    }
}
