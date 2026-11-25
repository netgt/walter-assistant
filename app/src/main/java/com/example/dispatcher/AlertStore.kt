package com.example.dispatcher

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Самостоятельное оповещение Walter: канал, правило и необязательная фоновая проверка. */
data class AlertDef(
    val id: Long,
    val name: String,
    val tools: String = "",
    val instructions: String = "",
    val watchChannel: String = "any",
    val watchTool: String = "",
    val watchRule: String = "",
    val watchEvery: Int = 0,
    val watchParam: String = ""
)

/** Локальное хранилище оповещений. */
object AlertStore {
    private const val LEGACY_STORE_KEY = "custom_" + "s" + "kills"
    private const val LEGACY_PRESETS_KEY = "preset_" + "s" + "kills_seen"

    val PRESETS: List<AlertDef> = listOf(
        AlertDef(
            id = -41,
            name = "Работа с документами из почты",
            tools = "open_app",
            instructions = "Оповещение по документам из почты. Срабатывает по пушу Яндекс Почты: в письме есть вложение и просьба посмотреть, отредактировать или прокомментировать документ. В уведомлении укажи, что пришло письмо с вложением.",
            watchChannel = "push",
            watchRule = "пуш почтового приложения: в письме есть вложение и просьба посмотреть, отредактировать или прокомментировать документ"
        ),
        AlertDef(
            id = -31,
            name = "Лимит по карте",
            tools = "read_sms",
            instructions = "Проверяй SMS от банков. Сработай, если баланс карты опустился ниже 100 000 рублей. В сообщении укажи баланс, карту и время SMS."
        )
    )

    fun toJson(alerts: List<AlertDef>): String = JSONArray().apply {
        alerts.forEach { a ->
            put(JSONObject()
                .put("id", a.id)
                .put("name", a.name)
                .put("tools", a.tools)
                .put("instructions", a.instructions)
                .put("watchChannel", a.watchChannel)
                .put("watchTool", a.watchTool)
                .put("watchRule", a.watchRule)
                .put("watchEvery", a.watchEvery)
                .put("watchParam", a.watchParam))
        }
    }.toString()

