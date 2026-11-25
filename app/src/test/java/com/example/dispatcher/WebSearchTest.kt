package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTest {

    private val html = """
        <html><body>
        <a rel="nofollow" href="https://example.com/one" class='result-link'>Первый результат</a>
        <td class='result-snippet'>Сниппет <b>первого</b> результата &amp; больше</td>
        <a rel="nofollow" href="https://example.com/two" class='result-link'>Второй &quot;результат&quot;</a>
        <td class='result-snippet'>Сниппет второго</td>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesLinksAndSnippets() {
        val r = WebSearch.parse(html)
        assertEquals(2, r.size)
        assertEquals("Первый результат", r[0].title)
        assertEquals("https://example.com/one", r[0].url)
        assertTrue(r[0].snippet.contains("Сниппет первого результата"))
        assertTrue(r[0].snippet.contains("&"))
        assertEquals("Второй \"результат\"", r[1].title)
        assertEquals("Сниппет второго", r[1].snippet)
    }

    @Test
    fun emptyHtmlGivesNoResults() {
        assertTrue(WebSearch.parse("").isEmpty())
        assertTrue(WebSearch.parse("<html>без результатов</html>").isEmpty())
    }

    @Test
    fun resultsTextIsFormatted() {
        val text = WebSearch.parseResults(html, "тест", limit = 1)
        assertTrue(text.contains("Результаты веб-поиска (DuckDuckGo) «тест» (1)"))
        assertTrue(text.contains("Первый результат"))
        assertTrue(text.contains("https://example.com/one"))
    }

    @Test
    fun nothingFoundMessage() {
        val text = WebSearch.parseResults("<html></html>", "нет такого")
        assertTrue(text.contains("ничего не найдено"))
    }

    @Test
    fun htmlToTextCleansPage() {
        val html = "<html><head><style>.x{color:red}</style><script>var a=1;</script></head><body><h1>Заголовок</h1><p>Текст &amp; детали<br>вторая строка</p><noscript>нет</noscript></body></html>"
        val t = WebSearch.htmlToText(html)
        assertTrue(t.contains("Заголовок"))
        assertTrue(t.contains("Текст & детали"))
        assertTrue(!t.contains("<style>"))
        assertTrue(!t.contains("var a=1"))
        assertTrue(!t.contains("<p>"))
    }
}
