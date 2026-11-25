package com.example.dispatcher

import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale

/**
 * Яндекс Диск: чистые парсеры и форматирование.
 * WebDAV (webdav.yandex.ru, логин + пароль приложения) — файловые операции;
 * REST (cloud-api.yandex.net/v1, OAuth-токен) — только публичные ссылки.
 * Тесты — в JVM без Android.
 */
data class DiskItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val created: String,
    val mimeType: String = ""
)

/** Информация о Диске: владелец и занятое место. */
data class DiskInfo(val login: String, val used: Long, val total: Long)

/** Ошибка API Диска с HTTP-кодом — для честной диагностики пользователю. */
class DiskHttpException(val code: Int, message: String) : Exception(message)

object YandexDisk {

    const val API_BASE = "https://cloud-api.yandex.net/v1"
    const val WEBDAV_DEFAULT = "https://webdav.yandex.ru"

    /** Максимальный размер файла для чтения в чат (disk_read) — большее скачиваем на устройство. */
    const val MAX_CHAT_READ = 10L * 1024 * 1024

    /** «Диск:/foo», «диск://foo», «disk:/foo», «/foo», «foo» → «/foo»; «диск:/» → «/»; пусто → «». */
    fun normalizePath(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        val body = t.replace(Regex("^(?:[Dd]isk|[Дд]иск):/*"), "").trim('/')
        return if (body.isEmpty()) "/" else "/" + body
    }

    /** Кодирование пути для URL WebDAV: каждый сегмент отдельно, пробел — %20, «+» остаётся «+». */
    fun encodeWebdavPath(path: String): String =
        path.split('/').joinToString("/") { seg ->
            URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }

