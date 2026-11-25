package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexDiskTest {

    private val multistatus = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>/Документы/</d:href>
            <d:propstat><d:prop>
              <d:resourcetype><d:collection/></d:resourcetype>
              <d:getlastmodified>Mon, 17 Nov 2025 09:30:00 GMT</d:getlastmodified>
            </d:prop></d:propstat>
          </d:response>
          <d:response>
            <d:href>/%D0%94%D0%BE%D0%BA%D1%83%D0%BC%D0%B5%D0%BD%D1%82%D1%8B/%D0%9E%D1%82%D1%87%D1%91%D1%82.docx</d:href>
            <d:propstat><d:prop>
              <d:resourcetype/>
              <d:getcontentlength>24576</d:getcontentlength>
              <d:getlastmodified>Tue, 15 Nov 2026 10:20:00 GMT</d:getlastmodified>
            </d:prop></d:propstat>
          </d:response>
          <d:response>
            <d:href>/note.txt</d:href>
            <d:propstat><d:prop>
              <d:resourcetype/>
              <d:getcontentlength>1024</d:getcontentlength>
            </d:prop></d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    private val quotaXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:">
          <D:response>
            <D:href>/</D:href>
            <D:propstat><D:prop>
              <D:quota-available-bytes>5368709120</D:quota-available-bytes>
              <D:quota-used-bytes>5368709120</D:quota-used-bytes>
            </D:prop></D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    // ─── пути ───

    @Test
    fun normalizePathVariants() {
        assertEquals("", YandexDisk.normalizePath(""))
        assertEquals("", YandexDisk.normalizePath("   "))
        assertEquals("/foo", YandexDisk.normalizePath("foo"))
        assertEquals("/foo", YandexDisk.normalizePath("/foo"))
        assertEquals("/foo/bar", YandexDisk.normalizePath("disk:/foo/bar"))
        assertEquals("/foo/bar", YandexDisk.normalizePath("Диск:/foo/bar"))
        assertEquals("/foo/bar", YandexDisk.normalizePath(" диск://foo/bar "))
        assertEquals("/", YandexDisk.normalizePath("диск:/"))
    }

    @Test
    fun encodeWebdavPathEncodesSegments() {
        assertEquals("", YandexDisk.encodeWebdavPath(""))
        assertEquals("/", YandexDisk.encodeWebdavPath("/"))
        assertEquals("/%D0%94%D0%BE%D0%BA%D1%83%D0%BC%D0%B5%D0%BD%D1%82%D1%8B/%D0%9E%D1%82%D1%87%D1%91%D1%82.docx", YandexDisk.encodeWebdavPath("/Документы/Отчёт.docx"))
        assertEquals("/%D0%9C%D0%BE%D0%B9%20%D1%84%D0%B0%D0%B9%D0%BB.txt", YandexDisk.encodeWebdavPath("/Мой файл.txt"))
        // плюс — зарезервированный символ пути: кодируется, чтобы не стал пробелом
        assertEquals("/a%2Bb", YandexDisk.encodeWebdavPath("/a+b"))
    }

    @Test
    fun decodeHrefWorks() {
        assertEquals("/Документы/Отчёт.docx", YandexDisk.decodeHref("/%D0%94%D0%BE%D0%BA%D1%83%D0%BC%D0%B5%D0%BD%D1%82%D1%8B/%D0%9E%D1%82%D1%87%D1%91%D1%82.docx"))
        assertEquals("/a+b.txt", YandexDisk.decodeHref("/a+b.txt"))
    }

    @Test
    fun folderDisplayWorks() {
        assertEquals("корень Диска", YandexDisk.folderDisplay(""))
        assertEquals("корень Диска", YandexDisk.folderDisplay("/"))
        assertEquals("«/Документы»", YandexDisk.folderDisplay("/Документы"))
    }

    // ─── PROPFIND ───

    @Test
    fun parsePropfindListsResources() {
        val items = YandexDisk.parsePropfind(multistatus)
        assertEquals(3, items.size)
        val dir = items[0]
        assertEquals("Документы", dir.name)
        assertEquals("/Документы", dir.path)
        assertTrue(dir.isDir)
        val doc = items[1]
        assertEquals("Отчёт.docx", doc.name)
        assertEquals("/Документы/Отчёт.docx", doc.path)
        assertFalse(doc.isDir)
        assertEquals(24576L, doc.size)
        assertEquals("15.11.26", doc.created)
        val note = items[2]
        assertEquals("note.txt", note.name)
        assertEquals(1024L, note.size)
    }

    @Test
    fun parsePropfindBadInput() {
        assertEquals(emptyList<DiskItem>(), YandexDisk.parsePropfind("не xml"))
        assertEquals(emptyList<DiskItem>(), YandexDisk.parsePropfind(""))
    }

    @Test
    fun parseQuotaWorks() {
        val q = YandexDisk.parseQuota(quotaXml)
        assertEquals(5368709120L, q!!.first)
        assertEquals(10737418240L, q.second)
        assertNull(YandexDisk.parseQuota("<xml></xml>"))
    }

    // ─── REST (публичные ссылки) ───

    @Test
    fun parseLinkFromHref() {
        assertEquals("https://yadi.sk/i/abc", YandexDisk.parseLink("""{"href": "https://yadi.sk/i/abc", "method": "GET"}"""))
        assertNull(YandexDisk.parseLink("""{"href": ""}"""))
        assertNull(YandexDisk.parseLink("не json"))
    }

    @Test
    fun parseInfoWithUserObject() {
        val info = YandexDisk.parseInfo("""
            {"user": {"country": "ru", "login": "alex", "display_name": "Alex"}, "total_space": 10737418240, "used_space": 5368709120}
        """.trimIndent())
        assertEquals("alex", info!!.login)
        assertEquals(5368709120L, info.used)
        assertEquals(10737418240L, info.total)
        assertNull(YandexDisk.parseInfo("""{"total_space": 1}"""))
    }

    // ─── форматирование ───

    @Test
    fun formatItemsSortsDirsFirst() {
        val items = YandexDisk.parsePropfind(multistatus)
        val text = YandexDisk.formatItems("/", items)
        assertTrue(text.contains("корень Диска"))
        assertTrue(text.contains("[папка] Документы"))
        assertTrue(text.contains("Отчёт.docx [/Документы/Отчёт.docx]"))
        assertTrue(text.contains("24,0 КБ"))
        // папка идёт раньше файлов
        assertTrue(text.indexOf("[папка]") < text.indexOf("Отчёт.docx"))
    }

    @Test
    fun formatItemsSingleFile() {
        val items = YandexDisk.parsePropfind("""
            <d:multistatus xmlns:d="DAV:"><d:response>
              <d:href>/note.txt</d:href>
              <d:propstat><d:prop><d:resourcetype/><d:getcontentlength>1024</d:getcontentlength></d:prop></d:propstat>
            </d:response></d:multistatus>
        """.trimIndent())
        val text = YandexDisk.formatItems("/", items)
        assertTrue(text.startsWith("Файл на Диске: note.txt"))
    }

    @Test
    fun formatItemsEmpty() {
        assertTrue(YandexDisk.formatItems("", emptyList()).contains("пуста"))
    }

    @Test
    fun formatMatches() {
        val items = listOf(DiskItem("TODO.txt", "/TODO.txt", false, 1024, ""))
        val text = YandexDisk.formatMatches("todo", items, false)
        assertTrue(text.contains("«todo»"))
        assertTrue(text.contains("TODO.txt [/TODO.txt]"))
        assertTrue(YandexDisk.formatMatches("нет такого", emptyList(), false).contains("не найдено"))
        assertTrue(YandexDisk.formatMatches("todo", items, true).contains("показаны первые"))
    }

    // ─── ошибки ───

    @Test
    fun webdavErrorsAreHonest() {
        assertTrue(YandexDisk.webdavErrorMessage(401).contains("пароль приложения"))
        assertTrue(YandexDisk.webdavErrorMessage(404).contains("нет такого файла"))
        assertTrue(YandexDisk.webdavErrorMessage(409).contains("уже есть"))
        assertTrue(YandexDisk.webdavErrorMessage(429).contains("Слишком много запросов"))
        assertTrue(YandexDisk.webdavErrorMessage(507).contains("не хватает места"))
        assertTrue(YandexDisk.webdavErrorMessage(500).contains("недоступен"))
    }

    @Test
    fun restErrorsAreHonest() {
        assertTrue(YandexDisk.errorMessage(401).contains("OAuth-токен"))
        assertTrue(YandexDisk.errorMessage(403).contains("cloud_api:disk.read"))
        assertTrue(YandexDisk.errorMessage(404).contains("нет такого файла"))
    }

    @Test
    fun describeErrorsUseHttpCode() {
        assertTrue(YandexDisk.describeWebdavError(DiskHttpException(401, "raw")).contains("пароль приложения"))
        assertTrue(YandexDisk.describeWebdavError(RuntimeException("сеть легла")).contains("сеть легла"))
        assertTrue(YandexDisk.describeError(DiskHttpException(401, "raw")).contains("OAuth-токен"))
    }

    // ─── аккаунты ───

    @Test
    fun diskAccountsJsonRoundtrip() {
        val list = listOf(
            DiskAccount(1L, "https://webdav.yandex.ru", "a@yandex.ru", "pass1"),
            DiskAccount(2L, "https://webdav.example.com", "b@example.com", "pass2")
        )
        val back = DiskAccounts.fromJson(DiskAccounts.toJson(list))
        assertEquals(list, back)
    }

    @Test
    fun diskAccountsBadInput() {
        assertEquals(emptyList<DiskAccount>(), DiskAccounts.fromJson(null))
        assertEquals(emptyList<DiskAccount>(), DiskAccounts.fromJson("не json"))
    }

    @Test
    fun diskAccountsAddRemove() {
        var list = DiskAccounts.add(emptyList(), "", "a@yandex.ru", "p")
        assertEquals(1L, list.single().id)
        assertEquals(YandexDisk.WEBDAV_DEFAULT, list.single().baseUrl)
        list = DiskAccounts.add(list, "https://webdav.yandex.ru/", "b@yandex.ru", "p")
        assertEquals(2L, list.last().id)
        assertEquals("https://webdav.yandex.ru", list.last().baseUrl)
        assertEquals(1, DiskAccounts.remove(list, 2L).size)
    }
}
