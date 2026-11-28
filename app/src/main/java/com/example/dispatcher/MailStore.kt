package com.example.dispatcher

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.BodyPart
import javax.mail.Folder
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.activation.FileDataSource

/**
 * Почта по IMAP (Яндекс: imap.yandex.ru:993, Gmail: imap.gmail.com:993 с паролем приложения).
 * Читает только заголовки и текст последних писем — сырые данные для модели.
 */
object MailStore {

    data class AttachmentInfo(val name: String, val mime: String, val size: Int)

    data class MailItem(
        val from: String,
        val subject: String,
        val date: String,
        val body: String,
        val at: Long = 0L,
        val uid: Long = 0L,
        val folder: String = "INBOX",
        val accountId: Long = 0L,
        val attachments: List<AttachmentInfo> = emptyList()
    )

    fun fetch(host: String, port: Int, user: String, pass: String, ssl: Boolean, count: Int, accountId: Long = 0L): List<MailItem> {
        val protocol = if (ssl) "imaps" else "imap"
        val props = Properties().apply {
            put("mail.store.protocol", protocol)
            put("mail.$protocol.host", host)
            put("mail.$protocol.port", port.toString())
            if (ssl) {
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.ssl.trust", "*")
            } else {
                put("mail.imap.starttls.enable", "true")
            }
            put("mail.$protocol.connectiontimeout", "15000")
            put("mail.$protocol.timeout", "20000")
        }
        val session = Session.getInstance(props, null)
        val store = session.getStore(protocol)
        try {
            store.connect(host, port, user, pass)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val uidFolder = inbox as? UIDFolder
            val out = mutableListOf<MailItem>()
            val total = inbox.messageCount
            val from = maxOf(1, total - count + 1)
            for (i in total downTo from) {
                val m = inbox.getMessage(i)
                val fromAddr = (m.from?.firstOrNull() as? InternetAddress)?.address
                    ?: m.from?.firstOrNull()?.toString() ?: ""
                out += MailItem(
                    from = fromAddr,
                    subject = m.subject ?: "",
                    date = formatDate(m.receivedDate ?: m.sentDate),
                    body = extractBody(m),
                    at = (m.receivedDate ?: m.sentDate)?.time ?: 0L,
                    uid = uidFolder?.getUID(m) ?: 0L,
                    folder = inbox.name,
                    accountId = accountId,
                    attachments = attachmentInfo(m)
                )
            }
            inbox.close(false)
            return out
        } finally {
            runCatching { store.close() }
        }
    }

    private fun extractBody(m: Message): String {
        return try {
            val text = when (val content = m.content) {
                is String -> if (m.isMimeType("text/html")) htmlToText(content) else content
                is MimeMultipart -> extractMultipart(content)
                else -> content.toString()
            }
            cleanBody(text)
        } catch (e: Exception) {
            ""
        }
    }

    /** Похоже ли на HTML-разметку (чтобы не показывать теги в карточке письма). */
    fun looksLikeHtml(s: String): Boolean =
        Regex("<[a-zA-Z!/][^>]*>").containsMatchIn(s) || s.contains("</") || s.contains("&lt;") || s.contains("&nbsp;")

    /**
     * Финальная зачистка тела письма: переносы, множественные пустые строки,
     * и страховка — если часть «text/plain» на самом деле содержит HTML, теги срезаются.
     */
    fun cleanBody(raw: String): String {
        val cleaned = raw.replace("\r\n", "\n").replace(Regex("\n{3,}"), "\n\n").trim().take(500)
        return if (looksLikeHtml(cleaned)) htmlToText(cleaned).take(500) else cleaned
    }

