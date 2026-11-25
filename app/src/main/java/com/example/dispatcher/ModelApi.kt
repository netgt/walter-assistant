package com.example.dispatcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Общий вызов модели без стриминга — для фоновых задач (движок следилок и др.).
 * Возвращает content ответа; ошибки сети/HTTP — исключение.
 */
object ModelApi {

    const val MODEL = "deepseek-chat"

    suspend fun raw(system: String, user: String, key: String, baseUrl: String, client: OkHttpClient): String {
        val msgs = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        return rawMessages(msgs, key, baseUrl, client)
    }

    /** Многоходовый вызов для фонового tool-loop: история передаётся целиком. */
    suspend fun rawMessages(msgs: JSONArray, key: String, baseUrl: String, client: OkHttpClient): String {
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", msgs)
            .put("stream", false)
            .toString()
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(baseUrl)
                .header("Authorization", "Bearer $key")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            resp.use { r ->
                if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
                val o = JSONObject(r.body?.string() ?: "{}")
                o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
            }
        }
    }
}
