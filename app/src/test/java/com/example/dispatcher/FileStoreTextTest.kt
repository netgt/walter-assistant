package com.example.dispatcher

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class FileStoreTextTest {

    @Test
    fun kindOfTable() {
        assertEquals("text", FileStore.kindOf("notes.txt"))
        assertEquals("text", FileStore.kindOf("README.md"))
        assertEquals("text", FileStore.kindOf("data.JSON"))
        assertEquals("image", FileStore.kindOf("photo.JPG"))
        assertEquals("image", FileStore.kindOf("pic.png"))
        assertEquals("apk", FileStore.kindOf("app.apk"))
        assertEquals("video", FileStore.kindOf("clip.mp4"))
        assertEquals("other", FileStore.kindOf("archive.zip"))
        assertEquals("other", FileStore.kindOf("без расширения"))
        assertEquals("doc", FileStore.kindOf("report.docx"))
        assertEquals("sheet", FileStore.kindOf("table.xlsx"))
        assertEquals("slides", FileStore.kindOf("deck.pptx"))
        assertEquals("pdf", FileStore.kindOf("book.pdf"))
    }

    @Test
    fun grepFindsText() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_test_${System.nanoTime()}")
        dir.mkdirs()
        try {
            File(dir, "a.txt").writeText("про налоги много слов\nвторая строка")
            File(dir, "b.md").writeText("ничего интересного")
            File(dir, "photo.jpg").writeText("налог") // не текстовый — не должен попасть
            val hits = FileStore.grepText(dir, "налоги", 10)
            assertEquals(1, hits.size)
            assertEquals("a.txt", hits[0].first.name)
            assertTrue(hits[0].second.contains("налоги"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun grepMiss() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_test2_${System.nanoTime()}")
        dir.mkdirs()
        try {
            File(dir, "x.txt").writeText("нет искомого")
            assertTrue(FileStore.grepText(dir, "налог", 10).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readTextLimit() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_test3_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val f = File(dir, "big.txt")
            f.writeText("0123456789".repeat(1000))
            val snip = FileStore.readText(f.absolutePath, 100)
            assertEquals(100, snip.length)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeTextReplacesWholeFile() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_test4_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val f = File(dir, "note.txt")
            f.writeText("Старый текст")
            val res = FileStore.writeText(f.absolutePath, "Новый текст целиком")
            assertTrue(res.contains("записан"))
            assertEquals("Новый текст целиком", f.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeTextDocxRecreates() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_test5_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val f = File(dir, "doc.docx")
            FileOutputStream(f).use { out ->
                val doc = XWPFDocument()
                doc.createParagraph().createRun().setText("Старый")
                doc.write(out)
                doc.close()
            }
            val res = FileStore.writeText(f.absolutePath, "Новый абзац")
            assertTrue(res.contains("docx"))
            assertTrue(FileStore.readText(f.absolutePath).contains("Новый абзац"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeTextXlsxRoundtrip() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_test6_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val f = File(dir, "t.xlsx")
            f.writeText("x") // файл пересоздаётся целиком
            val res = FileStore.writeText(f.absolutePath, "## Траты\nПродукты | 1200\nТранспорт | 300")
            assertTrue(res.contains("xlsx"))
            val text = FileStore.readText(f.absolutePath)
            assertTrue(text.contains("Траты"))
            assertTrue(text.contains("Продукты | 1200"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeTextPptxRoundtrip() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_test7_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val f = File(dir, "s.pptx")
            f.writeText("x")
            val res = FileStore.writeText(f.absolutePath, "## Слайд 1\nЗаголовок\n## Слайд 2\nТекст")
            assertTrue(res.contains("pptx"))
            val text = FileStore.readText(f.absolutePath)
            assertTrue(text.contains("## Слайд 2"))
            assertTrue(text.contains("Заголовок"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun createTextNewFile() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_create_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val f = File(dir, "new.txt")
            val res = FileStore.createText(f.absolutePath, "Привет, Walter")
            assertTrue(res.contains("создан"))
            assertTrue(f.exists())
            assertEquals("Привет, Walter", f.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun createTextRefusesExisting() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_create2_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val f = File(dir, "exists.txt")
            f.writeText("старое")
            val res = FileStore.createText(f.absolutePath, "новое")
            assertTrue(res.contains("уже существует"))
            assertEquals("старое", f.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun createTextMakesParentDirs() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_create3_${System.nanoTime()}")
        try {
            val f = File(File(dir, "a/b"), "deep.txt")
            val res = FileStore.createText(f.absolutePath, "данные")
            assertTrue(res.contains("создан"))
            assertTrue(f.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun resolveFilePathByNameAndRelative() {
        val dir = File(System.getProperty("java.io.tmpdir"), "walter_test_resolve_${System.nanoTime()}")
        val sub = File(dir, "docs")
        sub.mkdirs()
        try {
            val f = File(sub, "TODO.txt")
            f.writeText("x")
            assertEquals(f.absolutePath, FileStore.resolveFilePath("TODO.txt", dir)?.absolutePath)
            assertEquals(f.absolutePath, FileStore.resolveFilePath("docs/TODO.txt", dir)?.absolutePath)
            assertEquals(f.absolutePath, FileStore.resolveFilePath(f.absolutePath, dir)?.absolutePath)
            assertEquals(null, FileStore.resolveFilePath("нет_такого.txt", dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
