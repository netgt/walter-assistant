package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexSearchTest {

    private val sample = """
        <?xml version="1.0"?>
        <yandexsearch version="1.0">
        <response>
        <found-human>Нашлось 3 млн ответов</found-human>
        <results><grouping><group>
        <doc id="A">
            <url>https://example.com/a</url>
            <domain>example.com</domain>
            <title>Курс <hlword>биткоина</hlword> сегодня</title>
            <headline>Цена <hlword>биткоина</hlword> &amp; график</headline>
        </doc>
        <doc id="B">
            <url>https://example.com/b</url>
            <title>Второй заголовок</title>
            <passages><passage>Пассаж второго</passage></passages>
        </doc>
        </group></grouping></results>
        </response></yandexsearch>
    """.trimIndent()

    @Test
    fun parsesDocs() {
        val r = YandexSearch.parseXml(sample)
        assertEquals(2, r.size)
        assertEquals("Курс биткоина сегодня", r[0].title)
        assertEquals("https://example.com/a", r[0].url)
        assertEquals("Цена биткоина & график", r[0].snippet)
        assertEquals("Второй заголовок", r[1].title)
        assertEquals("Пассаж второго", r[1].snippet)
    }

    @Test
    fun emptyOrBroken() {
        assertTrue(YandexSearch.parseXml("").isEmpty())
        assertTrue(YandexSearch.parseXml("не xml").isEmpty())
        assertTrue(YandexSearch.parseXml("<yandexsearch></yandexsearch>").isEmpty())
    }

    @Test
    fun formatWithFound() {
        val text = YandexSearch.formatResults(sample, "биткоин", limit = 1)
        assertTrue(text.contains("Результаты веб-поиска (Яндекс) «биткоин»"))
        assertTrue(text.contains("Нашлось 3 млн ответов"))
        assertTrue(text.contains("Курс биткоина сегодня"))
        assertTrue(text.contains("https://example.com/a"))
    }

    @Test
    fun nothingFound() {
        assertTrue(YandexSearch.formatResults("<yandexsearch/>", "нет").contains("ничего не найдено"))
    }
}
