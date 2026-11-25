package com.example.dispatcher

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.FileInputStream

/** Прогон текста с форматированием (для «офисного вида» docx). */
data class ViewRun(val text: String, val bold: Boolean = false, val italic: Boolean = false, val size: Double = 0.0)

/** Блок документа: абзац с форматированием или таблица. */
sealed class DocBlock {
    data class Para(val runs: List<ViewRun>, val heading: Int = 0, val bullet: Boolean = false) : DocBlock()
    data class Table(val rows: List<List<String>>) : DocBlock()
}

/** Лист таблицы: имя, строки ячеек, признак жирной шапки. */
data class SheetView(val name: String, val rows: List<List<String>>, val headerBold: Boolean)

/** Слайд презентации: заголовок и пункты. */
data class SlideView(val title: String, val bullets: List<String>)

/**
 * Структурированное извлечение офисных форматов для «офисного вида» в карточке файла:
 * docx — абзацы с жирным/курсивом/размером и таблицы; xlsx — сетка листов;
 * pptx — слайды с заголовками и пунктами.
 * Упрощённый вид: картинки и сложная свёрстка не извлекаются — про это честная подпись в карточке.
 * Чистые функции с лимитами (файл не должен вешать UI), тестируются в JVM.
 */
object OfficeView {

    const val MAX_PARAS = 100_000
    const val MAX_TABLES = 5_000
    const val MAX_RUNS = 1_000_000
    const val MAX_SHEETS = 100
    const val MAX_ROWS = 200_000
    const val MAX_COLS = 300
    const val MAX_SLIDES = 500
    const val MAX_BULLETS = 2_000

    fun docxBlocks(path: String): List<DocBlock> = try {
        FileInputStream(path).use { ins ->
            val doc = org.apache.poi.xwpf.usermodel.XWPFDocument(ins)
            val out = mutableListOf<DocBlock>()
            var runs = 0
            var paras = 0
            var tables = 0
            for (el in doc.bodyElements) {
                when (el) {
                    is XWPFParagraph -> {
                        if (paras >= MAX_PARAS) continue
                        val style = (el.style ?: "").lowercase()
                        val heading = when {
                            style.contains("heading1") || style == "title" -> 1
                            style.contains("heading2") -> 2
                            style.contains("heading3") -> 3
                            else -> 0
                        }
                        val bullet = style.contains("list") || !el.numFmt.isNullOrBlank() ||
                            el.text.trimStart().startsWith("•")
                        val paraRuns = mutableListOf<ViewRun>()
                        for (r in el.runs) {
                            if (runs >= MAX_RUNS) break
                            val t = runCatching { r.getText(0) ?: "" }.getOrDefault("") ?: ""
                            if (t.isNotBlank()) {
                                val size = try { r.fontSize.toDouble() } catch (e: Throwable) { 0.0 }
                                paraRuns += ViewRun(t, r.isBold, r.isItalic, if (size < 1.0) 0.0 else size)
                                runs++
                            }
                        }
                        if (paraRuns.isEmpty() && el.text.isNotBlank()) {
                            paraRuns += ViewRun(el.text)
                            runs++
                        }
                        if (paraRuns.isNotEmpty()) {
                            out += DocBlock.Para(paraRuns, heading, bullet)
                            paras++
                        }
                    }
                    is XWPFTable -> {
                        if (tables >= MAX_TABLES) continue
                        val rows = el.rows.map { r -> r.tableCells.map { it.text.trim() } }
                        if (rows.isNotEmpty()) {
                            out += DocBlock.Table(rows)
                            tables++
                        }
                    }
                }
            }
            doc.close()
            return out
        }
    } catch (t: Throwable) {
        emptyList()
    }

    fun xlsxSheets(path: String): List<SheetView> = try {
        FileInputStream(path).use { ins ->
            val wb = XSSFWorkbook(ins)
            val fmt = DataFormatter()
            val out = mutableListOf<SheetView>()
            var si = 0
            for (sheet in wb) {
                if (si >= MAX_SHEETS) break
                si++
                val rows = mutableListOf<List<String>>()
                var ri = 0
                for (row in sheet) {
                    if (ri >= MAX_ROWS) break
                    ri++
                    val cells = (0 until minOf(row.lastCellNum.toInt(), MAX_COLS)).map { ci ->
                        val c = row.getCell(ci) ?: return@map ""
                        fmt.formatCellValue(c)
                    }
                    val trimmed = cells.dropLastWhile { it.isBlank() }
                    if (trimmed.isNotEmpty()) rows += trimmed
                }
                val headerBold = runCatching {
                    val c0 = sheet.getRow(0)?.getCell(0) ?: return@runCatching false
                    val font = c0.sheet.workbook.getFontAt(c0.cellStyle.fontIndex)
                    font.bold
                }.getOrDefault(false)
                out += SheetView(sheet.sheetName, rows, headerBold)
            }
            wb.close()
            return out
        }
    } catch (t: Throwable) {
        emptyList()
    }

    fun pptxSlides(path: String): List<SlideView> = try {
        FileInputStream(path).use { ins ->
            val ppt = XMLSlideShow(ins)
            val out = mutableListOf<SlideView>()
            for (slide in ppt.slides.take(MAX_SLIDES)) {
                var title = ""
                val bullets = mutableListOf<String>()
                for (sh in slide.shapes) {
                    if (sh !is XSLFTextShape) continue
                    val t = sh.text.trim()
                    if (t.isBlank()) continue
                    val isTitle = sh.textType == org.apache.poi.sl.usermodel.Placeholder.TITLE
                    if (title.isBlank() && isTitle) {
                        title = t
                    } else {
                        t.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach { bullets += it }
                    }
                }
                if (title.isBlank() && bullets.isNotEmpty()) title = bullets.removeAt(0)
                if (title.isNotBlank() || bullets.isNotEmpty()) {
                    out += SlideView(title, bullets.take(MAX_BULLETS))
                }
            }
            ppt.close()
            return out
        }
    } catch (t: Throwable) {
        emptyList()
    }
}
