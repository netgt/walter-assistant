package com.example.dispatcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/**
 * Яндекс Календарь по CalDAV (caldav.yandex.ru): авторизация Basic
 * (логин + пароль приложения типа «Календарь»), обнаружение календарей
 * через PROPFIND и чтение событий через calendar-data.
 * Парсинг — чистые функции, тестируются в JVM.
 */
object CalDavStore {

    const val DEFAULT_SERVER = "https://caldav.yandex.ru"

    private val XML_MEDIA = "application/xml; charset=utf-8".toMediaType()

    private fun propfindBody(inner: String): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<d:propfind xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">$inner</d:propfind>"

    private fun authHeader(user: String, pass: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))

    private fun absoluteUrl(base: String, href: String): String =
        if (href.startsWith("http")) href else base + (if (href.startsWith("/")) "" else "/") + href

    /** Все href из PROPFIND-ответа. */
    fun parseHrefs(xml: String): List<String> =
        Regex("<[^>]*href[^>]*>(.*?)</[^>]*href>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()

    /** href из свойства current-user-principal (в ответе Яндекса первый href — сам запрос, не принципал). */
    fun parsePrincipal(xml: String): String? {
        val m = Regex(
            "<[^>]*current-user-principal[^>]*>(.*?)</[^>]*current-user-principal>",
            RegexOption.DOT_MATCHES_ALL
        ).find(xml) ?: return null
        return Regex("<[^>]*href[^>]*>(.*?)</[^>]*href>", RegexOption.DOT_MATCHES_ALL)
            .find(m.groupValues[1])?.groupValues?.get(1)?.trim()
    }

    /** href из свойства calendar-home-set. */
    fun parseHomeSet(xml: String): String? {
        val m = Regex(
            "<[^>]*calendar-home-set[^>]*>(.*?)</[^>]*calendar-home-set>",
            RegexOption.DOT_MATCHES_ALL
        ).find(xml) ?: return null
        return Regex("<[^>]*href[^>]*>(.*?)</[^>]*href>", RegexOption.DOT_MATCHES_ALL)
            .find(m.groupValues[1])?.groupValues?.get(1)?.trim()
    }

    /** href-блоки календарных коллекций (resourcetype содержит calendar). */
    fun parseCalendars(xml: String): List<String> {
        val out = mutableListOf<String>()
        val respRe = Regex("<[^>]*response[^>]*>(.*?)</[^>]*response>", RegexOption.DOT_MATCHES_ALL)
        for (m in respRe.findAll(xml)) {
            val block = m.groupValues[1]
            if (!Regex("<[^>]*calendar").containsMatchIn(block)) continue
            val href = Regex("<[^>]*href[^>]*>(.*?)</[^>]*href>")
                .find(block)?.groupValues?.get(1)?.trim()
            if (!href.isNullOrEmpty()) out += href
        }
        return out
    }

    /** Блоки calendar-data из PROPFIND-ответа (CDATA-обёртка снимается). */
    fun parseCalendarData(xml: String): List<String> {
        val out = mutableListOf<String>()
        val cdRe = Regex("<[^>]*calendar-data[^>]*>(.*?)</[^>]*calendar-data>", RegexOption.DOT_MATCHES_ALL)
        for (m in cdRe.findAll(xml)) {
            val raw = m.groupValues[1].trim()
            val ics = if (raw.startsWith("<![CDATA["))
                raw.removePrefix("<![CDATA[").removeSuffix("]]>") else raw
            out += ics
        }
        return out
    }

    /**
     * Полный поток чтения: принципал → home-set → список календарей → события.
     * События фильтруются по [from, to) и сортируются по времени.
     */
    suspend fun fetchEvents(
        client: OkHttpClient,
        server: String,
        user: String,
        pass: String,
        from: Long,
        to: Long
    ): List<CalendarStore.CalEvent> {
        val base = server.trim().trimEnd('/')
        return withContext(Dispatchers.IO) {
            val auth = authHeader(user, pass)

            // 1) текущий пользователь
            val principalXml = propfindRequest(
                client, base, 0, auth,
                propfindBody("<d:prop><d:current-user-principal/></d:prop>")
            )
            val principalHref = parsePrincipal(principalXml)
            if (principalHref == null) {
                emptyList()
            } else {
                // 2) calendar-home-set
                val homeXml = propfindRequest(
                    client, absoluteUrl(base, principalHref), 0, auth,
                    propfindBody("<d:prop><c:calendar-home-set/></d:prop>")
                )
                val homeHref = parseHomeSet(homeXml)
                if (homeHref == null) {
                    emptyList()
                } else {
                    // 3) список календарей
                    val calsXml = propfindRequest(
                        client, absoluteUrl(base, homeHref), 1, auth,
                        propfindBody("<d:prop><d:resourcetype/></d:prop>")
                    )
                    val calendars = parseCalendars(calsXml).ifEmpty { parseHrefs(calsXml) }.take(10)

                    // 4) события из каждого календаря (один сбойный календарь не ломает всё)
                    val events = mutableListOf<CalendarStore.CalEvent>()
                    for (cal in calendars) {
                        runCatching {
                            val dataXml = propfindRequest(
                                client, absoluteUrl(base, cal), 1, auth,
                                propfindBody("<d:prop><c:calendar-data/></d:prop>")
                            )
                            for (ics in parseCalendarData(dataXml)) {
                                events += CalendarStore.parseIcsEvents(ics)
                            }
                        }
                    }
                    events.filter { it.start < to && it.end >= from }.sortedBy { it.start }
                }
            }
        }
    }

    private fun propfindRequest(
        client: OkHttpClient,
        url: String,
        depth: Int,
        auth: String,
        body: String
    ): String {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .header("Depth", depth.toString())
            .header("Content-Type", "application/xml; charset=utf-8")
            .method("PROPFIND", body.toRequestBody(XML_MEDIA))
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw java.io.IOException("HTTP ${resp.code}")
            }
            resp.body?.string() ?: ""
        }
    }
}

/** Аккаунт календаря (Яндекс CalDAV; системный календарь — отдельно через разрешение). */
data class CalDavAccount(
    val id: Long,
    val label: String,
    val url: String,
    val user: String,
    val pass: String
)

/** Хранилище списка CalDAV-аккаунтов: JSON в настройках, чистые функции. */
object CalDavAccounts {

    fun toJson(list: List<CalDavAccount>): String {
        val arr = org.json.JSONArray()
        list.forEach { a ->
            arr.put(
                org.json.JSONObject()
                    .put("id", a.id)
                    .put("label", a.label)
                    .put("url", a.url)
                    .put("user", a.user)
                    .put("pass", a.pass)
            )
        }
        return arr.toString()
    }

    fun fromJson(raw: String?): List<CalDavAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CalDavAccount(
                    o.optLong("id"),
                    o.optString("label"),
                    o.optString("url"),
                    o.optString("user"),
                    o.optString("pass")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(list: List<CalDavAccount>, label: String, url: String, user: String, pass: String): List<CalDavAccount> {
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1
        return list + CalDavAccount(id, label.trim(), url.trim(), user.trim(), pass)
    }

    fun remove(list: List<CalDavAccount>, id: Long): List<CalDavAccount> = list.filter { it.id != id }
}
