package com.example.dispatcher

/** Один найденный фрагмент полной истории чата. */
data class HistoryHit(
    val source: String,
    val index: Int,
    val fromUser: Boolean,
    val text: String,
    val score: Int
)

/** Детерминированный поиск сырых сообщений по всей истории; анализ результатов — у модели. */
object HistorySearch {
    private val STOP_WORDS = setOf(
        "что", "как", "когда", "где", "это", "этот", "эта", "эти", "был", "была", "были",
        "мне", "меня", "тебе", "тебя", "его", "ее", "них", "они", "мы", "вы", "он", "она",
        "про", "для", "или", "и", "в", "во", "на", "с", "со", "по", "из", "у", "о", "об",
        "пожалуйста", "нужно", "надо", "сделай", "скажи", "помнишь", "напомни", "покажи",
        "искал", "ищи", "найди", "поиск", "истории", "историю", "чата", "чате", "сообщение"
    )

    private fun terms(query: String): List<String> {
        val words = query.lowercase()
            .split(Regex("[^\\p{L}\\p{N}_@./:-]+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .distinct()
        return words.ifEmpty { listOf(query.trim().lowercase()) }.filter { it.isNotBlank() }
    }

    private fun messageText(m: Msg): String {
        val event = m.event?.let { eventToText(it) }.orEmpty()
        val nested = m.events.joinToString("\n") { eventToText(it) }
        val files = m.files.joinToString("\n") { "${it.name} ${it.path}" }
        val tools = m.toolsChain.joinToString(" ")
        return listOf(m.text, m.askConfirm.orEmpty(), event, nested, files, tools, m.planInputText)
            .filter { it.isNotBlank() }.joinToString("\n")
    }

    fun search(
        main: List<Msg>,
        threads: Map<String, List<Msg>>,
        query: String,
        limit: Int = 40
    ): List<HistoryHit> {
        if (query.isBlank()) return emptyList()
        val needles = terms(query)
        val hits = mutableListOf<HistoryHit>()
        fun scan(source: String, messages: List<Msg>) {
            messages.forEachIndexed { index, m ->
                val text = messageText(m)
                if (text.isBlank()) return@forEachIndexed
                val lower = text.lowercase()
                val score = needles.count { lower.contains(it) }
                if (score > 0) hits += HistoryHit(source, index + 1, m.fromUser, text, score)
            }
        }
        scan("Основной чат", main)
        threads.forEach { (key, messages) -> scan("Событие $key", messages) }
        return hits.sortedWith(compareByDescending<HistoryHit> { it.score }.thenByDescending { it.index })
            .take(limit)
    }

    fun format(main: List<Msg>, threads: Map<String, List<Msg>>, query: String): String {
        val total = main.size + threads.values.sumOf { it.size }
        val allHits = search(main, threads, query, Int.MAX_VALUE)
        if (allHits.isEmpty()) {
            return "Лексических совпадений по запросу «$query» нет. Проверено сообщений: $total (основной чат и все ветки). " +
                "Это не доказывает отсутствие темы: попробуй другие редкие слова или имя файла."
        }
        val shown = mutableListOf<HistoryHit>()
        var budget = 22_000
        for (hit in allHits) {
            if (shown.size >= 40) break
            val cost = hit.text.take(700).length + hit.source.length + 80
            if (budget - cost < 0) break
            shown += hit
            budget -= cost
        }
        return "Лексический поиск по всей истории «$query»: найдено ${allHits.size}, показано ${shown.size}, проверено сообщений: $total.\n\n" +
            shown.joinToString("\n\n") { hit ->
                val role = if (hit.fromUser) "пользователь" else "Walter"
                "[${hit.source}, сообщение ${hit.index}, совпадений ${hit.score}] $role:\n${hit.text.take(700)}"
            }
    }
}
