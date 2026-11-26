package com.example.dispatcher

import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Base64

/** Gmail API v1: чтение последних писем через OAuth, без IMAP-пароля. */
object GmailApi {
    private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
    private val http = OkHttpClient()

    private fun getJson(url: String, token: String): JSONObject {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Gmail API ${response.code}: ${text.take(250)}")
            }
            return JSONObject(text)
        }
    }

    fun fetchMessages(prefs: SharedPreferences, count: Int): List<MailStore.MailItem> {
        val token = GmailOAuth.validAccessToken(prefs)
            ?: throw IllegalStateException("Gmail OAuth не подключён")
        if (GmailOAuth.email(prefs).isBlank()) {
            runCatching {
                val profile = getJson("$BASE/profile", token)
                val raw = JSONObject(prefs.getString(Prefs.KEY_GMAIL_OAUTH, null) ?: "{}")
                raw.put("email", profile.optString("emailAddress"))
                prefs.edit().putString(Prefs.KEY_GMAIL_OAUTH, raw.toString()).apply()
            }
        }
        val list = getJson("$BASE/messages?maxResults=${count.coerceIn(1, 20)}", token)
        val messages = list.optJSONArray("messages") ?: return emptyList()
        return (0 until messages.length()).mapNotNull { i ->
            val id = messages.optJSONObject(i)?.optString("id").orEmpty()
            if (id.isBlank()) return@mapNotNull null
            fetchMessage(prefs, id)
        }
    }

    fun fetchMessage(prefs: SharedPreferences, id: String): MailStore.MailItem {
        val token = GmailOAuth.validAccessToken(prefs)
            ?: throw IllegalStateException("Gmail OAuth не подключён")
        val full = getJson("$BASE/messages/$id?format=full", token)
        val headers = full.optJSONObject("payload")?.optJSONArray("headers")
        fun header(name: String): String {
            if (headers == null) return ""
            return (0 until headers.length()).firstOrNull { j ->
                headers.optJSONObject(j)?.optString("name").equals(name, ignoreCase = true)
            }?.let { headers.optJSONObject(it)?.optString("value").orEmpty() } ?: ""
        }
        val at = full.optString("internalDate").toLongOrNull() ?: 0L
        val payload = full.optJSONObject("payload") ?: JSONObject()
        return MailStore.MailItem(
            from = header("From"),
            subject = header("Subject"),
            date = if (at > 0) java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at)) else "",
            body = payloadText(payload).take(500),
            at = at
        )
    }

    fun historyAddedIds(prefs: SharedPreferences, startHistoryId: String): List<String> {
        val token = GmailOAuth.validAccessToken(prefs)
            ?: throw IllegalStateException("Gmail OAuth не подключён")
        val history = getJson(
            "$BASE/history?startHistoryId=$startHistoryId&historyTypes=messageAdded",
            token
        )
        val entries = history.optJSONArray("history") ?: return emptyList()
        val ids = mutableSetOf<String>()
        for (i in 0 until entries.length()) {
            val added = entries.optJSONObject(i)?.optJSONArray("messagesAdded") ?: continue
            for (j in 0 until added.length()) {
                added.optJSONObject(j)?.optJSONObject("message")?.optString("id")
                    ?.takeIf { it.isNotBlank() }?.let(ids::add)
            }
        }
        return ids.toList()
    }

    fun payloadText(payload: JSONObject): String {
        val mime = payload.optString("mimeType").lowercase()
        if (mime == "text/plain" || mime == "text/html") {
            val encoded = payload.optJSONObject("body")?.optString("data").orEmpty()
            if (encoded.isNotBlank()) {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
                }.getOrDefault("")
                return if (mime == "text/html") MailStore.htmlToText(decoded) else MailStore.cleanBody(decoded)
            }
        }
        val parts = payload.optJSONArray("parts") ?: return ""
        var html = ""
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val type = part.optString("mimeType").lowercase()
            val text = payloadText(part)
            if (text.isBlank()) continue
            if (type == "text/plain") return text
            if (html.isBlank()) html = text
        }
        return html
    }
}