    /** Декодирование href из ответов WebDAV (%XX → UTF-8 байты; многосимвольные последовательности корректны). */
    fun decodeHref(href: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < href.length) {
            if (href[i] == '%' && i + 2 < href.length) {
                val v = href.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) {
                    bytes += v.toByte()
                    i += 3
                    continue
                }
            }
            bytes += href[i].toString().toByteArray(Charsets.UTF_8).toList()
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8).trim()
    }

    /** Название папки для заголовка листинга. */
    fun folderDisplay(p: String): String = if (p.isBlank() || p == "/") "корень Диска" else "«$p»"

    // ─── WebDAV: PROPFIND (multistatus XML, устойчив к префиксам пространств имён) ───

    /**
     * Список ресурсов из ответа PROPFIND. Разбирает каждый блок <response>:
     * href, тип (папка/файл), размер, дату изменения. Устойчив к префиксам (d:, D:, без префикса).
     */
    fun parsePropfind(xml: String): List<DiskItem> {
        val out = mutableListOf<DiskItem>()
        val respRe = Regex("""<[^>]*response[^>]*>""")
        var searchFrom = 0
        while (true) {
            val open = respRe.find(xml, searchFrom) ?: break
            val close = Regex("""</[^>]*response[^>]*>""").find(xml, open.range.last + 1) ?: break
            val block = xml.substring(open.range.last + 1, close.range.first)
            searchFrom = close.range.last + 1
            val href = Regex("""<[^>]*href[^>]*>([^<]*)</[^>]*href[^>]*>""")
                .find(block)?.groupValues?.get(1)?.let { decodeHref(it) } ?: continue
            if (href.isBlank()) continue
            val path = if (href.endsWith("/") && href != "/") href.dropLast(1) else href
            val name = path.trim('/').substringAfterLast('/').ifBlank { "Диск" }
            if (path == "/") continue // корень сам по себе — не элемент списка
            val isDir = Regex("""<[^>]*collection\s*/?>""").containsMatchIn(block)
            val size = Regex("""<[^>]*getcontentlength[^>]*>(\d+)<""")
                .find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val created = Regex("""<[^>]*getlastmodified[^>]*>([^<]*)<""")
                .find(block)?.groupValues?.get(1)?.let { formatRfcDate(it) } ?: ""
            out += DiskItem(name, path, isDir, size, created)
        }
        return out
    }

    /** Занятое и общее место из PROPFIND с квотами (RFC 4331). */
    fun parseQuota(xml: String): Pair<Long, Long>? {
        val used = Regex("""<[^>]*quota-used-bytes[^>]*>(\d+)<""").find(xml)?.groupValues?.get(1)?.toLongOrNull()
        val total = Regex("""<[^>]*quota-available-bytes[^>]*>(\d+)<""").find(xml)?.groupValues?.get(1)?.toLongOrNull()
        if (used == null && total == null) return null
        // RFC 4331: quota-available-bytes — свободное место, общее = used + available
        val u = used ?: 0L
        return if (total != null) u to (u + total) else u to 0L
    }

    // ─── REST: только публичные ссылки ───

    /** href из ответов download/upload/publish. */
    fun parseLink(json: String): String? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return o.optString("href").takeIf { it.isNotBlank() }
    }

    /** GET /v1/disk: владелец и занятое место (проверка OAuth-токена). */
    fun parseInfo(json: String): DiskInfo? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val user = o.optJSONObject("user") ?: return null
        val login = user.optString("login").ifBlank { user.optString("display_name") }
        if (login.isBlank()) return null
        return DiskInfo(login, o.optLong("used_space", 0L), o.optLong("total_space", 0L))
    }

    // ─── Форматирование для модели ───

    /** Листинг папки: папки первыми, потом файлы; имена, размеры, даты, полные пути. */
    fun formatItems(folderPath: String, items: List<DiskItem>): String {
        if (items.isEmpty()) return "Папка на Диске пуста (${folderDisplay(folderPath)})."
        val sorted = items.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
        if (sorted.size == 1 && !sorted[0].isDir) {
            val f = sorted[0]
            return "Файл на Диске: ${f.name} [${f.path}] — ${FileStore.fmtSize(f.size)}" +
                if (f.created.isNotBlank()) " [${f.created}]" else ""
        }
        return "Яндекс Диск, ${folderDisplay(folderPath)} (${sorted.size}):\n" + sorted.joinToString("\n") { it ->
            if (it.isDir) "[папка] ${it.name} [${it.path}]"
            else "${it.name} [${it.path}] — ${FileStore.fmtSize(it.size)}" +
                if (it.created.isNotBlank()) " [${it.created}]" else ""
        }
    }

    /** Результат поиска по имени для модели. */
    fun formatMatches(query: String, items: List<DiskItem>, truncated: Boolean): String {
        if (items.isEmpty()) return "На Диске не найдено файлов по запросу «$query»."
        return "Найдено на Диске по «$query» (${items.size}${if (truncated) ", показаны первые совпадения" else ""}):\n" +
            items.joinToString("\n") { "${it.name} [${it.path}] — ${FileStore.fmtSize(it.size)}" }
    }

    // ─── Диагностика ошибок ───

    /** Честная диагностика ошибки WebDAV (логин + пароль приложения). */
    fun describeWebdavError(e: Throwable): String = when (e) {
        is DiskHttpException -> webdavErrorMessage(e.code)
        else -> e.message ?: e.javaClass.simpleName
    }

    fun webdavErrorMessage(code: Int): String = when (code) {
        401, 403 -> "Неверный логин или пароль приложения (или пароль не имеет доступа к Диску). Проверь аккаунт: «+» → «Настройки» → «Яндекс Диск»."
        404 -> "На Диске нет такого файла или папки."
        409 -> "На Диске уже есть файл или папка с таким именем (или такой папки нет)."
        423 -> "Ресурс на Диске заблокирован."
        429 -> "Слишком много запросов к Диску — повтори через минуту."
        507 -> "На Диске не хватает места."
        else -> if (code >= 500) "Сервер Яндекс Диска недоступен (HTTP $code)."
        else "Ошибка Яндекс Диска (HTTP $code)."
    }

    /** Честная диагностика ошибки REST (публичные ссылки, OAuth-токен). */
    fun describeError(e: Throwable): String = when (e) {
        is DiskHttpException -> errorMessage(e.code)
        else -> e.message ?: e.javaClass.simpleName
    }

    fun errorMessage(code: Int): String = when (code) {
        401 -> "OAuth-токен неверен, просрочен или не имеет доступа к Диску. Проверь токен: «+» → «Настройки» → «Яндекс Диск»."
        403 -> "Нет прав: токену нужны права cloud_api:disk.read и cloud_api:disk.write."
        404 -> "На Диске нет такого файла или папки."
        409 -> "На Диске уже есть файл или папка с таким именем."
        423 -> "Яндекс Диск временно недоступен (технические работы)."
        429 -> "Слишком много запросов к Диску — повтори через минуту."
        else -> if (code >= 500) "Сервер Яндекс Диска недоступен (HTTP $code)."
        else "Ошибка Яндекс Диска (HTTP $code)."
    }

    /** Дата WebDAV «Mon, 17 Nov 2026 09:30:00 GMT» → «17.11.26». */
    private fun formatRfcDate(s: String): String = runCatching {
        val df = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(df.parse(s.trim()))
    }.getOrDefault("")

    private fun formatCreated(iso: String): String = runCatching {
        val ms = Instant.parse(iso).toEpochMilli()
        SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(java.util.Date(ms))
    }.getOrDefault("")
}
