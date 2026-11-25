package com.example.dispatcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder

/**
 * Яндекс Диск:
 * - файловые операции — WebDAV (webdav.yandex.ru), Basic-авторизация: логин + пароль приложения;
 * - публичные ссылки — REST (cloud-api.yandex.net/v1), OAuth-токен.
 * Ответы разбираются чистыми парсерами YandexDisk; ошибки HTTP — DiskHttpException
 * с понятной диагностикой. Хранилище и анализ — на стороне модели (правило №3).
 */

/** Аккаунт Яндекс Диска (WebDAV): логин + пароль приложения. */
data class DiskAccount(
    val id: Long,
    val baseUrl: String,
    val user: String,
    val pass: String
)

/** Хранилище списка аккаунтов Диска: JSON в настройках, чистые функции. */
object DiskAccounts {

    fun toJson(list: List<DiskAccount>): String {
        val arr = org.json.JSONArray()
        list.forEach { a ->
            arr.put(
                org.json.JSONObject()
                    .put("id", a.id)
                    .put("baseUrl", a.baseUrl)
                    .put("user", a.user)
                    .put("pass", a.pass)
            )
        }
        return arr.toString()
    }

    fun fromJson(raw: String?): List<DiskAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DiskAccount(
                    o.optLong("id"),
                    o.optString("baseUrl", YandexDisk.WEBDAV_DEFAULT).ifBlank { YandexDisk.WEBDAV_DEFAULT },
                    o.optString("user"),
                    o.optString("pass")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(list: List<DiskAccount>, baseUrl: String, user: String, pass: String): List<DiskAccount> {
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1
        val url = baseUrl.trim().ifBlank { YandexDisk.WEBDAV_DEFAULT }.trimEnd('/')
        return list + DiskAccount(id, url, user.trim(), pass)
    }

    fun remove(list: List<DiskAccount>, id: Long): List<DiskAccount> = list.filter { it.id != id }
}

object DiskStore {

