package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FileStoreWalkTest {

    @Test
    fun walksAndSearchesTempDir() {
        val root = File.createTempFile("walt", "")
        root.delete()
        root.mkdirs()
        val sub = File(root, "docs")
        sub.mkdirs()
        val f1 = File(root, "Договор 2026.pdf")
        f1.writeText("x")
        f1.setLastModified(1000L)
        val f2 = File(sub, "Заметка.txt")
        f2.writeText("y")
        f2.setLastModified(2000L)

        val found = FileStore.walkLegacy(root, "договор", 0, 10, pathOf = { it.absolutePath })
        assertEquals(1, found.size)
        assertEquals("Договор 2026.pdf", found.first().name)

        val recent = FileStore.walkLegacy(root, null, 10, 0, pathOf = { it.absolutePath })
        assertEquals("Заметка.txt", recent.first().name)
        assertEquals(2, recent.size)

        root.deleteRecursively()
    }
}
