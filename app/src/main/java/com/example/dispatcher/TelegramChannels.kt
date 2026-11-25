package com.example.dispatcher

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Парсер публичных каналов Telegram через веб-превью t.me/s/<канал>.
 * Чистый: получает HTML, отдаёт посты (текст + дата). Тестируется в JVM.
 */
object TelegramChannels {

    data class Post(val text: String, val date: String)

    fun parse(html: String): List<Post> {
        val out = mutableListOf<Post>()
        val textRe = Regex(
            """<div class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val timeRe = Regex("""<time datetime="([^"]+)""")
        var searchFrom = 0
        while (out.size < 30) {
            val tm = textRe.find(html, searchFrom) ?: break
            searchFrom = tm.range.last + 1
            val clean = cleanHtml(tm.groupValues[1])
            if (clean.isBlank()) continue
            // дата — ближайший <time> перед текстом (или после)
            val before = html.substring(maxOf(0, tm.range.first - 2000), tm.range.first)
            val after = html.substring(tm.range.last + 1, minOf(html.length, tm.range.last + 500))
            val dm = timeRe.find(before) ?: timeRe.find(after)
            val date = dm?.groupValues?.get(1)?.let { formatDate(it) } ?: ""
            out += Post(clean.take(400), date)
        }
        return out
    }

    private fun formatDate(iso: String): String = runCatching {
        val df = SimpleDateFormat("dd.MM", Locale.getDefault())
        df.format(Date(java.time.Instant.parse(iso).toEpochMilli()))
    }.getOrDefault(iso.take(10))

    private fun cleanHtml(s: String): String =
        MarkdownText.stripEmoji(unescape(stripTags(s))).trim()

    private fun stripTags(s: String): String =
        s.replace(Regex("""<[^>]+>"""), " ").replace(Regex("""\s+"""), " ")

    private fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")

    fun formatPosts(channel: String, posts: List<Post>): String {
        val name = channel.removePrefix("@")
        if (posts.isEmpty()) return "В канале @$name не нашлось текстовых постов (канал может быть приватным или публикует только медиа)."
        return "Посты канала @$name (${posts.size}):\n" + posts.joinToString("\n") { p ->
            if (p.date.isBlank()) p.text else "[${p.date}] ${p.text}"
        }
    }

    /** Имя канала из любого ввода: «@name», «https://t.me/s/name», «t.me/name», «name». */
    fun normalizeChannel(raw: String): String =
        raw.trim()
            .removePrefix("@")
            .replace(Regex("""^https?://"""), "")
            .removePrefix("t.me/s/")
            .removePrefix("t.me/")
            .trim('/')
            .substringBefore('/')

    /**
     * Разбор свободного ввода списка каналов: по одному на строку,
     * через запятую, «;» или пробел. Возвращает нормализованные имена без дублей.
     */
    fun parseChannelsInput(raw: String): List<String> =
        raw.split(Regex("""[\n,;]+|\s+"""))
            .map { normalizeChannel(it) }
            .filter { it.isNotBlank() }
            .distinct()

    fun channelsToJson(channels: List<String>): String {
        val arr = org.json.JSONArray()
        channels.forEach { arr.put(it) }
        return arr.toString()
    }

    fun channelsFromJson(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Список прокси из поля настроек: через запятую, «;» или по одному на строку. */
    fun parseProxyList(raw: String): List<String> =
        raw.split(Regex("""[\n,;]+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    /** Разбор «[http|socks5://]host:port» → (scheme, host, port); null — битая строка. */
    fun parseProxy(spec: String): Triple<String, String, Int>? {
        val t = spec.trim()
        val (scheme, rest) = if (t.contains("://")) t.substringBefore("://") to t.substringAfter("://")
        else "http" to t
        if (rest.isBlank()) return null
        val host = if (rest.contains(":")) rest.substringBefore(':') else rest
        val port = rest.substringAfter(':', "80").toIntOrNull() ?: return null
        return Triple(scheme.lowercase(), host, port)
    }
}