    private val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:"><d:prop>
        <d:resourcetype/><d:getcontentlength/><d:getlastmodified/>
        </d:prop></d:propfind>""".trimIndent()

    private val QUOTA_BODY = """<?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:"><d:prop>
        <d:quota-available-bytes/><d:quota-used-bytes/>
        </d:prop></d:propfind>""".trimIndent()

    private fun auth(user: String, pass: String) = Credentials.basic(user, pass)

    private fun webdavUrl(acc: DiskAccount, path: String): String =
        acc.baseUrl + YandexDisk.encodeWebdavPath(if (path.isBlank()) "/" else path)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun execute(client: OkHttpClient, req: Request): String {
        val resp = client.newCall(req).execute()
        resp.use { r ->
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) throw DiskHttpException(r.code, body.take(300))
            return body
        }
    }

    private fun propfind(client: OkHttpClient, acc: DiskAccount, path: String, depth: String, body: String): String {
        val req = Request.Builder()
            .url(webdavUrl(acc, path))
            .header("Authorization", auth(acc.user, acc.pass))
            .header("Depth", depth)
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
            .build()
        return execute(client, req)
    }

    /** Проверка аккаунта и место на Диске (PROPFIND корня с квотами). */
    suspend fun info(client: OkHttpClient, acc: DiskAccount): DiskInfo {
        val raw = withContext(Dispatchers.IO) {
            propfind(client, acc, "/", "0", QUOTA_BODY)
        }
        val quota = YandexDisk.parseQuota(raw) ?: return DiskInfo(acc.user, 0L, 0L)
        return DiskInfo(acc.user, quota.first, quota.second)
    }

    /** Содержимое папки (пустая строка — корень). */
    suspend fun list(client: OkHttpClient, acc: DiskAccount, folder: String): List<DiskItem> {
        val p = folder.ifBlank { "/" }
        val raw = withContext(Dispatchers.IO) { propfind(client, acc, p, "1", PROPFIND_BODY) }
        return YandexDisk.parsePropfind(raw)
    }

    /**
     * Поиск файла по имени: рекурсивный обход папок (PROPFIND depth 1, как обход диска
     * в FileStore) с бюджетом папок — без Depth: infinity, который сервер может ограничивать.
     */
    suspend fun files(client: OkHttpClient, acc: DiskAccount, nameQuery: String, maxMatches: Int = 60): Pair<List<DiskItem>, Boolean> {
        val q = nameQuery.trim()
        val out = mutableListOf<DiskItem>()
        var truncated = false
        var budget = 60 // бюджет PROPFIND-запросов папок
        val queue = ArrayDeque<String>()
        queue.add("/")
        while (queue.isNotEmpty() && budget > 0) {
            val dir = queue.removeFirst()
            budget--
            val raw = runCatching { propfind(client, acc, dir, "1", PROPFIND_BODY) }.getOrElse { throw it }
            val items = YandexDisk.parsePropfind(raw)
            for (it in items) {
                if (it.isDir) { queue.add(it.path); continue }
                if (q.isNotEmpty() && !it.name.contains(q, ignoreCase = true)) continue
                out += it
                if (out.size >= maxMatches) { truncated = true; return out to truncated }
            }
        }
        if (queue.isNotEmpty() || budget == 0) truncated = out.isNotEmpty()
        return out to truncated
    }

    /** Метаданные файла/папки (PROPFIND depth 0). */
    suspend fun meta(client: OkHttpClient, acc: DiskAccount, path: String): DiskItem {
        val raw = withContext(Dispatchers.IO) { propfind(client, acc, path, "0", PROPFIND_BODY) }
        return YandexDisk.parsePropfind(raw).firstOrNull()
            ?: throw DiskHttpException(200, "Непонятный ответ сервера Диска")
    }

    /** Скачать файл с Диска в локальный файл; возвращает количество байтов. */
    suspend fun download(client: OkHttpClient, acc: DiskAccount, path: String, dest: File): Long {
        dest.parentFile?.mkdirs()
        val req = Request.Builder()
            .url(webdavUrl(acc, path))
            .header("Authorization", auth(acc.user, acc.pass))
            .build()
        return withContext(Dispatchers.IO) {
            val resp = client.newCall(req).execute()
            resp.use { r ->
                if (!r.isSuccessful) throw DiskHttpException(r.code, r.body?.string()?.take(300) ?: "")
                val src = r.body ?: throw DiskHttpException(200, "Пустое тело ответа")
                var total = 0L
                src.byteStream().use { ins ->
                    dest.outputStream().use { outs ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            outs.write(buf, 0, n)
                            total += n
                        }
                    }
                }
                total
            }
        }
    }

    /** Загрузить байты на Диск (PUT). Возвращает "OK". */
    suspend fun uploadBytes(client: OkHttpClient, acc: DiskAccount, path: String, bytes: ByteArray): String {
        val req = Request.Builder()
            .url(webdavUrl(acc, path))
            .header("Authorization", auth(acc.user, acc.pass))
            .put(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        withContext(Dispatchers.IO) { execute(client, req) }
        return "OK"
    }

    /** Загрузить локальный файл на Диск. */
    suspend fun uploadFile(client: OkHttpClient, acc: DiskAccount, path: String, src: File): String {
        val bytes = withContext(Dispatchers.IO) {
            if (!src.exists() || !src.isFile) throw DiskHttpException(404, "Локальный файл не найден: ${src.name}")
            if (src.length() > 200L * 1024 * 1024) throw DiskHttpException(413, "Файл больше 200 МБ — загрузка на Диск не поддерживается.")
            src.readBytes()
        }
        return uploadBytes(client, acc, path, bytes)
    }

    /** Удалить файл/папку на Диске. */
    suspend fun deleteResource(client: OkHttpClient, acc: DiskAccount, path: String): String {
        val req = Request.Builder()
            .url(webdavUrl(acc, path))
            .header("Authorization", auth(acc.user, acc.pass))
            .delete()
            .build()
        withContext(Dispatchers.IO) { execute(client, req) }
        return "OK"
    }

    /** Переместить файл/папку на Диске (MOVE, from → to). */
    suspend fun moveResource(client: OkHttpClient, acc: DiskAccount, from: String, to: String): String {
        val req = Request.Builder()
            .url(webdavUrl(acc, from))
            .header("Authorization", auth(acc.user, acc.pass))
            .header("Destination", webdavUrl(acc, to))
            .header("Overwrite", "T")
            .method("MOVE", null)
            .build()
        withContext(Dispatchers.IO) { execute(client, req) }
        return "OK"
    }

    /** Публичная ссылка на файл (REST, PUT /v1/disk/resources/publish; OAuth-токен). */
    suspend fun publishResource(client: OkHttpClient, token: String, path: String): String {
        val raw = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${YandexDisk.API_BASE}/disk/resources/publish?path=${enc(path)}")
                .header("Authorization", "OAuth $token")
                .put(ByteArray(0).toRequestBody(null))
                .build()
            val resp = client.newCall(req).execute()
            resp.use { r ->
                val body = r.body?.string() ?: ""
                if (!r.isSuccessful) throw DiskHttpException(r.code, body.take(300))
                body
            }
        }
        return YandexDisk.parseLink(raw) ?: throw DiskHttpException(200, "Яндекс не отдал публичную ссылку")
    }

    /** Проверка OAuth-токена (REST GET /v1/disk): владелец и занятое место. */
    suspend fun publishInfo(client: OkHttpClient, token: String): DiskInfo? {
        val raw = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${YandexDisk.API_BASE}/disk")
                .header("Authorization", "OAuth $token")
                .build()
            val resp = client.newCall(req).execute()
            resp.use { r ->
                val body = r.body?.string() ?: ""
                if (!r.isSuccessful) throw DiskHttpException(r.code, body.take(300))
                body
            }
        }
        return YandexDisk.parseInfo(raw)
    }
}
