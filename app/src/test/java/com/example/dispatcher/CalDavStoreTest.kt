package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalDavStoreTest {

    @Test
    fun parseHrefs() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/principals/users/alex@yandex.com/</D:href>
                <D:propstat><D:prop><D:current-user-principal>
                  <D:href>/principals/users/alex@yandex.com/</D:href>
                </D:current-user-principal></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        val hrefs = CalDavStore.parseHrefs(xml)
        assertTrue(hrefs.contains("/principals/users/alex@yandex.com/"))
        assertTrue(hrefs.isNotEmpty())
    }

    @Test
    fun parseCalendars() {
        val xml = """
            <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:response>
                <D:href>/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/calendars/personal/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/><C:calendar/></D:resourcetype></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/calendars/work/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/><C:calendar/></D:resourcetype></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        val cals = CalDavStore.parseCalendars(xml)
        assertEquals(listOf("/calendars/personal/", "/calendars/work/"), cals)
    }

    @Test
    fun parseCalendarDataWithCdata() {
        val xml = """
            <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:response>
                <D:href>/calendars/personal/event1.ics</D:href>
                <D:propstat><D:prop>
                  <C:calendar-data><![CDATA[BEGIN:VCALENDAR
BEGIN:VEVENT
UID:1@t
DTSTART:20261120T100000Z
SUMMARY:Тест
END:VEVENT
END:VCALENDAR]]></C:calendar-data>
                </D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        val data = CalDavStore.parseCalendarData(xml)
        assertEquals(1, data.size)
        assertTrue(data[0].contains("BEGIN:VCALENDAR"))
        assertTrue(!data[0].contains("CDATA"))
    }

    @Test
    fun parsePrincipalFromRealYandexResponse() {
        // реальный ответ Яндекса: первый href в ответе — сам запрос, принципал внутри свойства
        val xml = """
            <D:multistatus xmlns:D="DAV:"><D:response>
              <href xmlns="DAV:">/principals/users/a%40yandex.com/</href>
              <D:propstat><D:prop>
                <D:current-user-principal><D:href>/principals/users/a%40yandex.com/</D:href></D:current-user-principal>
              </D:prop><status xmlns="DAV:">HTTP/1.1 200 OK</status></D:propstat>
            </D:response></D:multistatus>
        """.trimIndent()
        assertEquals("/principals/users/a%40yandex.com/", CalDavStore.parsePrincipal(xml))
    }

    @Test
    fun parseHomeSetFromRealYandexResponse() {
        val xml = """
            <D:multistatus xmlns:D="DAV:"><D:response>
              <href xmlns="DAV:">/principals/users/a%40yandex.com/</href>
              <D:propstat><D:prop>
                <C:calendar-home-set xmlns:C="urn:ietf:params:xml:ns:caldav"><D:href>/calendars/a%40yandex.com/</D:href></C:calendar-home-set>
              </D:prop><status xmlns="DAV:">HTTP/1.1 200 OK</status></D:propstat>
            </D:response></D:multistatus>
        """.trimIndent()
        assertEquals("/calendars/a%40yandex.com/", CalDavStore.parseHomeSet(xml))
        assertTrue(CalDavStore.parseHrefs(xml).first() != CalDavStore.parseHomeSet(xml))
    }
}

class CalDavAccountsTest {

    @Test
    fun jsonRoundtrip() {
        val list = listOf(
            CalDavAccount(1, "Личный", "https://caldav.yandex.ru", "a@yandex.com", "pass1"),
            CalDavAccount(2, "Работа", "https://caldav.yandex.ru", "b@yandex.com", "pass2")
        )
        assertEquals(list, CalDavAccounts.fromJson(CalDavAccounts.toJson(list)))
        assertEquals(emptyList<CalDavAccount>(), CalDavAccounts.fromJson(null))
        assertEquals(emptyList<CalDavAccount>(), CalDavAccounts.fromJson("мусор"))
    }

    @Test
    fun addRemove() {
        val a = CalDavAccounts.add(emptyList(), "Л", "https://caldav.yandex.ru", "u", "p")
        assertEquals(1L, a.single().id)
        val b = CalDavAccounts.add(a, "Р", "url", "u2", "p2")
        assertEquals(2, b.size)
        assertEquals(2L, b.last().id)
        assertEquals(1, CalDavAccounts.remove(b, 2L).size)
    }
}
