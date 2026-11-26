package com.example.dispatcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Шина входящих событий (SMS/пуши) + очередь на случай, если чат закрыт. */
object EventHub {

    @Volatile
    private var handler: ((EventInfo) -> Unit)? = null
    private var attachGeneration = 0L

    /** Возвращает токен подписки: старая активность не сможет отключить обработчик новой. */
    @Synchronized
    fun attach(h: (EventInfo) -> Unit): Long {
        attachGeneration++
        handler = h
        DebugLog.log("event-log", "attach #$attachGeneration")
        return attachGeneration
    }

    @Synchronized
    fun detach(token: Long) {
        if (token == attachGeneration) handler = null
        DebugLog.log("event-log", "detach #$token, текущий #$attachGeneration")
    }

    /** Долговечный журнал: пишем синхронно (commit), чтобы события пережили гибель процесса/активности. */
    private fun appendLog(ctx: Context, e: EventInfo) {
        val prefs = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(prefs.getString(Prefs.KEY_EVENT_LOG, null) ?: "[]")
        } catch (ex: Exception) {
            JSONArray()
        }
        arr.put(toJson(e))
        while (arr.length() > 200) arr.remove(0)
        val committed = prefs.edit().putString(Prefs.KEY_EVENT_LOG, arr.toString()).commit()
        if (!committed) DebugLog.log("event-log", "не удалось сохранить событие: ${e.kind} ${e.source}")
    }

    private fun toJson(e: EventInfo): JSONObject {
        val o = JSONObject()
            .put("k", e.kind).put("s", e.source).put("ti", e.title)
            .put("tx", e.text).put("at", e.at)
        e.note?.let { o.put("n", it) }
        e.pi?.let { o.put("pi", it) }
        e.pkg?.let { o.put("pkg", it) }
        return o
    }

    private fun fromJson(o: JSONObject): EventInfo = EventInfo(
        o.optString("k", "push"),
        o.optString("s", ""),
        o.optString("ti", ""),
        o.optString("tx", ""),
        o.optLong("at", System.currentTimeMillis()),
        o.optString("n", "").takeIf { it.isNotBlank() },
        o.optString("pi", "").takeIf { it.isNotBlank() },
        o.optString("pkg", "").takeIf { it.isNotBlank() }
    )

    /** Всегда пишем в долговечный журнал; если чат жив — доставляем сразу. */
    fun post(e: EventInfo, ctx: Context) {
        appendLog(ctx, e)
        handler?.invoke(e)
    }

    /** Читает долговечный журнал (не очищает): чат сам добавляет недостающее при старте/возврате. */
    fun drain(ctx: Context): List<EventInfo> {
        val prefs = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(Prefs.KEY_EVENT_LOG, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

}
