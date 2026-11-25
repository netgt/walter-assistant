package com.example.dispatcher

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Офисные форматы: сырое чтение (извлечение текста) и полная запись того,
 * что подготовила модель: docx/xlsx/pptx/pdf пересоздаются из текста.
 * Весь анализ и генерация содержимого — на стороне модели.
 */
object OfficeStore {

    fun kindOf(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "docx" -> "doc"
        "xlsx" -> "sheet"
        "pptx" -> "slides"
        "pdf" -> "pdf"
        else -> null
    }

    fun extractText(path: String, maxChars: Int = 20_000): String = try {
        val text = when (kindOf(path)) {
            "doc" -> docxText(path)
            "sheet" -> xlsxText(path)
            "slides" -> pptxText(path)
            "pdf" -> pdfText(path)
            else -> ""
        }
        text.take(maxChars)
    } catch (t: Throwable) {
        "Не удалось извлечь текст: ${t.message ?: t.javaClass.simpleName}"
    }

    private fun docxText(path: String): String {
        FileInputStream(path).use { ins ->
            val doc = XWPFDocument(ins)
            val sb = StringBuilder()
            doc.paragraphs.forEach { p ->
                var t = p.text
                if (t.isBlank()) t = p.runs.joinToString("") { r -> runCatching { r.getText(0) ?: "" }.getOrDefault("") }
                if (t.isNotBlank()) sb.append(t).append('\n')
            }
            doc.tables.forEach { t ->
                t.rows.forEach { r ->
                    val cells = r.tableCells.map { it.text }.filter { it.isNotBlank() }
                    if (cells.isNotEmpty()) sb.append(cells.joinToString(" | ")).append('\n')
                }
            }
            doc.close()
            return sb.toString()
        }
    }

    private fun xlsxText(path: String): String {
        FileInputStream(path).use { ins ->
            val wb = XSSFWorkbook(ins)
            val fmt = DataFormatter()
            val sb = StringBuilder()
            for (sheet in wb) {
                sb.append("## ").append(sheet.sheetName).append('\n')
                for (row in sheet) {
                    val cells = row.map { fmt.formatCellValue(it) }.filter { it.isNotBlank() }
                    if (cells.isNotEmpty()) sb.append(cells.joinToString(" | ")).append('\n')
                }
            }
            wb.close()
            return sb.toString()
        }
    }

    private fun pptxText(path: String): String {
        FileInputStream(path).use { ins ->
            val ppt = XMLSlideShow(ins)
            val sb = StringBuilder()
            ppt.slides.forEachIndexed { i, slide ->
                sb.append("## Слайд ").append(i + 1).append('\n')
                slide.shapes.forEach { sh ->
                    if (sh is XSLFTextShape && sh.text.isNotBlank()) sb.append(sh.text).append('\n')
                }
            }
            ppt.close()
            return sb.toString()
        }
    }

    /**
     * Полная перезапись docx текстом, подготовленным моделью:
     * строки текста становятся абзацами документа.
     */
    fun writeDocx(path: String, content: String): String {
        FileOutputStream(path).use { out ->
            val doc = XWPFDocument()
            content.split("\n").forEach { line ->
                if (line.isNotBlank()) doc.createParagraph().createRun().setText(line)
            }
            doc.write(out)
            doc.close()
        }
        return "Файл записан (docx)"
    }

    /**
     * Полная перезапись xlsx из текста модели:
     * строка «## Имя листа» открывает новый лист, остальные строки — ряды,
     * ячейки разделены « | ».
     */
    fun writeXlsx(path: String, content: String): String {
        FileOutputStream(path).use { out ->
            val wb = XSSFWorkbook()
            var sheet = wb.createSheet("Лист1")
            val usedNames = mutableSetOf("Лист1")
            for (raw in content.split("\n")) {
                val line = raw.trimEnd('\r').trim()
                if (line.startsWith("##")) {
                    var name = line.removePrefix("##").trim()
                    if (name.isBlank()) name = "Лист${wb.numberOfSheets + 1}"
                    name = name.take(31)
                    var candidate = name
                    var n = 2
                    val lower = usedNames.map { it.lowercase() }
                    while (candidate.lowercase() in lower) {
                        candidate = name.take(28) + " ($n)"
                        n++
                    }
                    usedNames += candidate
                    sheet = wb.createSheet(candidate)
                } else if (line.isNotBlank()) {
                    val row = sheet.createRow(sheet.lastRowNum + 1)
                    line.split("|").forEachIndexed { i, cell -> row.createCell(i).setCellValue(cell.trim()) }
                }
            }
            wb.write(out)
            wb.close()
        }
        return "Файл записан (xlsx)"
    }

    /** Полная перезапись pptx из текста модели: «## Слайд N» начинает новый слайд. */
    fun writePptx(path: String, content: String): String =
        PptxWriter.write(path, PptxWriter.parseSlides(content))

    /**
     * Полная перезапись pdf из текста модели: простой текстовый PDF.
     * Запись — нативный android.graphics.pdf.PdfDocument (без сторонних библиотек,
     * кириллица через системный шрифт, не зависит от java.awt и CMAP-ресурсов).
     */
    fun writePdf(path: String, content: String): String = try {
        val doc = android.graphics.pdf.PdfDocument()
        try {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.BLACK
                textSize = 12f
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF,
                    android.graphics.Typeface.NORMAL
                )
            }
            val lineHeight = 16f
            val pageW = 595f // A4 в пунктах
            val pageH = 842f
            val margin = 50f
            var pageNum = 1
            for (pageLines in paginateLines(content, 42)) {
                val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                    pageW.toInt(), pageH.toInt(), pageNum
                ).create()
                val page = doc.startPage(info)
                var y = margin + 12f
                for (l in pageLines) {
                    page.canvas.drawText(l, margin, y, paint)
                    y += lineHeight
                }
                doc.finishPage(page)
                pageNum++
            }
            FileOutputStream(path).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        "Файл записан (pdf)"
    } catch (t: Throwable) {
        "Не удалось создать PDF: ${t.message ?: t.javaClass.simpleName}"
    }

    /**
     * Чистое разбиение текста на страницы: перенос длинных строк (95 символов),
     * страницы по maxLines строк. Тестируется в JVM.
     */
    fun paginateLines(content: String, maxLines: Int = 42): List<List<String>> {
        val lines = mutableListOf<String>()
        for (raw in content.split("\n")) {
            val l = raw.trimEnd('\r')
            if (l.length <= 95) lines.add(l) else l.chunked(95).forEach { lines.add(it) }
        }
        if (lines.isEmpty()) return listOf(emptyList())
        return lines.chunked(maxLines)
    }

    private fun pdfText(path: String): String =
        PDDocument.load(File(path)).use { doc -> PDFTextStripper().getText(doc) }
}
