package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarStoreTest {

    private val now: Long = Calendar.getInstance().apply {
        set(2026, 10, 17, 13, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun parseRangeKeywords() {
        val (s1, e1) = CalendarStore.parseRange("сегодня", now)
        val (s2, e2) = CalendarStore.parseRange("завтра", now)
        val (s3, e3) = CalendarStore.parseRange("неделя", now)
        assertEquals(24 * 3600_000L, e1 - s1)
        assertEquals(s1 + 24 * 3600_000L, s2)
        assertEquals(7 * 24 * 3600_000L, e3 - s3)
        // неизвестное — по умолчанию неделя
        val (s4, e4) = CalendarStore.parseRange("что-то левое", now)
        assertEquals(7 * 24 * 3600_000L, e4 - s4)
    }

    @Test
    fun parseRangeExplicit() {
        // диапазон включительно: 20, 21, 22 → конец исключающий = начало 23-го
        val (s, e) = CalendarStore.parseRange("2026-11-20..2026-11-22", now)
        assertEquals(3 * 24 * 3600_000L, e - s)
    }

    @Test
    fun formatEvents() {
        val ev = listOf(
            CalendarStore.CalEvent("Встреча", now, now + 3600_000L, "Офис"),
            CalendarStore.CalEvent("Звонок", now + 7200_000L, now + 7500_000L, "")
        )
        val text = CalendarStore.formatEvents(ev, now, now + 24 * 3600_000L)
        assertTrue(text.contains("Встреча"))
        assertTrue(text.contains("(Офис)"))
        assertTrue(text.contains("Звонок"))
        assertTrue(CalendarStore.formatEvents(emptyList(), now, now).contains("Событий нет"))
        // источник отображается в метке
        val withSrc = CalendarStore.formatEvents(
            listOf(CalendarStore.CalEvent("Событие", now, now + 1, "", "Яндекс")), now, now + 24 * 3600_000L
        )
        assertTrue(withSrc.contains("[Яндекс]"))
        // кастомный заголовок для фолбэка
        val fallback = CalendarStore.formatEvents(
            listOf(CalendarStore.CalEvent("Событие", now, now + 1, "", "")), now, now,
            "Недавние события:"
        )
        assertTrue(fallback.startsWith("Недавние события:"))
    }

    @Test
    fun partitionRange() {
        val d = 24 * 3600_000L
        val events = listOf(
            CalendarStore.CalEvent("прошлое", now - 5 * d, now - 5 * d + d, ""),
            CalendarStore.CalEvent("в диапазоне", now + d, now + 2 * d, ""),
            CalendarStore.CalEvent("будущее", now + 10 * d, now + 11 * d, "")
        )
        val (inRange, upcoming, past) = CalendarStore.partitionRange(events, now, now + 7 * d)
        assertEquals(listOf("в диапазоне"), inRange.map { it.title })
        assertEquals(listOf("будущее"), upcoming.map { it.title })
        assertEquals(listOf("прошлое"), past.map { it.title })
        // пустой диапазон: всё уходит в нужные корзины
        val (i2, u2, p2) = CalendarStore.partitionRange(events, now, now + d)
        assertEquals(0, i2.size)
        assertEquals(2, u2.size)
        assertEquals(1, p2.size)
    }

    @Test
    fun parseIcsEvents() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:1@test
            DTSTART:20261120T100000Z
            DTEND:20261120T110000Z
            SUMMARY:Встреча с командой
            LOCATION:Офис
            END:VEVENT
            BEGIN:VEVENT
            UID:2@test
            DTSTART:20261122
            SUMMARY:Весь день
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val events = CalendarStore.parseIcsEvents(ics)
        assertEquals(2, events.size)
        assertEquals("Встреча с командой", events[0].title)
        assertEquals("Офис", events[0].location)
        assertEquals(3600_000L, events[0].end - events[0].start)
        assertEquals("Весь день", events[1].title)
        assertEquals(0, CalendarStore.parseIcsEvents("мусор без VEVENT").size)
    }

    @Test
    fun parseIcsTimeFormats() {
        assertTrue(CalendarStore.parseIcsTime("20261120T100000Z") != null)
        assertTrue(CalendarStore.parseIcsTime("20261120T100000") != null)
        assertTrue(CalendarStore.parseIcsTime("20261120") != null)
        assertEquals(null, CalendarStore.parseIcsTime("мусор"))
    }

    @Test
    fun parseIcsEventsCapturesRrule() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:g@t
            DTSTART:20260101T110000
            DTEND:20260101T113000
            SUMMARY:Заводить генераторы
            RRULE:FREQ=WEEKLY;BYDAY=SU;INTERVAL=2
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val ev = CalendarStore.parseIcsEvents(ics).single()
        assertEquals("Заводить генераторы", ev.title)
        assertEquals("FREQ=WEEKLY;BYDAY=SU;INTERVAL=2", ev.rrule)
    }

    @Test
    fun expandWeeklyByDayRealCase() {
        // реальный кейс: каждые 2 недели по воскресеньям с 01.01.2026
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:g@t
            DTSTART:20260101T110000
            DTEND:20260101T113000
            SUMMARY:Заводить генераторы
            RRULE:FREQ=WEEKLY;BYDAY=SU;INTERVAL=2
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val ev = CalendarStore.parseIcsEvents(ics).single()
        val from = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20261101")!!.time
        val to = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20261201")!!.time
        val occ = CalendarStore.expandEvent(ev, from, to)
        assertEquals("в ноябре 2026: вхождения", 2, occ.size)
        val df = SimpleDateFormat("dd.MM", Locale.getDefault())
        assertEquals(listOf("08.11", "22.11"), occ.map { df.format(java.util.Date(it.start)) })
        // каждое вхождение — воскресенье, разница 14 дней
        occ.forEach {
            val dow = java.util.Calendar.getInstance().apply { timeInMillis = it.start }.get(java.util.Calendar.DAY_OF_WEEK)
            assertEquals(java.util.Calendar.SUNDAY, dow)
        }
        assertEquals(14L * 24 * 3600_000L, occ[1].start - occ[0].start)
    }

    @Test
    fun expandYearlyByMonthDay() {
        // реальный кейс: «чистить септик» ежегодно 30 октября
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:s@t
            DTSTART;VALUE=DATE:20261030
            DTEND;VALUE=DATE:20261031
            SUMMARY:чистить септик
            RRULE:FREQ=YEARLY;BYMONTH=10;BYMONTHDAY=30;INTERVAL=1
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val ev = CalendarStore.parseIcsEvents(ics).single()
        val from = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20271001")!!.time
        val to = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20271130")!!.time
        val occ = CalendarStore.expandEvent(ev, from, to)
        assertEquals(1, occ.size)
        val df = SimpleDateFormat("dd.MM", Locale.getDefault())
        assertEquals("30.10", df.format(java.util.Date(occ.single().start)))
    }

    @Test
    fun expandHonorsCountAndUntil() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:c@t
            DTSTART:20260105T090000
            DTEND:20260105T093000
            SUMMARY:каждый день с лимитом
            RRULE:FREQ=DAILY;COUNT=3
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val ev = CalendarStore.parseIcsEvents(ics).single()
        val from = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20260101")!!.time
        val to = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20260201")!!.time
        assertEquals(3, CalendarStore.expandEvent(ev, from, to).size)
        // без RRULE событие возвращается как есть
        val plain = ev.copy(rrule = null)
        assertEquals(1, CalendarStore.expandEvent(plain, from, to).size)
        assertEquals(plain, CalendarStore.expandEvent(plain, from, to).single())
    }

    private fun icsEvent(dtstart: String, rrule: String, dtend: String? = null): CalendarStore.CalEvent {
        val end = dtend ?: dtstart.replace("T000000", "T003000")
        return CalendarStore.parseIcsEvents("""
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:x@t
            DTSTART:$dtstart
            DTEND:$end
            SUMMARY:тест
            RRULE:$rrule
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()).single()
    }

    @Test
    fun expandKeepsTimeOfDay() {
        // событие в 11:00 каждое воскресенье — вхождения должны остаться в 11:00, а не в полночь
        val ev = icsEvent("20261101T110000", "FREQ=WEEKLY;BYDAY=SU")
        val from = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20261105")!!.time
        val to = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20261201")!!.time
        val occ = CalendarStore.expandEvent(ev, from, to)
        assertTrue(occ.isNotEmpty())
        val df = SimpleDateFormat("HH:mm", Locale.US)
        occ.forEach { assertEquals("11:00", df.format(java.util.Date(it.start))) }
    }

    @Test
    fun expandWeeklyMultipleByDays() {
        // BYDAY=MO,WE — должны выходить ОБА дня каждой недели, а не только первый
        val ev = icsEvent("20261104T090000", "FREQ=WEEKLY;BYDAY=MO,WE")
        val from = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20261106")!!.time
        val to = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20261113")!!.time
        val occ = CalendarStore.expandEvent(ev, from, to)
        val df = SimpleDateFormat("dd.MM", Locale.US)
        assertEquals(listOf("09.11", "11.11"), occ.map { df.format(java.util.Date(it.start)) })
    }

    @Test
    fun expandWeeklyBaseNotInByDay() {
        // база в воскресенье, а правило про ПН/ВТ — событие не должно исчезать вовсе
        val ev = icsEvent("20261108T080000", "FREQ=WEEKLY;BYDAY=MO,TU")
        val from = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20261108")!!.time
        val to = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20261120")!!.time
        val occ = CalendarStore.expandEvent(ev, from, to)
        val df = SimpleDateFormat("dd.MM", Locale.US)
        assertEquals(listOf("09.11", "10.11", "16.11", "17.11"), occ.map { df.format(java.util.Date(it.start)) })
    }

    @Test
    fun expandLongDailyRuleNotCapped() {
        // ежедневное событие с 2016 года: старый лимит 2000 вхождений не должен съедать ноябрь 2026
        val ev = icsEvent("20160101T070000", "FREQ=DAILY")
        val from = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20261101")!!.time
        val to = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20261108")!!.time
        val occ = CalendarStore.expandEvent(ev, from, to)
        assertEquals(7, occ.size)
        val df = SimpleDateFormat("dd.MM", Locale.US)
        assertEquals("01.11", df.format(java.util.Date(occ.first().start)))
        assertEquals("07.11", df.format(java.util.Date(occ.last().start)))
    }

    @Test
    fun expandMonthlyClampsShortMonths() {
        // 31 января ежемесячно: февраль должен дать 28-е (високосный — 29-е), а не вылететь
        val ev = icsEvent("20260131T120000", "FREQ=MONTHLY")
        val from = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20260101")!!.time
        val to = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20260401")!!.time
        val occ = CalendarStore.expandEvent(ev, from, to)
        val df = SimpleDateFormat("dd.MM", Locale.US)
        assertEquals(listOf("31.01", "28.02", "31.03"), occ.map { df.format(java.util.Date(it.start)) })
    }

    @Test
    fun formatEventWhenForCards() {
        val df = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
        val timed = CalendarStore.CalEvent("Совещание", df.parse("20261117T093000")!!.time, df.parse("20261117T110000")!!.time, "офис 12", "")
        val s = CalendarStore.formatEventWhen(timed)
        assertTrue(s.contains("17.11"))
        assertTrue(s.contains("09:30"))
        assertTrue(s.contains("11:00"))
        assertTrue(s.contains("офис 12"))
        // весь день: без времени
        val d2 = SimpleDateFormat("yyyyMMdd", Locale.US)
        val allDay = CalendarStore.CalEvent("Праздник", d2.parse("20261118")!!.time, d2.parse("20261119")!!.time, "", "")
        val s2 = CalendarStore.formatEventWhen(allDay)
        assertTrue(s2.contains("весь день"))
        assertFalse(s2.contains("00:00"))
        // без места — нет разделителя «·»
        assertFalse(CalendarStore.formatEventWhen(timed.copy(location = "")).contains("·"))
    }
}
