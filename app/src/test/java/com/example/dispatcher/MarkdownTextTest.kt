package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun boldInline() {
        val lines = MarkdownText.parse("Итого **128 450 ₽** за месяц")
        val chunks = lines.single()
        assertTrue(chunks.any { it.bold && it.text == "128 450 ₽" })
        assertTrue(chunks.any { !it.bold && it.text == "Итого " })
    }

    @Test
    fun wholeLineBoldIsHeading() {
        val lines = MarkdownText.parse("**Траты за месяц**")
        val c = lines.single().single()
        assertTrue(c.bold)
        assertEquals(2, c.kind)
        assertEquals("Траты за месяц", c.text)
    }

    @Test
    fun hashHeadings() {
        val lines = MarkdownText.parse("## Категории\nобычный текст")
        assertEquals(1, lines[0].single().kind)
        assertTrue(lines[0].single().bold)
        assertEquals("Категории", lines[0].single().text)
        assertEquals(0, lines[1].single().kind)
    }

    @Test
    fun italic() {
        val chunks = MarkdownText.parse("слово *курсив* тут").single()
        assertTrue(chunks.any { it.italic && it.text == "курсив" })
    }

    @Test
    fun currencyAutoBold() {
        val chunks = MarkdownText.parse("Остаток 45 000,00 ₽ на карте").single()
        assertTrue(chunks.any { it.bold && it.text.contains("45 000,00 ₽") })
    }

    @Test
    fun sumAlreadyInsideBoldIsNotDoubleWrapped() {
        // модель уже выделила категорию с суммой целиком — звёздочек-хвостов быть не должно
        val chunks = MarkdownText.parse("**Продукты: 4500 ₽**").single()
        val text = chunks.joinToString("") { it.text }
        assertTrue(!text.contains("**"))
        assertTrue(chunks.all { it.bold })
        assertTrue(text.contains("4500 ₽"))
        // категория жирная, сумма рядом — сумма жирнеет автоматически, хвостов нет
        val chunks2 = MarkdownText.parse("**Категория:** 4500 ₽").single()
        val text2 = chunks2.joinToString("") { it.text }
        assertTrue(!text2.contains("**"))
        assertTrue(chunks2.any { it.bold && it.text.contains("4500 ₽") })
        assertTrue(chunks2.any { it.bold && it.text.contains("Категория:") })
    }

    @Test
    fun plainTextUntouched() {
        val chunks = MarkdownText.parse("открой телеграм").single()
        assertFalse(chunks.any { it.bold })
        assertEquals("открой телеграм", chunks.single().text)
    }

    @Test
    fun unclosedBoldDoesNotCrash() {
        val lines = MarkdownText.parse("незакрытый **жирный")
        assertEquals(2, lines.single().size)
    }

    @Test
    fun statusColorsWorkEvenWithoutExplicitMarkers() {
        val enabled = MarkdownText.parse("**Статус:** **включено**").single()
        assertTrue(enabled.any { it.text == "включено" && it.colorIdx == 1 && it.bold })
        val disabled = MarkdownText.parse("**Статус:** **выключено**").single()
        assertTrue(disabled.joinToString { "${it.text}/${it.colorIdx}/${it.bold}" },
            disabled.any { it.text == "выключено" && it.colorIdx == 2 && it.bold })
    }

    @Test
    fun emojiAreStripped() {
        assertEquals("", MarkdownText.stripEmoji("\uD83D\uDED2"))
        assertEquals("покупки  и траты", MarkdownText.stripEmoji("покупки \uD83D\uDED2 и траты"))
        assertEquals("папка", MarkdownText.stripEmoji("\uD83D\uDCC1 папка").trim())
        assertEquals("текст", MarkdownText.stripEmoji("\uD83D\uDE00текст").trim())
        assertEquals("обычный", MarkdownText.stripEmoji("обычный"))
    }

    @Test
    fun parseStripsEmojiFromLines() {
        val lines = MarkdownText.parse("Итого \uD83D\uDCB0 100 ₽ и корзина \uD83D\uDED2")
        val text = lines.single().joinToString("") { it.text }
        assertTrue(!text.contains("\uD83D\uDED2"))
        assertTrue(text.contains("100 ₽"))
    }
}
