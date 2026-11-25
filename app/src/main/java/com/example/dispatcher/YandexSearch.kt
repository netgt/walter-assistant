package com.example.dispatcher

/**
 * Парсер выдачи Yandex Search API v2 (синхронный метод /v2/web/search).
 * Ответ — JSON {"rawData": "<base64>"}, внутри — XML yandexsearch:
 * <doc><url/><title/><headline/><passages><passage/></passages></doc>.
 * Найденные слова обёрнуты в <hlword>. Чистый — тестируется в JVM.
 */
object YandexSearch {

    data class Result(val title: String, val url: String, val snippet: String)

    fun parseXml(xml: String): List<Result> {
        val out = mutableListOf<Result>()
        val docRe = Regex("""<doc\b[^>]*>(.*?)</doc>""", RegexOption.DOT_MATCHES_ALL)
        for (dm in docRe.findAll(xml)) {
            val body = dm.groupValues[1]
            val url = firstField(body, "url")
            val title = firstField(body, "title")
            if (url.isBlank() || title.isBlank()) continue
            var snippet = firstField(body, "headline")
            if (snippet.isBlank()) snippet = firstField(body, "passage")
            out += Result(title, url, snippet)
        }
        return out
    }

    /** «Нашлось N ответов» — если есть в выдаче. */
    fun foundHuman(xml: String): String = firstField(xml, "found-human")

    fun formatResults(xml: String, query: String, limit: Int = 8): String {
        val results = parseXml(xml)
        if (results.isEmpty()) return "По запросу «$query» ничего не найдено."
        val found = foundHuman(xml)
        val b = StringBuilder("Результаты веб-поиска (Яндекс) «$query»")
        if (found.isNotBlank()) b.append(" — ").append(found)
        b.append(" (показано ").append(minOf(results.size, limit)).append("):\n")
        results.take(limit).forEach { r ->
            b.append("• ").append(r.title).append("\n")
            b.append("  ").append(r.url).append("\n")
            if (r.snippet.isNotBlank()) b.append("  ").append(r.snippet).append("\n")
        }
        return b.trim().toString()
    }

    private fun firstField(xml: String, tag: String): String {
        val m = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL).find(xml)
            ?: return ""
        return clean(m.groupValues[1])
    }

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
