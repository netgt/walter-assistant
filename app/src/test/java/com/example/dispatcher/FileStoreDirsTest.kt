package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileStoreDirsTest {

    private fun makeTree(): File {
        val root = File.createTempFile("waltdir", "")
        root.delete(); root.mkdirs()
        val docs = File(root, "Documents")
        docs.mkdirs()
        File(root, "Download").mkdirs()
        File(root, "a.txt").writeText("x")
        File(docs, "b.txt").writeText("y")
        return root
    }

    @Test
    fun topDirsListsOnlyFolders() {
        val root = makeTree()
        val dirs = FileStore.topDirs(root)
        assertEquals(2, dirs.size)
        assertEquals("Documents", dirs.first().name)
        assertTrue(dirs.all { it.isDir })
        root.deleteRecursively()
    }

    @Test
    fun listDirPutsFoldersFirst() {
        val root = makeTree()
        val items = FileStore.listDir(root)
        assertEquals(3, items.size)
        assertTrue(items.first().isDir)
        assertEquals("a.txt", items.last().name)
        root.deleteRecursively()
    }

    @Test
    fun resolveDirByName() {
        val root = makeTree()
        val found = FileStore.resolveDir(root, "documents")
        assertNotNull(found)
        assertEquals("Documents", found!!.name)
        root.deleteRecursively()
    }

    @Test
    fun resolveDirMissReturnsNull() {
        val root = makeTree()
        assertNull(FileStore.resolveDir(root, "нет такой папки"))
        root.deleteRecursively()
    }
}