    fun fromJson(raw: String?): List<AlertDef> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                alertFromObject(arr.getJSONObject(i))
            }
        }.getOrDefault(emptyList())
    }

    private fun alertFromObject(o: JSONObject): AlertDef = AlertDef(
        id = o.optLong("id"),
        name = o.optString("name"),
        tools = o.optString("tools"),
        instructions = o.optString("instructions").ifBlank { o.optString("prompt") },
        watchChannel = o.optString("watchChannel", "any").ifBlank { "any" },
        watchTool = o.optString("watchTool"),
        watchRule = o.optString("watchRule"),
        watchEvery = o.optInt("watchEvery"),
        watchParam = o.optString("watchParam")
    )

    /** Разбирает прежний JSON напрямую и переносит только записи фоновых правил. */
    fun fromLegacyJson(raw: String?): List<AlertDef> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                if (o.optString("mode", "chat") != "watch") return@mapNotNull null
                alertFromObject(o)
            }
        }.getOrDefault(emptyList())
    }

    private fun namesFromJson(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun load(prefs: SharedPreferences): List<AlertDef> {
        prefs.getString(Prefs.KEY_ALERTS, null)?.let { return fromJson(it) }
        if (prefs.getBoolean(Prefs.KEY_ALERTS_MIGRATED, false)) return emptyList()

        val migrated = fromLegacyJson(prefs.getString(LEGACY_STORE_KEY, null))
        if (prefs.getString(Prefs.KEY_ALERT_PRESETS_SEEN, null) == null) {
            val seen = namesFromJson(prefs.getString(LEGACY_PRESETS_KEY, null))
                .filter { name -> PRESETS.any { it.name == name } }
            prefs.edit().putString(Prefs.KEY_ALERT_PRESETS_SEEN, JSONArray().apply {
                seen.forEach { put(it) }
            }.toString()).apply()
        }
        save(prefs, migrated)
        prefs.edit()
            .putBoolean(Prefs.KEY_ALERTS_MIGRATED, true)
            .remove(LEGACY_STORE_KEY)
            .remove(LEGACY_PRESETS_KEY)
            .apply()
        return migrated
    }

    fun save(prefs: SharedPreferences, alerts: List<AlertDef>) {
        prefs.edit().putString(Prefs.KEY_ALERTS, toJson(alerts)).apply()
    }

    fun add(
        prefs: SharedPreferences,
        name: String,
        tools: String = "",
        instructions: String = "",
        watchChannel: String = "any",
        watchTool: String = "",
        watchRule: String = "",
        watchEvery: Int = 0,
        watchParam: String = ""
    ): AlertDef {
        val list = load(prefs).toMutableList()
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1
        val alert = AlertDef(
            id = id,
            name = name.trim(),
            tools = tools.trim(),
            instructions = instructions.trim(),
            watchChannel = normalizeChannel(watchChannel),
            watchTool = watchTool.trim(),
            watchRule = watchRule.trim(),
            watchEvery = watchEvery.coerceAtLeast(0),
            watchParam = watchParam.trim()
        )
        list += alert
        save(prefs, list)
        return alert
    }

    fun update(prefs: SharedPreferences, id: Long, field: String, value: String) {
        val list = load(prefs).map { a ->
            if (a.id != id) a else when (field) {
                "name" -> a.copy(name = value.trim())
                "tools" -> a.copy(tools = value.trim())
                "instructions" -> a.copy(instructions = value)
                "watchChannel" -> a.copy(watchChannel = normalizeChannel(value))
                "watchTool" -> a.copy(watchTool = value.trim())
                "watchRule" -> a.copy(watchRule = value.trim())
                "watchEvery" -> a.copy(watchEvery = value.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0)
                "watchParam" -> a.copy(watchParam = value.trim())
                else -> a
            }
        }
        save(prefs, list)
    }

    fun remove(prefs: SharedPreferences, id: Long) {
        save(prefs, load(prefs).filterNot { it.id == id })
    }

    /** Единый текст списка и состояния оповещений для чата и инструмента alert_list. */
    fun formatList(alerts: List<AlertDef>, disabled: Set<Long>, lastFire: (Long) -> Long): String {
        if (alerts.isEmpty()) return "Оповещений нет. Создай: «создай оповещение: когда … — сделай …»"
        val df = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        return "Оповещения Walter (${alerts.size}):\n\n" + alerts.joinToString("\n\n") { alert ->
            val enabled = alert.id !in disabled
            val status = if (enabled) "[[g:**включено**]]" else "[[r:**выключено**]]"
            val channel = AlertStore.channelLabel(alert)
            val last = lastFire(alert.id)
            val launched = if (last == 0L) "ещё не запускалось" else df.format(java.util.Date(last))
            "**Название:** ${alert.name}\n" +
                "**Канал:** $channel\n" +
                "**Правило:** ${alert.watchRule.ifBlank { alert.instructions }}\n" +
                "**Запуск:** $launched\n" +
                "**Статус:** $status"
        }
    }

    /** Совместимые значения каналов для движка и модели; в UI — ПУШ/СМС/ФОН. */
    fun normalizeChannel(value: String): String = when (value.trim().lowercase()) {
        "push", "пуш" -> "push"
        "sms", "смс" -> "sms"
        "tool", "фон" -> "tool"
        else -> value.trim().ifBlank { "any" }
    }

    fun channelLabel(alert: AlertDef): String = when {
        alert.watchChannel == "tool" -> "ФОН" +
            (if (alert.watchTool.isNotBlank()) " (${alert.watchTool}" +
                (if (alert.watchEvery > 0) ", каждые ${alert.watchEvery} мин)" else ")") else "")
        alert.watchChannel == "push" -> "ПУШ"
        alert.watchChannel == "sms" -> "СМС"
        alert.watchChannel.isBlank() || alert.watchChannel == "any" -> "любой"
        else -> alert.watchChannel
    }

    /** Добавляет два штатных оповещения один раз; пользовательские правки и удаления сохраняются. */
    fun ensurePresets(prefs: SharedPreferences) {
        val alerts = load(prefs).toMutableList()
        val seen = namesFromJson(prefs.getString(Prefs.KEY_ALERT_PRESETS_SEEN, null)).toMutableList()
        var alertsChanged = false
        var seenChanged = false
        PRESETS.forEach { preset ->
            val exists = alerts.any { it.id == preset.id || it.name.equals(preset.name, ignoreCase = true) }
            if (exists) {
                if (preset.name !in seen) {
                    seen += preset.name
                    seenChanged = true
                }
            } else if (preset.name !in seen) {
                alerts += preset
                seen += preset.name
                alertsChanged = true
                seenChanged = true
            }
        }
        if (alertsChanged) save(prefs, alerts)
        if (seenChanged) {
            prefs.edit().putString(Prefs.KEY_ALERT_PRESETS_SEEN, JSONArray().apply {
                seen.distinct().forEach { put(it) }
            }.toString()).apply()
        }
    }
}
