package com.example.dispatcher

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart

/**
 * Почта по IMAP (Яндекс: imap.yandex.ru:993, Gmail: imap.gmail.com:993 с паролем приложения).
 * Читает только заголовки и текст последних писем — сырые данные для модели.
 */
object MailStore {

    data class MailItem(
        val from: String,
        val subject: String,
        val date: String,
        val body: String,
        val at: Long = 0L
    )

    fun fetch(host: String, port: Int, user: String, pass: String, ssl: Boolean, count: Int): List<MailItem> {
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
                    at = (m.receivedDate ?: m.sentDate)?.time ?: 0L
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
            }.trim()
        }
    }
}

/** Почтовый аккаунт по IMAP (Яндекс/Gmail; пароль — пароль приложения). */
data class MailAccount(
    val id: Long,
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val ssl: Boolean = true
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
                    o.optBoolean("ssl", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(list: List<MailAccount>, host: String, port: Int, user: String, pass: String, ssl: Boolean = true): List<MailAccount> {
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1
        return list + MailAccount(id, host.trim(), port, user.trim(), pass, ssl)
    }

    fun remove(list: List<MailAccount>, id: Long): List<MailAccount> = list.filter { it.id != id }

    /** Миграция одиночных старых настроек в список из одного аккаунта. */
    fun fromLegacy(host: String, port: Int, user: String, pass: String): List<MailAccount> =
        if (host.isBlank() || user.isBlank() || pass.isBlank()) emptyList()
        else listOf(MailAccount(1L, host.trim(), port, user.trim(), pass, true))
}
