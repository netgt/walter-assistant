package com.example.dispatcher

/** Единый порядок событий: старые сверху, свежие внизу; limit берёт самые свежие без переворота. */
object EventSort {
    fun <T> latest(items: List<T>, limit: Int, at: (T) -> Long): List<T> =
        items.sortedBy(at).takeLast(limit)
}
