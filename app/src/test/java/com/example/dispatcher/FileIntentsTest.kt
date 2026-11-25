package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileIntentsTest {

    @Test
    fun parseQueryField() {
        val a = parseAction(
            """{"action":"find_files","app":null,"title":null,"when":null,"address":null,"reply":null,"query":"договор"}"""
        )
        assertEquals("find_files", a.action)
        assertEquals("договор", a.query)
    }

    @Test
    fun formatSizes() {
        assertEquals("512 Б", FileStore.fmtSize(512))
        assertEquals("2,0 КБ", FileStore.fmtSize(2048))
        assertEquals("1,5 МБ", FileStore.fmtSize((1.5 * 1024 * 1024).toLong()))
    }


}