    private fun extractMultipart(mp: MimeMultipart): String {
        var plain: String? = null
        var html: String? = null
        for (i in 0 until mp.count) {
            val bp = runCatching { mp.getBodyPart(i) }.getOrNull() ?: continue
            if (bp.isMimeType("text/plain") && plain == null) {
                plain = bp.content?.toString()
            } else if (bp.isMimeType("text/html") && html == null) {
                html = bp.content?.toString()
            } else if (bp.isMimeType("multipart/*")) {
                val inner = runCatching { bp.content as? MimeMultipart }.getOrNull()
                if (inner != null) {
                    val t = extractMultipart(inner)
                    if (t.isNotBlank()) {
                        // вложенный мультипарт уже отдал готовый текст
                        if (html == null && plain == null) html = t else if (plain == null) plain = t
                    }
                }
            }
        }
        return plain ?: html?.let { htmlToText(it) } ?: ""
    }

    /** HTML-письмо → читаемый текст: теги, стили, скрипты и сущности. Тестируется в JVM. */
    fun htmlToText(s: String): String {
        var t = s
            .replace(Regex("(?is)<(style|script|head)[^>]*>.*?</(style|script|head)>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|tr|li|h[1-6])>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
        t = t
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&rsquo;", "'")
            .replace("&ldquo;", "\"")
            .replace("&rdquo;", "\"")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            }
        return t
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex(" ([.,!?;:)])"), "$1")
            .replace(Regex(" *\n *"), "\n")
            .replace(Regex("\n{2,}"), "\n")
            .trim()
    }

    internal fun attachmentInfo(message: Message): List<AttachmentInfo> {
        val out = mutableListOf<AttachmentInfo>()
        fun walk(part: BodyPart) {
            val name = runCatching { part.fileName }.getOrNull().orEmpty()
            if (name.isNotBlank()) {
                out += AttachmentInfo(name, part.contentType ?: "application/octet-stream", runCatching { part.size }.getOrDefault(-1))
            }
            val content = runCatching { part.content }.getOrNull()
            if (content is MimeMultipart) {
                for (i in 0 until content.count) runCatching { content.getBodyPart(i) }.getOrNull()?.let(::walk)
            }
        }
        runCatching {
            when (val content = message.content) {
                is MimeMultipart -> for (i in 0 until content.count) walk(content.getBodyPart(i))
                else -> Unit
            }
        }
        return out
    }

    private fun connect(host: String, port: Int, user: String, pass: String, ssl: Boolean) = run {
        val protocol = if (ssl) "imaps" else "imap"
        val props = Properties().apply {
            put("mail.store.protocol", protocol)
            put("mail.$protocol.host", host)
            put("mail.$protocol.port", port.toString())
            if (ssl) put("mail.imaps.ssl.enable", "true") else put("mail.imap.starttls.enable", "true")
            put("mail.$protocol.connectiontimeout", "15000")
            put("mail.$protocol.timeout", "20000")
        }
        val store = Session.getInstance(props, null).getStore(protocol)
        store.connect(host, port, user, pass)
        store
    }

    fun downloadAttachment(
        host: String, port: Int, user: String, pass: String, ssl: Boolean,
        uid: Long, requestedName: String, destinationDir: String
    ): String {
        val store = connect(host, port, user, pass, ssl)
        return try {
            val folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            val uidFolder = folder as UIDFolder
            val message = uidFolder.getMessageByUID(uid)
                ?: throw IllegalArgumentException("Письмо с UID $uid не найдено")
            fun find(part: BodyPart): BodyPart? {
                val name = runCatching { part.fileName }.getOrNull().orEmpty()
                if (name.isNotBlank() && (requestedName.isBlank() || name == requestedName)) return part
                val content = runCatching { part.content }.getOrNull()
                if (content is MimeMultipart) {
                    for (i in 0 until content.count) {
                        runCatching { content.getBodyPart(i) }.getOrNull()?.let(::find)?.let { return it }
                    }
                }
                return null
            }
            var found: BodyPart? = null
            when (val content = message.content) {
                is MimeMultipart -> for (i in 0 until content.count) {
                    found = find(content.getBodyPart(i))
                    if (found != null) break
                }
            }
            val part = found ?: throw IllegalArgumentException("Вложение «$requestedName» не найдено")
            val name = part.fileName?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
                ?: "attachment.bin"
            val dir = java.io.File(destinationDir).apply { mkdirs() }
            val out = java.io.File(dir, name)
            part.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out.absolutePath
        } finally {
            runCatching { store.close() }
        }
    }

