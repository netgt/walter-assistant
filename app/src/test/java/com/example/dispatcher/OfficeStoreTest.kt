package com.example.dispatcher

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class OfficeStoreTest {

    private fun tmp(ext: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "office_${System.nanoTime()}")
        dir.mkdirs()
        return File(dir, "test.$ext")
    }

    @Test
    fun docxExtract() {
        val f = tmp("docx")
        FileOutputStream(f).use { out ->
            val doc = XWPFDocument()
            doc.createParagraph().createRun().setText("Первый абзац")
            doc.createParagraph().createRun().setText("Второй абзац")
            doc.write(out)
            doc.close()
        }
        try {
            val text = OfficeStore.extractText(f.absolutePath)
            assertTrue(text.contains("Первый абзац"))
            assertTrue(text.contains("Второй абзац"))
        } finally {
            f.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun xlsxExtract() {
        val f = tmp("xlsx")
        FileOutputStream(f).use { out ->
            val wb = XSSFWorkbook()
            val sheet = wb.createSheet("Лист1")
            sheet.createRow(0).createCell(0).setCellValue("Ячейка")
            wb.write(out)
            wb.close()
        }
        try {
            val text = OfficeStore.extractText(f.absolutePath)
            assertTrue(text.contains("Лист1"))
            assertTrue(text.contains("Ячейка"))
        } finally {
            f.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun docxWriteRecreatesFromModelText() {
        val f = tmp("docx")
        FileOutputStream(f).use { out ->
            val doc = XWPFDocument()
            doc.createParagraph().createRun().setText("Старое содержимое")
            doc.write(out)
            doc.close()
        }
        try {
            val res = OfficeStore.writeDocx(f.absolutePath, "Новая строка 1\nНовая строка 2")
            assertTrue(res.contains("записан"))
            val text = OfficeStore.extractText(f.absolutePath)
            assertTrue(text.contains("Новая строка 1"))
            assertTrue(text.contains("Новая строка 2"))
            assertTrue(!text.contains("Старое содержимое"))
        } finally {
            f.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun missingFileGivesErrorText() {
        val res = OfficeStore.extractText("/tmp/nonexistent_xyz.docx")
        assertTrue(res.contains("Не удалось извлечь текст"))
    }

    @Test
    fun kindOfTable() {
        assertEquals("doc", OfficeStore.kindOf("a.docx"))
        assertEquals("sheet", OfficeStore.kindOf("b.xlsx"))
        assertEquals("slides", OfficeStore.kindOf("c.pptx"))
        assertEquals("pdf", OfficeStore.kindOf("d.pdf"))
        assertEquals(null, OfficeStore.kindOf("e.txt"))
    }

    @Test
    fun xlsxWriteRoundtrip() {
        val f = tmp("xlsx")
        f.writeText("x") // пересоздаётся целиком
        try {
            val res = OfficeStore.writeXlsx(f.absolutePath, "## Бюджет\nЕда | 100\n## Прочее\nСон | 50")
            assertTrue(res.contains("xlsx"))
            val text = OfficeStore.extractText(f.absolutePath)
            assertTrue(text.contains("Бюджет"))
            assertTrue(text.contains("Прочее"))
            assertTrue(text.contains("Еда | 100"))
        } finally {
            f.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun pptxWriteRoundtrip() {
        val f = tmp("pptx")
        f.writeText("x")
        try {
            val res = OfficeStore.writePptx(f.absolutePath, "## Слайд 1\nПривет\n## Слайд 2\nПока")
            assertTrue(res.contains("pptx"))
            val text = OfficeStore.extractText(f.absolutePath)
            assertTrue(text.contains("## Слайд 2"))
            assertTrue(text.contains("Привет"))
        } finally {
            f.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun pptxParseSlides() {
        val slides = PptxWriter.parseSlides("## Слайд 1\nA\nB\n## Слайд 2\nC")
        assertEquals(2, slides.size)
        assertEquals(listOf("Слайд 1", "A", "B"), slides[0])
        assertEquals(listOf("Слайд 2", "C"), slides[1])
        // без маркеров — один слайд
        assertEquals(1, PptxWriter.parseSlides("просто текст").size)
    }

    @Test
    fun paginateLinesSplitsPages() {
        // короткий текст — одна страница
        val pages = OfficeStore.paginateLines("a\nb", maxLines = 2)
        assertEquals(1, pages.size)
        assertEquals(listOf("a", "b"), pages[0])
        // длинная строка переносится и разбивается по страницам
        val long = OfficeStore.paginateLines("x".repeat(200), maxLines = 2)
        assertEquals(2, long.size)
        assertEquals(3, long.flatten().size)
        // пустой текст — одна страница с одной пустой строкой
        val empty = OfficeStore.paginateLines("", maxLines = 2)
        assertEquals(1, empty.size)
        assertEquals(listOf(""), empty[0])
    }
}
