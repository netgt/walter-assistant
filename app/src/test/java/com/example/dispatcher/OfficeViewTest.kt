package com.example.dispatcher

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class OfficeViewTest {

    private fun temp(ext: String): File = File.createTempFile("officeview", ext).apply { deleteOnExit() }

    @Test
    fun docxBlocksWithFormattingAndTable() {
        val f = temp(".docx")
        val doc = XWPFDocument()
        val p1 = doc.createParagraph().apply { style = "Heading1" }
        p1.createRun().apply { setText("Заголовок"); isBold = true }
        val p2 = doc.createParagraph()
        p2.createRun().apply { setText("жирный "); isBold = true }
        p2.createRun().apply { setText("обычный") }
        val t = doc.createTable(2, 2)
        t.getRow(0).getCell(0).setText("a"); t.getRow(0).getCell(1).setText("b")
        t.getRow(1).getCell(0).setText("c"); t.getRow(1).getCell(1).setText("d")
        FileOutputStream(f).use { doc.write(it) }
        doc.close()

        val blocks = OfficeView.docxBlocks(f.absolutePath)
        val h = blocks[0] as DocBlock.Para
        assertEquals(1, h.heading)
        assertTrue(h.runs.first().bold)
        val p = blocks[1] as DocBlock.Para
        assertEquals("жирный ", p.runs[0].text)
        assertTrue(p.runs[0].bold)
        assertEquals("обычный", p.runs[1].text)
        assertFalse(p.runs[1].bold)
        val table = blocks.last() as DocBlock.Table
        assertEquals(listOf("a", "b"), table.rows[0])
        assertEquals(listOf("c", "d"), table.rows[1])
    }

    @Test
    fun xlsxSheetsWithHeader() {
        val f = temp(".xlsx")
        val wb = XSSFWorkbook()
        val sh = wb.createSheet("План")
        val font = wb.createFont().apply { bold = true }
        val style = wb.createCellStyle().apply { setFont(font) }
        sh.createRow(0).createCell(0).apply { setCellValue("Сумма"); cellStyle = style }
        sh.createRow(1).createCell(0).setCellValue("123")
        sh.createRow(2).createCell(0).setCellValue("456")
        FileOutputStream(f).use { wb.write(it) }
        wb.close()

        val sheets = OfficeView.xlsxSheets(f.absolutePath)
        assertEquals(1, sheets.size)
        val s = sheets[0]
        assertEquals("План", s.name)
        assertTrue(s.headerBold)
        assertEquals(listOf("Сумма"), s.rows[0])
        assertEquals(listOf("123"), s.rows[1])
        assertEquals(listOf("456"), s.rows[2])
    }

    @Test
    fun xlsxTrailingEmptyCellsTrimmed() {
        val f = temp(".xlsx")
        val wb = XSSFWorkbook()
        val sh = wb.createSheet("Л")
        val r = sh.createRow(0)
        r.createCell(0).setCellValue("x")
        r.createCell(5).setCellValue("")
        FileOutputStream(f).use { wb.write(it) }
        wb.close()
        val s = OfficeView.xlsxSheets(f.absolutePath).single()
        assertEquals(listOf("x"), s.rows[0])
        assertFalse(s.headerBold)
    }

    @Test
    fun pptxSlidesWithTitleAndBullets() {
        val f = temp(".pptx")
        val ppt = XMLSlideShow()
        val s1 = ppt.createSlide()
        s1.createTextBox().text = "Заголовок"
        s1.createTextBox().text = "пункт один\nпункт два"
        FileOutputStream(f).use { ppt.write(it) }
        ppt.close()

        val slides = OfficeView.pptxSlides(f.absolutePath)
        assertEquals(1, slides.size)
        assertEquals("Заголовок", slides[0].title)
        assertEquals(listOf("пункт один", "пункт два"), slides[0].bullets)
    }

    @Test
    fun emptyPptxGivesEmptyList() {
        val f = temp(".pptx")
        val ppt = XMLSlideShow()
        FileOutputStream(f).use { ppt.write(it) }
        ppt.close()
        assertEquals(emptyList<SlideView>(), OfficeView.pptxSlides(f.absolutePath))
    }

    @Test
    fun brokenFileGivesEmptyResults() {
        val f = temp(".docx")
        f.writeText("это не docx")
        assertNotNull(OfficeView.docxBlocks(f.absolutePath)) // не падает: блоки просто не найдутся
    }
}