    fun send(
        host: String, port: Int, user: String, pass: String, ssl: Boolean,
        to: String, subject: String, body: String, attachments: List<String>
    ) {
        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "20000")
            if (ssl) put("mail.smtp.ssl.enable", "true") else put("mail.smtp.starttls.enable", "true")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication = PasswordAuthentication(user, pass)
        })
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(user))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject, Charsets.UTF_8.name())
        }
        val multipart = MimeMultipart("mixed")
        multipart.addBodyPart(MimeBodyPart().apply { setText(body, Charsets.UTF_8.name()) })
        attachments.forEach { path ->
            val file = java.io.File(path)
            if (!file.isFile) throw IllegalArgumentException("Файл не найден: $path")
            multipart.addBodyPart(MimeBodyPart().apply {
                dataHandler = javax.activation.DataHandler(FileDataSource(file))
                fileName = file.name
            })
        }
        message.setContent(multipart)
        Transport.send(message)
    }

    private fun formatDate(d: Date?): String = d?.let {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(it)
    } ?: ""

    fun formatItems(items: List<MailItem>): String {
        if (items.isEmpty()) return "Писем нет (ящик пуст)."
        return "Последние письма (${items.size}):\n\n" + items.joinToString("\n\n") { m ->
            buildString {
                if (m.from.isNotBlank()) append("От: ").append(m.from).append("\n")
                if (m.subject.isNotBlank()) append("Тема: ").append(m.subject).append("\n")
                if (m.date.isNotBlank()) append("Дата: ").append(m.date).append("\n")
                if (m.body.isNotBlank()) append(m.body)
                if (m.uid > 0) append("\nUID: ").append(m.uid)
                if (m.attachments.isNotEmpty()) {
                    append("\nВложения: ")
                    append(m.attachments.joinToString(", ") { it.name })
                }
            }.trim()
        }
    }
}

/** Почтовый аккаунт: IMAP для чтения и SMTP для отправки (пароль — пароль приложения). */
data class MailAccount(
    val id: Long,
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val ssl: Boolean = true,
    val smtpHost: String = "",
    val smtpPort: Int = 465
)

/** Хранилище списка почтовых аккаунтов: JSON в настройках, чистые функции. */
object MailAccounts {

    fun toJson(list: List<MailAccount>): String {
        val arr = org.json.JSONArray()
        list.forEach { a ->
            arr.put(
                org.json.JSONObject()
                    .put("id", a.id)
                    .put("host", a.host)
                    .put("port", a.port)
                    .put("user", a.user)
                    .put("pass", a.pass)
                    .put("ssl", a.ssl)
                    .put("smtpHost", a.smtpHost)
                    .put("smtpPort", a.smtpPort)
            )
        }
        return arr.toString()
    }

    fun fromJson(raw: String?): List<MailAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MailAccount(
                    o.optLong("id"),
                    o.optString("host"),
                    o.optInt("port", 993),
                    o.optString("user"),
                    o.optString("pass"),
                    o.optBoolean("ssl", true),
                    o.optString("smtpHost"),
                    o.optInt("smtpPort", 465)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(
        list: List<MailAccount>, host: String, port: Int, user: String, pass: String,
        ssl: Boolean = true, smtpHost: String = "", smtpPort: Int = 465
    ): List<MailAccount> {
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1
        return list + MailAccount(id, host.trim(), port, user.trim(), pass, ssl, smtpHost.trim(), smtpPort)
    }

    fun remove(list: List<MailAccount>, id: Long): List<MailAccount> = list.filter { it.id != id }

    /** Миграция одиночных старых настроек в список из одного аккаунта. */
    fun fromLegacy(host: String, port: Int, user: String, pass: String): List<MailAccount> =
        if (host.isBlank() || user.isBlank() || pass.isBlank()) emptyList()
        else listOf(MailAccount(1L, host.trim(), port, user.trim(), pass, true))
}
