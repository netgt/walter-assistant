package com.example.dispatcher

/**
 * Веб-поиск через DuckDuckGo Lite (без API-ключа).
 * Чистый парсер HTML — тестируется в JVM.
 */
object WebSearch {

    data class Result(val title: String, val url: String, val snippet: String)

    fun parse(html: String): List<Result> {
        val linkRe = Regex("""<a[^>]*href="([^"]+)"[^>]*class='result-link'>([\s\S]*?)</a>""")
        val snipRe = Regex("""<td[^>]*class='result-snippet'>([\s\S]*?)</td>""")
        val links = linkRe.findAll(html).toList()
        val snips = snipRe.findAll(html).toList()
        val out = mutableListOf<Result>()
        links.forEachIndexed { i, m ->
            val title = clean(m.groupValues[2])
            if (title.isBlank()) return@forEachIndexed
            val snip = if (i < snips.size) clean(snips[i].groupValues[1]) else ""
            out += Result(title, m.groupValues[1], snip)
        }
        return out
    }

    fun parseResults(html: String, query: String, limit: Int = 8): String {
        val results = parse(html)
        if (results.isEmpty()) return "По запросу «$query» ничего не найдено."
        val b = StringBuilder("Результаты веб-поиска (DuckDuckGo) «$query» (${minOf(results.size, limit)}):\n")
        results.take(limit).forEach { r ->
            b.append("• ").append(r.title).append("\n")
            b.append("  ").append(r.url).append("\n")
            if (r.snippet.isNotBlank()) b.append("  ").append(r.snippet).append("\n")
        }
        return b.trim().toString()
    }

    /** HTML любой страницы → чистый текст (для fetch_url через cors.lol). */
    fun htmlToText(html: String): String =
        html
            .replace(Regex("""(?is)<(script|style|noscript)[^>]*>.*?</\1>"""), " ")
            .replace(Regex("""(?is)<br\s*/?>|</p>|</div>|</li>|</h1>|</h2>|</h3>|</h4>|</tr>"""), "\n")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n\s*\n\s*"""), "\n")
            .replace(Regex("""\n{2,}"""), "\n")
            .trim()

    private fun clean(s: String): String = unescape(stripTags(s)).trim()

    private fun stripTags(s: String): String =
        s.replace(Regex("""<[^>]+>"""), " ").replace(Regex("""\s+"""), " ")

    private fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}
