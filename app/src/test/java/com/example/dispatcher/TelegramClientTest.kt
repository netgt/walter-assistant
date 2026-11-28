package com.example.dispatcher

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramClientTest {
    @Test
    fun documentCaptionAndFileIdAreExposed() {
        val file = TdApi.File().apply { id = 42; size = 128 }
        val document = TdApi.Document().apply {
            fileName = "report.pdf"
            mimeType = "application/pdf"
            this.document = file
        }
        val content = TdApi.MessageDocument(document, TdApi.FormattedText("Посмотри", null))
        val text = TelegramClient.contentText(content)
        assertTrue(text.contains("Посмотри"))
        assertTrue(text.contains("report.pdf"))
        assertTrue(text.contains("fileId=42"))
    }

    @Test
    fun largestPhotoIsSelectedForDownload() {
        val small = TdApi.File().apply { id = 1; expectedSize = 100 }
        val large = TdApi.File().apply { id = 2; expectedSize = 1000 }
        val photo = TdApi.Photo().apply {
            sizes = arrayOf(
                TdApi.PhotoSize().apply { photo = small },
                TdApi.PhotoSize().apply { photo = large }
            )
        }
        val attachments = TelegramClient.attachments(TdApi.MessagePhoto(photo, null, null, false, false, false))
        assertEquals(2, attachments.single().fileId)
        assertEquals("photo-2.jpg", attachments.single().name)
    }

    @Test
    fun fileNameCannotEscapeDestination() {
        assertEquals("report.pdf", TelegramClient.safeFileName("../../report.pdf"))
        assertEquals("b.txt", TelegramClient.safeFileName("a/b.txt"))
        assertEquals("fallback", TelegramClient.safeFileName("", "fallback"))
    }

    @Test
    fun channelAndGroupKindsAreDistinct() {
        val channel = TdApi.Chat().apply { type = TdApi.ChatTypeSupergroup(1, true) }
        val group = TdApi.Chat().apply { type = TdApi.ChatTypeSupergroup(2, false) }
        assertEquals("канал", TelegramClient.chatKind(channel))
        assertEquals("группа", TelegramClient.chatKind(group))
    }

    @Test
    fun downloadNamesNeverOverwriteExistingFile() {
        val dir = java.nio.file.Files.createTempDirectory("telegram-test").toFile()
        val first = TelegramClient.uniqueTarget(dir, "report.pdf")
        first.writeText("one")
        val second = TelegramClient.uniqueTarget(dir, "report.pdf")
        assertEquals("report.pdf", first.name)
        assertEquals("report (2).pdf", second.name)
        dir.deleteRecursively()
    }

    @Test
    fun historyContainsRawMessageIds() {
        val chat = TdApi.Chat().apply {
            id = -1001
            title = "Канал"
            type = TdApi.ChatTypeSupergroup(1, true)
        }
        val history = TelegramClient.History(chat, listOf(
            TelegramClient.MessageSummary(99, chat.id, "Автор", 1000, "Текст", emptyList())
        ))
        val formatted = TelegramClient.formatHistory(history)
        assertTrue(formatted.contains("id=-1001"))
        assertTrue(formatted.contains("id=99"))
        assertTrue(formatted.contains("Текст"))
    }
}
