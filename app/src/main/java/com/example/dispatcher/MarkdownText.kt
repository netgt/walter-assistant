package com.example.dispatcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Лёгкий рендер markdown-разметки для сообщений чата.
 * Подмножество: **жирный**, *курсив*, заголовки (#, ##, ###),
 * строки целиком в **…** (заголовки модели), суммы с валютой — автоматически жирным.
 * Парсер чистый (без Compose) — тестируется в JVM; render собирает AnnotatedString.
 */
object MarkdownText {

    /** Кусок текста: kind 0 = обычный, 1 = заголовок, 2 = строка-заголовок. */
    data class Chunk(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val kind: Int = 0,
        val colorIdx: Int = 0 // 1 — зелёный, 2 — красный
    )

    private val HEADING = Regex("""^#{1,4}\s+(.+)$""")
    private val BOLD_LINE = Regex("""^\*\*(.+)\*\*$""")
    private val CURRENCY = Regex("""(\d[\d\s., ]*\d)\s?[₽$€]""")

    // эмодзи и разноцветные иконки — вне стиля Walter: вырезаем из отображаемого текста
    private fun isEmojiCp(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF ||
            cp == 0xFE0F || cp == 0x200D

    /** Убрать эмодзи/смайлики — единый текстовый стиль чата (без regex, по кодпоинтам). */
    fun stripEmoji(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val cp: Int
            val width: Int
            if (Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
                cp = Character.toCodePoint(c, text[i + 1])
                width = 2
            } else {
                cp = c.code
                width = 1
            }
            if (!isEmojiCp(cp)) {
                sb.append(c)
                if (width == 2) sb.append(text[i + 1])
            }
            i += width
        }
        return sb.toString()
    }

    private fun boldCurrency(text: String): String =
        text.split("\n").joinToString("\n") { line -> boldCurrencyLine(line) }

    private fun boldCurrencyLine(line: String): String {
        val matches = CURRENCY.findAll(line).toList()
        if (matches.isEmpty()) return line
        // жирные сегменты строки — всё между парами **...**
        val boldRanges = mutableListOf<IntRange>()
        var i = line.indexOf("**")
        while (i >= 0) {
            val close = line.indexOf("**", i + 2)
            if (close < 0) break
            boldRanges += i..close + 1
            i = line.indexOf("**", close + 2)
        }
        val sb = StringBuilder()
        var last = 0
        for (m in matches) {
            val start = m.range.first
            val end = m.range.last + 1
            // сумма уже внутри жирного выделения — не оборачиваем повторно
            val insideBold = boldRanges.any { start >= it.first && end - 1 <= it.last }
            sb.append(line, last, start)
            if (insideBold) sb.append(m.value) else sb.append("**").append(m.value).append("**")
            last = end
        }
        sb.append(line, last, line.length)
        return sb.toString()
    }

    /** Строки -> список строк, каждая — список кусков. */
    fun parse(text: String): List<List<Chunk>> {
        // суммы с валютой — автоматически жирным (не трогаем уже выделенные **...**)
        val pre = boldCurrency(stripEmoji(text))
        return pre.split("\n").map { line ->
            val hm = HEADING.matchEntire(line)
            if (hm != null) return@map listOf(Chunk(hm.groupValues[1].trim(), bold = true, kind = 1))
            val bm = BOLD_LINE.matchEntire(line)
            // строка **Категория:** **4500 ₽** матчится BOLD_LINE целиком и оставила бы «**» в тексте —
            // поэтому строку с внутренними маркерами отдаём инлайн-парсеру
            if (bm != null && !bm.groupValues[1].contains("**")) {
                return@map listOf(Chunk(bm.groupValues[1].trim(), bold = true, kind = 2))
            }
            inline(line).map { chunk ->
                // Цвет статуса не зависит от того, сохранила ли модель [[g:]]/[[r:]] маркеры.
                val statusColor = when {
                    Regex("(?i)Статус(?:\\s*\\*\\*)?\\s*:").containsMatchIn(line) && line.contains("выключено", ignoreCase = true) -> 2
                    Regex("(?i)Статус(?:\\s*\\*\\*)?\\s*:").containsMatchIn(line) && line.contains("включено", ignoreCase = true) -> 1
                    else -> chunk.colorIdx
                }
                if (statusColor != 0 && (chunk.text.contains("включено", ignoreCase = true) ||
                        chunk.text.contains("выключено", ignoreCase = true)))
                    chunk.copy(colorIdx = statusColor) else chunk
            }
        }
    }

    private fun inline(s: String): List<Chunk> {
        val out = mutableListOf<Chunk>()
        val cur = StringBuilder()
        var bold = false
        var italic = false
        var colorIdx = 0
        fun flush() {
            if (cur.isNotEmpty()) {
                out.add(Chunk(cur.toString(), bold, italic, 0, colorIdx))
                cur.setLength(0)
            }
        }
        var i = 0
        while (i < s.length) {
            when {
                // цветные маркеры: [[g:зелёный]] и [[r:красный]]
                i + 4 < s.length && s.substring(i, i + 4) == "[[g:" -> {
                    flush(); colorIdx = 1; i += 4
                }
                i + 4 < s.length && s.substring(i, i + 4) == "[[r:" -> {
                    flush(); colorIdx = 2; i += 4
                }
                i + 1 < s.length && s[i] == ']' && s[i + 1] == ']' -> {
                    flush(); colorIdx = 0; i += 2
                }
                i + 1 < s.length && s[i] == '*' && s[i + 1] == '*' -> {
                    flush(); bold = !bold; i += 2
                }
                s[i] == '*' -> {
                    flush(); italic = !italic; i += 1
                }
                else -> {
                    cur.append(s[i]); i++
                }
            }
        }
        flush()
        return out
    }

    /** AnnotatedString для Compose Text. */
    fun render(
        text: String,
        baseColor: Color,
        baseSize: Int = 13,
        headingSize: Int = 15,
        boldLineSize: Int = 14,
        family: FontFamily = FontFamily.Monospace
    ): AnnotatedString = buildAnnotatedString {
        parse(text).forEachIndexed { li, chunks ->
            if (li > 0) append("\n")
            chunks.forEach { c ->
                val size = when (c.kind) {
                    1 -> headingSize
                    2 -> boldLineSize
                    else -> baseSize
                }
                val style = SpanStyle(
                    color = when (c.colorIdx) {
                        1 -> Color(0xFF2F7A4D) // включено — зелёный
                        2 -> Color(0xFFC0392B) // выключено — красный
                        else -> baseColor
                    },
                    fontFamily = family,
                    fontSize = size.sp,
                    fontWeight = if (c.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (c.italic) FontStyle.Italic else FontStyle.Normal
                )
                withStyle(style) { append(c.text) }
            }
        }
    }
}
