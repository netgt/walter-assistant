package com.example.dispatcher

/**
 * Фильтр мусорных пушей и сборка полного текста уведомления.
 * Чистый — тестируется в JVM.
 */
object PushFilter {

    private val COUNT_PATTERNS = listOf(
        Regex("""^\d+\s+(новое\s+)?сообщени[ея](\s+от\s+.+)?$""", RegexOption.IGNORE_CASE),
        Regex("""^\d+\s+нов(ое|ых)\s+сообщени[ей]?$""", RegexOption.IGNORE_CASE),
        Regex("""^(у\s+вас\s+)?\d+\s+(новых\s+)?(писем|уведомлени[йя]|комментари[евя])\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^\d+\s+(новый\s+)?комментари[йя]$""", RegexOption.IGNORE_CASE)
    )

    /** Если extras пусты, tickerText часто содержит отправителя/тему письма (Gmail и др.). */
    fun fallbackText(text: String, ticker: String): String = text.ifBlank { ticker.trim() }

    /** Одиночная содержательная group summary без детей: её можно показать вместо отбрасывания. */
    fun useGroupSummary(isGroupSummary: Boolean, hasDetailedChild: Boolean, title: String, text: String): Boolean {
        if (!isGroupSummary || hasDetailedChild) return false
        val t = title.trim(); val x = text.trim()
        if (t.isBlank() && x.isBlank()) return false
        return !COUNT_PATTERNS.any { it.matches(t) || it.matches(x) }
    }

    /** Пропустить пуш как неинформативный (счётчик/сводка/постоянный). */
    fun shouldSkip(title: String, text: String, isGroupSummary: Boolean, ongoing: Boolean): Boolean {
        if (isGroupSummary) return true
        if (ongoing) return true
        val t = title.trim()
        val x = text.trim()
        if (t.isBlank() && x.isBlank()) return true
        return COUNT_PATTERNS.any { it.matches(t) || it.matches(x) }
    }

    /**
     * Полный текст пуша из всех полей уведомления.
     * Приложения кладут короткую версию в EXTRA_TEXT, а полную — в EXTRA_BIG_TEXT
     * или в построчные EXTRA_TEXT_LINES; сообщения мессенджеров (MessagingStyle)
     * лежат в «android.messages». Берём самый длинный непустой кандидат,
     * подтекст (EXTRA_SUB_TEXT) дописываем, если его там нет.
     */
    fun pushText(small: String, big: String, lines: List<String>, messages: List<String>, sub: String): String {
        val linesText = lines.joinToString("\n") { it.trim() }.trim()
        val msgsText = messages.filter { it.isNotBlank() }.joinToString("\n").trim()
        val main = listOf(big, small, linesText, msgsText)
            .filter { it.isNotBlank() }
            .maxByOrNull { it.length } ?: ""
        val cap = main.take(2000)
        return if (sub.isNotBlank() && !cap.contains(sub)) {
            if (cap.isBlank()) sub else "$cap\n$sub"
        } else cap
    }

    /** Заголовок пуша: развёрнутый EXTRA_TITLE_BIG важнее короткого EXTRA_TITLE. */
    fun pushTitle(title: String, bigTitle: String): String =
        bigTitle.trim().ifBlank { title.trim() }
}
