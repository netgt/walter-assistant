package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class MailStoreAttachmentTest {
    @Test
    fun attachmentNamesAndMimeAreExposed() {
        val message = MimeMessage(Session.getInstance(Properties()))
        val multipart = MimeMultipart("mixed")
        multipart.addBodyPart(MimeBodyPart().apply { setText("Текст") })
        multipart.addBodyPart(MimeBodyPart().apply {
            dataHandler = javax.activation.DataHandler("Тест", "text/plain")
            fileName = "test.pdf"
        })
        message.setContent(multipart)
        val attachments = MailStore.attachmentInfo(message)
        assertEquals(listOf("test.pdf"), attachments.map { it.name })
    }
}
