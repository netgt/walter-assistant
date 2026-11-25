package com.example.dispatcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Шина входящих событий (SMS/пуши) + очередь на случай, если чат закрыт. */
object EventHub {

    @Volatile
    private var handler: ((EventInfo) -> Unit)? = null

    fun attach(h: ((EventInfo) -> Unit)?) {
        handler = h
    }

    /** Если чат жив — доставляем сразу, иначе кладём в очередь. */
    fun post(e: EventInfo, ctx: Context) {
        val h = handler
        if (h != null) {
            h(e)
        } else {
            queue(ctx, e)
        }
    }

    /** Забирает события, накопившиеся пока чат был закрыт. */
    fun drain(ctx: Context): List<EventInfo> {
        val prefs = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(Prefs.KEY_PENDING, null) ?: return emptyList()
        val out = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                EventInfo(
                    o.optString("k", "push"),
                    o.optString("s", ""),
                    o.optString("ti", ""),
                    o.optString("tx", ""),
                    o.optLong("at", System.currentTimeMillis()),
                    o.optString("n", "").takeIf { it.isNotBlank() },
                    o.optString("pi", "").takeIf { it.isNotBlank() },
                    o.optString("pkg", "").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        prefs.edit().remove(Prefs.KEY_PENDING).apply()
        return out
    }

    private fun queue(ctx: Context, e: EventInfo) {
        val prefs = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(Prefs.KEY_PENDING, null)
        val arr = try {
            if (raw != null) JSONArray(raw) else JSONArray()
        } catch (ex: Exception) {
            JSONArray()
        }
        val o = JSONObject()
            .put("k", e.kind).put("s", e.source).put("ti", e.title)
            .put("tx", e.text).put("at", e.at)
        e.note?.let { o.put("n", it) }
        e.pi?.let { o.put("pi", it) }
        e.pkg?.let { o.put("pkg", it) }
        arr.put(o)
        prefs.edit().putString(Prefs.KEY_PENDING, arr.toString()).apply()
    }
}
