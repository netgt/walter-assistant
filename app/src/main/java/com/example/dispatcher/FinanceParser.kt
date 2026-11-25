package com.example.dispatcher

import java.util.Locale

/** Разбор банковских SMS: суммы, категории, остатки. Чистые функции — тестируются без сети. */
object FinanceParser {

    data class Balance(val amount: Double, val sender: String, val date: Long)

    data class Summary(
        val total: Double,
        val count: Int,
        val byCategory: List<Pair<String, Double>>,
        val balances: List<Balance>,
        val days: Int
    )

    private val CATEGORIES: List<Pair<String, List<String>>> = listOf(
        "Еда и кафе" to listOf("кафе", "ресторан", "кофе", "суши", "пицц", "бургер", "доставк", "продукт", "перекрёсток", "пятёрочк", "магнит", "вкусвилл", "дикси", "ашан", "столовая", "пекарн"),
        "Магазины" to listOf("супермаркет", "гипермаркет", "универмаг"),
        "Маркетплейсы" to listOf("ozon", "wildberries", "мегамаркет", "я.маркет", "яндекс маркет", "маркет"),
        "Такси и транспорт" to listOf("такси", "яндекс го", "yandex go", "uber", "ситимобил", "каршеринг", "метро", "электричк", "аэроэкспресс"),
        "АЗС и авто" to listOf("азс", "заправк", "бензин", "газпромнефть", "лукойл", "rosneft", "татнефть", "башнефть", "мойк"),
        "Аптеки" to listOf("аптек", "здравсити", "еаптек", "ригла"),
        "Связь" to listOf("мтс", "билайн", "мегафон", "теле2", "связь", "оператор"),
        "Подписки" to listOf("подписк", "subscription", "netflix", "spotify", "яндекс плюс", "ivi", "кинопоиск", "okko"),
        "ЖКХ" to listOf("жкх", "квартплат", "электроэнерг", "водоснабж", "тко", "управляющая", "энергосбыт"),
        "Дети" to listOf("детск", "школ", "кружок", "сад")
    )

    /** Похоже ли SMS на финансовое уведомление. */
    fun looksFinancial(text: String): Boolean {
        val t = text.lowercase()
        if (!Regex("""\d""").containsMatchIn(t)) return false
        return Regex("покупк|оплат|списан|зачислен|остаток|баланс|перевод|карт|счёт|счет|пополнен|выдан|банкомат").containsMatchIn(t)
    }

    /** Первая сумма в тексте (руб). */
    fun extractAmount(text: String): Double? {
        val m = Regex("""(\d{1,3}(?:[   ]\d{3})+[.,]\d{1,2}|\d+[.,]\d{1,2}|\d{2,})""").find(text) ?: return null
        var s = m.value.replace(Regex("""[   ]"""), "")
        s = s.replace(',', '.')
        return s.toDoubleOrNull()
    }

    /** Категория расхода по ключевым словам. */
    fun categoryOf(text: String): String {
        val t = text.lowercase()
        CATEGORIES.forEach { (cat, keys) -> if (keys.any { t.contains(it) }) return cat }
        return "Прочее"
    }

    /** Короткая аннотация для карточки события (null — не финансовое). */
    fun classifySms(text: String): String? {
        if (!looksFinancial(text)) return null
        val t = text.lowercase()
        val amt = extractAmount(text)
        if (t.contains("остаток") || t.contains("баланс")) {
            return amt?.let { "Остаток: ${fmt(it)} ₽" } ?: "Остаток по счёту"
        }
        if (t.contains("зачислен") || t.contains("пополнен")) {
            return amt?.let { "Зачисление: ${fmt(it)} ₽" }
        }
        if (t.contains("покупк") || t.contains("оплат") || t.contains("списан") || t.contains("перевод") || t.contains("выдан")) {
            return amt?.let { "Расход: ${fmt(it)} ₽ · ${categoryOf(text)}" }
        }
        return null
    }

    /** Сводка расходов/остатков из списка SMS (sender, body, date). */
    fun analyze(sms: List<Triple<String, String, Long>>, days: Int): Summary {
        val since = System.currentTimeMillis() - days * 24L * 3600_000L
        var total = 0.0
        var count = 0
        val cats = linkedMapOf<String, Double>()
        val balances = mutableListOf<Balance>()
        sms.forEach { (sender, body, date) ->
            if (date < since) return@forEach
            if (!looksFinancial(body)) return@forEach
            val t = body.lowercase()
            val amt = extractAmount(body)
            if (t.contains("остаток") || t.contains("баланс")) {
                amt?.let { balances += Balance(it, sender, date) }
                return@forEach
            }
            if (t.contains("зачислен") || t.contains("пополнен")) return@forEach
            if (amt != null && (t.contains("покупк") || t.contains("оплат") || t.contains("списан") || t.contains("перевод") || t.contains("выдан"))) {
                total += amt
                count++
                val c = categoryOf(body)
                cats[c] = (cats[c] ?: 0.0) + amt
            }
        }
        return Summary(
            total, count,
            cats.entries.map { it.key to it.value }.sortedByDescending { it.second },
            balances, days
        )
    }

    /** Форматирование суммы: 1 234,50. */
    fun fmt(v: Double): String =
        String.format(Locale("ru", "RU"), "%,.2f", v).replace('\u00A0', ' ')
}
