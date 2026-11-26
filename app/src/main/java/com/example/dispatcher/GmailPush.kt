package com.example.dispatcher

import android.content.SharedPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Gmail users.watch + Pub/Sub bridge: bridge отдаёт historyId, письма читаются через Gmail API. */
object GmailPush {
    data class Result(val items: List<MailStore.MailItem>, val note: String)

    private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
    private val http = OkHttpClient()

    private fun configError(prefs: SharedPreferences): String? {
        if (!GmailOAuth.isConnected(prefs)) return "Сначала подключи Gmail OAuth в настройках."
        if (prefs.getString(Prefs.KEY_GMAIL_PUBSUB_TOPIC, "").isNullOrBlank()) {
            return "Укажи Pub/Sub topic в настройках Gmail push."
        }
        if (prefs.getString(Prefs.KEY_GMAIL_BRIDGE_URL, "").isNullOrBlank()) {
            return "Укажи URL Gmail push bridge в настройках."
        }
        if (prefs.getString(Prefs.KEY_GMAIL_BRIDGE_TOKEN, "").isNullOrBlank()) {
            return "Укажи секрет Gmail push bridge в настройках."
        }
        return null
    }

    fun registerWatch(prefs: SharedPreferences): Result {
        configError(prefs)?.let { return Result(emptyList(), it) }
        val token = GmailOAuth.validAccessToken(prefs)
            ?: return Result(emptyList(), "Gmail OAuth не подключён")
        val topic = prefs.getString(Prefs.KEY_GMAIL_PUBSUB_TOPIC, "")!!.trim()
        val body = JSONObject().put("topicName", topic).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$BASE/watch").post(body)
            .header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return Result(emptyList(), "users.watch ${response.code}: ${text.take(250)}")
            }
            val json = JSONObject(text)
            val state = JSONObject()
                .put("historyId", json.optString("historyId"))
                .put("expiration", json.optString("expiration"))
            prefs.edit().putString(Prefs.KEY_GMAIL_WATCH, state.toString()).apply()
            return Result(emptyList(), "Gmail watch зарегистрирован, historyId=${json.optString("historyId")}, expiration=${json.optString("expiration")}")
        }
    }

    private fun bridgeEvents(prefs: SharedPreferences, after: String): List<Long> {
        val base = prefs.getString(Prefs.KEY_GMAIL_BRIDGE_URL, "")!!.trim().trimEnd('/')
        val token = prefs.getString(Prefs.KEY_GMAIL_BRIDGE_TOKEN, "")!!.trim()
        val url = (base + "/gmail/events").toHttpUrl().newBuilder()
            .addQueryParameter("email", GmailOAuth.email(prefs))
            .addQueryParameter("after", after)
            .build()
        val request = Request.Builder().url(url).header("X-Bridge-Token", token).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("bridge ${response.code}: ${text.take(200)}")
            }
            val events = JSONObject(text).optJSONArray("events") ?: return emptyList()
            return (0 until events.length()).mapNotNull { events.optString(it).toLongOrNull() }
        }
    }

    fun check(prefs: SharedPreferences): Result {
        configError(prefs)?.let { return Result(emptyList(), it) }
        val raw = prefs.getString(Prefs.KEY_GMAIL_WATCH, null)
        if (raw == null) return registerWatch(prefs)
        val state = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val expiration = state.optString("expiration").toLongOrNull() ?: 0L
        if (expiration > 0 && expiration < System.currentTimeMillis()) return registerWatch(prefs)
        val historyId = state.optString("historyId")
        if (historyId.isBlank()) return registerWatch(prefs)
        return runCatching {
            val events = bridgeEvents(prefs, historyId)
            if (events.isEmpty()) return@runCatching Result(emptyList(), "Новых писем по Gmail push нет.")
            val ids = GmailApi.historyAddedIds(prefs, historyId)
            val latest = events.maxOrNull() ?: events.last()
            prefs.edit().putString(Prefs.KEY_GMAIL_WATCH,
                state.put("historyId", latest.toString()).toString()).apply()
            val items = ids.map { GmailApi.fetchMessage(prefs, it) }
            Result(items, "Gmail push: новых писем ${items.size}, historyId=$latest")
        }.getOrElse { e ->
            Result(emptyList(), "Gmail push: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
