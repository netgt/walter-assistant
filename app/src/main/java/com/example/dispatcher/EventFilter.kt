package com.example.dispatcher

/** Единый фильтр сырых событий для read_pushes/read_sms: модель и карточки получают один набор. */
object EventFilter {
    private fun isWalterQuery(query: String): Boolean =
        query.trim().equals("walter", ignoreCase = true) || query.trim().equals("уолтер", ignoreCase = true)

    fun filterPushes(events: List<EventInfo>, query: String): List<EventInfo> {
        if (query.isBlank()) return events
        if (isWalterQuery(query)) return events.filter { it.kind == "watch" }
        val bySource = events.filter {
            it.source.contains(query.trim(), ignoreCase = true) ||
                it.pkg.orEmpty().contains(query.trim(), ignoreCase = true)
        }
        if (bySource.isNotEmpty()) return bySource
        return events.filter {
            it.title.contains(query.trim(), ignoreCase = true) || it.text.contains(query.trim(), ignoreCase = true)
        }
    }

    fun filterSms(rows: List<Triple<String, String, Long>>, query: String): List<Triple<String, String, Long>> {
        if (query.isBlank()) return rows
        if (isWalterQuery(query)) return rows.filter {
            it.first.contains("walter", ignoreCase = true) || it.first.contains("уолтер", ignoreCase = true)
        }
        val bySender = rows.filter { it.first.contains(query.trim(), ignoreCase = true) }
        if (bySender.isNotEmpty()) return bySender
        return rows.filter { it.second.contains(query.trim(), ignoreCase = true) }
    }

    fun matchesPush(event: EventInfo, query: String): Boolean {
        if (query.isBlank()) return true
        if (isWalterQuery(query)) return event.kind == "watch"
        return listOf(event.source, event.title, event.text, event.pkg.orEmpty())
            .any { it.contains(query.trim(), ignoreCase = true) }
    }

    fun matchesSms(source: String, body: String, query: String): Boolean {
        if (query.isBlank()) return true
        if (isWalterQuery(query)) {
            return source.contains("walter", ignoreCase = true) || source.contains("уолтер", ignoreCase = true)
        }
        return source.contains(query.trim(), ignoreCase = true) || body.contains(query.trim(), ignoreCase = true)
    }
}
