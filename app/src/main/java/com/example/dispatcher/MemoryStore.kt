package com.example.dispatcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Запись журнала памяти. */
data class JournalEntry(val role: String, val text: String, val source: String)

/** Память: явные факты + журнал всех сообщений (основной чат и все треды). */
data class MemoryState(val facts: List<String>, val journal: List<JournalEntry>)

/**
 * Хранение и сборка памяти для контекста модели.
 * Всё на устройстве; чистая логика отделена от prefs для тестов.
 */
object MemoryStore {

    fun empty(): MemoryState = MemoryState(emptyList(), emptyList())

    /** Добавить сообщение в журнал (роль: user/agent). */
    fun append(s: MemoryState, role: String, text: String, source: String): MemoryState =
        s.copy(journal = (s.journal + JournalEntry(role, text.take(400), source)).takeLast(300))

    /** Явный факт «запомни …». */
    fun addFact(s: MemoryState, fact: String): MemoryState =
        if (s.facts.contains(fact)) s
        else s.copy(facts = (s.facts + fact.take(200)).takeLast(30))

    /** Забыть факты, совпадающие с запросом. */
    fun removeFact(s: MemoryState, q: String): MemoryState =
        s.copy(facts = s.facts.filterNot { it.contains(q, ignoreCase = true) })

    /**
     * Компактный блок памяти для системного промпта:
     * факты + последние 20 сообщений из всех диалогов + сводки веток событий.
     */
    fun buildMemoryBlock(s: MemoryState, threads: Map<String, List<Msg>>): String {
        val b = StringBuilder()
        if (s.facts.isNotEmpty()) {
            b.append("Факты о пользователе:\n")
            s.facts.forEach { b.append("- ").append(it.take(200)).append("\n") }
        }
        val tail = s.journal.takeLast(20)
        if (tail.isNotEmpty()) {
            b.append("Недавние сообщения из всех диалогов:\n")
            tail.forEach { e ->
                val who = if (e.role == "user") "пользователь" else "агент"
                b.append("- ").append(who).append(": ")
                    .append(e.text.replace("\n", " ").take(180)).append("\n")
            }
        }
        if (threads.isNotEmpty()) {
            b.append("Обсуждения событий:\n")
            threads.entries.toList().takeLast(8).forEach { (key, ms) ->
                val src = key.substringAfter('|').substringBeforeLast('|')
                b.append("Событие «").append(src.take(30)).append("»:\n")
                val shown = if (ms.size > 6) ms.take(2) + ms.takeLast(6) else ms
                shown.forEach { m ->
                    val who = if (m.fromUser) "пользователь" else "агент"
                    b.append("  - ").append(who).append(": ")
                        .append(m.text.replace("\n", " ").take(160)).append("\n")
                }
            }
        }
        return b.toString().take(8000)
    }

    // ─── Сериализация ──────────────────────────────────────
    fun toJson(s: MemoryState): String {
        val facts = JSONArray()
        s.facts.forEach { facts.put(it) }
        val j = JSONArray()
        s.journal.forEach { e ->
            j.put(JSONObject().put("r", e.role).put("t", e.text).put("s", e.source))
        }
        return JSONObject().put("facts", facts).put("journal", j).toString()
    }

    fun fromJson(raw: String?): MemoryState {
        if (raw.isNullOrBlank()) return empty()
        return try {
            val o = JSONObject(raw)
            val facts = mutableListOf<String>()
            val fa = o.optJSONArray("facts")
            if (fa != null) for (i in 0 until fa.length()) facts += fa.optString(i)
            val j = mutableListOf<JournalEntry>()
            val ja = o.optJSONArray("journal")
            if (ja != null) for (i in 0 until ja.length()) {
                val e = ja.optJSONObject(i) ?: continue
                j += JournalEntry(e.optString("r"), e.optString("t"), e.optString("s"))
            }
            MemoryState(facts, j)
        } catch (e: Exception) {
            empty()
        }
    }

    fun load(prefs: android.content.SharedPreferences): MemoryState =
        fromJson(prefs.getString(Prefs.KEY_MEMORY, null))

    fun save(prefs: android.content.SharedPreferences, s: MemoryState) {
        prefs.edit().putString(Prefs.KEY_MEMORY, toJson(s)).apply()
    }
}
