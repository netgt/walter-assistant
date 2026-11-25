package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamDecoderTest {

    @Test
    fun decodesNewline() {
        val d = StreamDecoder()
        assertEquals("привет\nмир", d.push("привет\\nмир"))
    }

    @Test
    fun splitEscapeAcrossChunks() {
        val d = StreamDecoder()
        assertEquals("привет", d.push("привет\\"))
        assertEquals("\nмир", d.push("nмир"))
    }

    @Test
    fun quoteAndBackslash() {
        val d = StreamDecoder()
        assertEquals("a\"b\\c", d.push("a\\\"b\\\\c"))
    }

    @Test
    fun unicodeEscape() {
        val d = StreamDecoder()
        assertEquals("Ж", d.push("\\u0416"))
    }

    @Test
    fun splitUnicodeAcrossChunks() {
        val d = StreamDecoder()
        assertEquals("", d.push("\\u04"))
        assertEquals("Ж", d.push("16"))
    }

    @Test
    fun plainTextPassesThrough() {
        val d = StreamDecoder()
        assertEquals("обычный текст", d.push("обычный текст"))
    }

    @Test
    fun markdownAsterisksUntouched() {
        val d = StreamDecoder()
        assertEquals("**жирный** и ## заголовок", d.push("**жирный** и ## заголовок"))
    }

    @Test
    fun displayWithholdsJsonCloser() {
        val d = StreamDecoder()
        assertEquals("при", d.display("приве"))
        assertEquals("вет", d.display("т\"}"))
    }

    @Test
    fun displayHandlesSplitEscape() {
        val d = StreamDecoder()
        assertEquals("прив", d.display("привет\\"))
        assertEquals("е", d.display("n"))
        assertEquals("т\n", d.display("\"}"))
    }

    @Test
    fun stopsAtClosingQuoteAndHidesJsonTail() {
        val d = StreamDecoder()
        val chunks = listOf(
            "Привет, это \"сводка\" новостей",
            " и вот итог\"",
            ",\"app\":null,\"title\":null,\"reply\":null,\"query\":null,\"from\":null,\"to\":null}"
        )
        var out = ""
        chunks.forEach { c -> out += d.display(c) }
        out += d.display("") // дожим остатка буфера
        assertTrue(out.contains("Привет, это \"сводка\" новостей и вот итог"))
        assertFalse(out.contains("\"app\""))
        assertFalse(out.contains("null"))
    }
}
