package com.example.dispatcher

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Чистые функции календаря: разбор периода из запроса и форматирование событий.
 * Данные берутся из локального CalendarProvider (Google и Яндекс синхронизируются в него).
 */
object CalendarStore {

    data class CalEvent(
        val title: String,
        val start: Long,
        val end: Long,
        val location: String,
        val source: String = "",
        val rrule: String? = null
    )

    /** «сегодня»/«завтра»/«неделя»/«месяц»/«YYYY-MM-DD..YYYY-MM-DD» → [начало, конец). */
    fun parseRange(q: String, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val t = q.trim().lowercase(Locale.getDefault())
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        fun dayStart(offsetDays: Int): Long {
            val c = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_MONTH, offsetDays)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return c.timeInMillis
        }
        val rangeRe = Regex("""(\d{4}-\d{2}-\d{2})\s*\.\.\s*(\d{4}-\d{2}-\d{2})""")
        val rm = rangeRe.find(q)
        if (rm != null) {
            val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val start = runCatching { df.parse(rm.groupValues[1])!!.time }.getOrDefault(now)
            val end = runCatching {
                val c = Calendar.getInstance().apply { time = df.parse(rm.groupValues[2])!!; add(Calendar.DAY_OF_MONTH, 1) }
                c.timeInMillis
            }.getOrDefault(now + 7 * 24 * 3600_000L)
            return start to end
        }
        return when (t) {
            "сегодня", "today" -> dayStart(0) to dayStart(1)
            "завтра", "tomorrow" -> dayStart(1) to dayStart(2)
            "неделя", "неделю", "week" -> dayStart(0) to dayStart(7)
            "месяц", "month" -> dayStart(0) to dayStart(30)
            else -> dayStart(0) to dayStart(7)
        }
    }

    fun formatEvents(events: List<CalEvent>, from: Long, to: Long, header: String? = null): String {
        if (events.isEmpty()) return "Событий нет."
        val df = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val dfDay = SimpleDateFormat("dd.MM", Locale.getDefault())
        val h = header ?: "События календаря (${dfDay.format(Date(from))} — ${dfDay.format(Date(to - 1))}):"
        return h + "\n" + events.joinToString("\n") { e ->
            val loc = if (e.location.isBlank()) "" else " (${e.location})"
            val src = if (e.source.isBlank()) "" else " [${e.source}]"
            "• [${df.format(Date(e.start))}] ${e.title}$loc$src"
        }
    }

    /** Строка для карточки события: день и время (или «весь день») + место. */
    fun formatEventWhen(e: CalEvent): String {
        val dayF = SimpleDateFormat("EEE dd.MM", Locale.getDefault())
        val timeF = SimpleDateFormat("HH:mm", Locale.getDefault())
        val allDay = e.end > e.start && e.end - e.start == 24L * 3600_000L
        val whenStr = when {
            allDay -> "${dayF.format(Date(e.start))} (весь день)"
            e.end > e.start -> "${dayF.format(Date(e.start))}, ${timeF.format(Date(e.start))}–${timeF.format(Date(e.end))}"
            else -> "${dayF.format(Date(e.start))}, ${timeF.format(Date(e.start))}"
        }
        return if (e.location.isBlank()) whenStr else "$whenStr · ${e.location}"
    }

    /** Разбивка событий на диапазоны: в запросе / будущие / прошедшие. */
    fun partitionRange(
        events: List<CalEvent>,
        start: Long,
        end: Long
    ): Triple<List<CalEvent>, List<CalEvent>, List<CalEvent>> {
        val inRange = events.filter { it.start < end && it.end >= start }.sortedBy { it.start }
        val upcoming = events.filter { it.start >= end }.sortedBy { it.start }
        val past = events.filter { it.end < start }.sortedBy { it.start }
        return Triple(inRange, upcoming, past)
    }

    /**
     * Разбор ICS: VEVENT → события (SUMMARY/DTSTART/DTEND/LOCATION).
     * Поддерживает UTC (Z), локальное время и даты без времени.
     */
    fun parseIcsEvents(ics: String): List<CalEvent> {
        val out = mutableListOf<CalEvent>()
        val veventRe = Regex("(?s)BEGIN:VEVENT(.*?)END:VEVENT")
        for (m in veventRe.findAll(ics)) {
            val block = m.groupValues[1]
            fun prop(name: String): String? {
                val r = Regex("(?m)^" + name + "(?:;[^:]*)?:(.*)$").find(block) ?: return null
                return r.groupValues[1].trim().replace("\\n", " ").take(300)
            }
            val dtstart = prop("DTSTART") ?: continue
            val start = parseIcsTime(dtstart) ?: continue
            val dtend = prop("DTEND")
            val end = dtend?.let { parseIcsTime(it) } ?: start
            out += CalEvent(
                prop("SUMMARY") ?: "(без названия)",
                start,
                end,
                prop("LOCATION") ?: "",
                rrule = prop("RRULE")
            )
        }
        return out
    }

    /** Время ICS: 20261120T100000Z (UTC), 20261120T100000 (локальное), 20261120 (весь день). */
    fun parseIcsTime(s: String): Long? = when {
        s.endsWith("Z") -> runCatching {
            val df = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            df.timeZone = java.util.TimeZone.getTimeZone("UTC")
            df.parse(s)?.time
        }.getOrNull()
        s.length == 15 -> runCatching {
            SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).parse(s)?.time
        }.getOrNull()
        s.length == 8 -> runCatching {
            SimpleDateFormat("yyyyMMdd", Locale.US).parse(s)?.time
        }.getOrNull()
        else -> null
    }

    // ─── Повторяющиеся события (RRULE) ───
    // Поддерживаются частые кейсы: FREQ=DAILY/WEEKLY/MONTHLY/YEARLY,
    // INTERVAL, COUNT, UNTIL, BYDAY (для WEEKLY), BYMONTH/BYMONTHDAY (для YEARLY/MONTHLY).

    private data class RruleOpts(
        val freq: String,
        val interval: Int = 1,
        val count: Int? = null,
        val until: Long? = null,
        val byDay: List<java.time.DayOfWeek>? = null,
        val byMonth: Int? = null,
        val byMonthDay: Int? = null
    )

    private fun parseRrule(s: String): RruleOpts? {
        val freq = Regex("FREQ=([A-Z]+)").find(s)?.groupValues?.get(1) ?: return null
        val interval = (Regex("INTERVAL=(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val count = Regex("COUNT=(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull()
        val until = Regex("UNTIL=([0-9TZ]+)").find(s)?.groupValues?.get(1)?.let { parseIcsTime(it) }
        val byDay = Regex("BYDAY=([A-Z,]+)").find(s)?.groupValues?.get(1)
            ?.split(",")?.mapNotNull { parseWeekday(it) }
        val byMonth = Regex("BYMONTH=(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull()
        val byMonthDay = Regex("BYMONTHDAY=(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull()
        return RruleOpts(freq.uppercase(), interval, count, until, byDay, byMonth, byMonthDay)
    }

    private fun parseWeekday(s: String): java.time.DayOfWeek? = when (s.uppercase()) {
        "MO" -> java.time.DayOfWeek.MONDAY
        "TU" -> java.time.DayOfWeek.TUESDAY
        "WE" -> java.time.DayOfWeek.WEDNESDAY
        "TH" -> java.time.DayOfWeek.THURSDAY
        "FR" -> java.time.DayOfWeek.FRIDAY
        "SA" -> java.time.DayOfWeek.SATURDAY
        "SU" -> java.time.DayOfWeek.SUNDAY
        else -> null
    }

    /**
     * Раскрывает повторяющееся событие во вхождения в диапазоне [from, to).
     * Время суток вхождения сохраняется от DTSTART, событие без RRULE возвращается как есть.
     */
    fun expandEvent(e: CalEvent, from: Long, to: Long): List<CalEvent> {
        val rule = e.rrule ?: return listOf(e)
        val opts = parseRrule(rule) ?: return listOf(e)
        val zone = java.time.ZoneId.systemDefault()
        val startZdt = java.time.Instant.ofEpochMilli(e.start).atZone(zone)
        val base = startZdt.toLocalDate()
        val timeOfDay = startZdt.toLocalTime()
        val duration = (e.end - e.start).coerceAtLeast(0L)
        val fromDate = java.time.Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val k0 = startK(base, fromDate, opts)
        val out = mutableListOf<CalEvent>()
        var k = k0
        while (k - k0 < 5000) {
            if (opts.count != null && out.size >= opts.count) break
            val occs = occurrencesAt(base, opts, k)
            k++
            if (occs.isEmpty()) continue
            if (occs.all { it.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli() >= to }) break
            for (occ in occs) {
                if (opts.count != null && out.size >= opts.count) break
                val occMillis = occ.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
                if (opts.until != null && occMillis > opts.until) return out
                if (occMillis >= to) continue
                if (occMillis + duration > from) {
                    out += e.copy(start = occMillis, end = occMillis + duration)
                }
            }
        }
        return out
    }

    /** Начальный индекс вхождений: пропускаем всё до [from], чтобы длинные правила (ежедневные с 2016-го) не упирались в лимит. */
    private fun startK(base: java.time.LocalDate, fromDate: java.time.LocalDate, opts: RruleOpts): Long {
        val diff = when (opts.freq) {
            "DAILY" -> java.time.temporal.ChronoUnit.DAYS.between(base, fromDate)
            "WEEKLY" -> java.time.temporal.ChronoUnit.WEEKS.between(base, fromDate)
            "MONTHLY" -> java.time.temporal.ChronoUnit.MONTHS.between(base, fromDate)
            "YEARLY" -> java.time.temporal.ChronoUnit.YEARS.between(base, fromDate)
            else -> return 0L
        }
        return ((diff / opts.interval) - 2).coerceAtLeast(0L)
    }

    /** Все вхождения правила для индекса k (для WEEKLY+BYDAY — все дни недели). */
    private fun occurrencesAt(base: java.time.LocalDate, opts: RruleOpts, k: Long): List<java.time.LocalDate> {
        return when (opts.freq) {
            "DAILY" -> listOf(base.plusDays(k * opts.interval))
            "WEEKLY" -> {
                val days = opts.byDay ?: listOf(base.dayOfWeek)
                val monday = base.minusDays((base.dayOfWeek.value - 1).toLong())
                val weekStart = monday.plusWeeks(k * opts.interval)
                days.map { weekStart.plusDays((it.value - 1).toLong()) }.filter { it >= base }.sorted()
            }
            "MONTHLY" -> listOfNotNull(monthlyAt(base, opts, k))
            "YEARLY" -> listOf(yearlyAt(base, opts, k))
            else -> emptyList()
        }
    }

    private fun monthlyAt(base: java.time.LocalDate, opts: RruleOpts, k: Long): java.time.LocalDate? {
        val target = base.plusMonths(k * opts.interval)
        val day = opts.byMonthDay ?: return target
        val clamped = day.coerceIn(1, target.lengthOfMonth())
        return if (k == 0L && target.withDayOfMonth(clamped) < base) {
            val shifted = base.plusMonths(opts.interval.toLong())
            shifted.withDayOfMonth(day.coerceIn(1, shifted.lengthOfMonth()))
        } else target.withDayOfMonth(clamped)
    }

    private fun yearlyAt(base: java.time.LocalDate, opts: RruleOpts, k: Long): java.time.LocalDate {
        val target = base.plusYears(k * opts.interval)
        if (opts.byMonth != null) {
            val ym = java.time.YearMonth.of(target.year, opts.byMonth.coerceIn(1, 12))
            val day = (opts.byMonthDay ?: target.dayOfMonth).coerceIn(1, ym.lengthOfMonth())
            return ym.atDay(day)
        }
        return if (opts.byMonthDay != null) {
            target.withDayOfMonth(opts.byMonthDay.coerceIn(1, target.lengthOfMonth()))
        } else target
    }

    /** Все события с раскрытыми RRULE-вхождениями в диапазоне. */
    fun expandEvents(events: List<CalEvent>, from: Long, to: Long): List<CalEvent> =
        events.flatMap { expandEvent(it, from, to) }
}
