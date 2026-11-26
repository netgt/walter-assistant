package com.example.dispatcher

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Правило извлечения поля: фиксированное значение или подстрока между якорями after/before. */
data class FinanceFieldRule(
    val value: String = "",
    val after: String = "",
    val before: String = ""
) {
    fun extract(text: String): String {
        if (value.isNotBlank()) return value.trim()
        if (after.isBlank()) return ""
        val lower = text.lowercase(Locale.getDefault())
        val anchor = after.lowercase(Locale.getDefault())
        val startAt = lower.indexOf(anchor)
        if (startAt < 0) return ""
        val start = startAt + after.length
        val end = if (before.isNotBlank()) {
            lower.indexOf(before.lowercase(Locale.getDefault()), start).takeIf { it >= start } ?: text.length
        } else {
            text.indexOf('\n', start).takeIf { it >= 0 } ?: text.length
        }
        return text.substring(start, end).trim().trim('«', '»', '"', '\'')
    }
}

data class FinanceRule(
    val id: Long,
    val sender: String,
    val signature: String,
    val operation: String = "",
    val amount: FinanceFieldRule? = null,
    val balance: FinanceFieldRule? = null,
    val category: FinanceFieldRule? = null,
    val card: FinanceFieldRule? = null,
    val merchant: FinanceFieldRule? = null
) {
    fun matches(sender: String, text: String): Boolean {
        if (this.sender.isNotBlank() && !sender.contains(this.sender, ignoreCase = true)) return false
        return signature.split('|').filter { it.isNotBlank() }.all { text.contains(it.trim(), ignoreCase = true) }
    }

    fun structured(text: String): String {
        val fields = linkedMapOf<String, String>()
        if (operation.isNotBlank()) fields["operation"] = operation
        amount?.extract(text)?.takeIf { it.isNotBlank() }?.let { raw ->
            fields["amount"] = FinanceRules.amount(raw) ?: raw
        }
        balance?.extract(text)?.takeIf { it.isNotBlank() }?.let { raw ->
            fields["balance"] = FinanceRules.amount(raw) ?: raw
        }
        category?.extract(text)?.takeIf { it.isNotBlank() }?.let { fields["category"] = it }
        card?.extract(text)?.takeIf { it.isNotBlank() }?.let { fields["card"] = it }
        merchant?.extract(text)?.takeIf { it.isNotBlank() }?.let { fields["merchant"] = it }
        return fields.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }
}

object FinanceRules {
    fun fromJson(raw: String?): List<FinanceRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                FinanceRule(
                    id = o.optLong("id"),
                    sender = o.optString("sender"),
                    signature = o.optString("signature"),
                    operation = o.optString("operation"),
                    amount = field(o, "amount"),
                    balance = field(o, "balance"),
                    category = field(o, "category"),
                    card = field(o, "card"),
                    merchant = field(o, "merchant")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun field(o: JSONObject, name: String): FinanceFieldRule? {
        val value = o.opt(name) ?: return null
        return when (value) {
            is String -> FinanceFieldRule(value = value)
            is JSONObject -> FinanceFieldRule(
                value = value.optString("value"),
                after = value.optString("after"),
                before = value.optString("before")
            )
            else -> null
        }
    }

    fun toJson(rules: List<FinanceRule>): String = JSONArray().apply {
        rules.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("sender", r.sender)
                put("signature", r.signature)
                if (r.operation.isNotBlank()) put("operation", r.operation)
                r.amount?.let { put("amount", fieldJson(it)) }
                r.balance?.let { put("balance", fieldJson(it)) }
                r.category?.let { put("category", fieldJson(it)) }
                r.card?.let { put("card", fieldJson(it)) }
                r.merchant?.let { put("merchant", fieldJson(it)) }
            })
        }
    }.toString()

    private fun fieldJson(field: FinanceFieldRule): Any {
        if (field.value.isNotBlank()) return field.value
        return JSONObject()
            .put("after", field.after)
            .put("before", field.before)
    }

    fun amount(raw: String): String? {
        val normalized = raw.replace(Regex("[   ]"), "").replace(',', '.')
        val number = Regex("-?\\d+(?:\\.\\d+)?").find(normalized)?.value?.toDoubleOrNull() ?: return null
        return FinanceParser.fmt(number)
    }

    fun bestMatch(rules: List<FinanceRule>, sender: String, text: String): FinanceRule? =
        rules.filter { it.matches(sender, text) }
            .maxWithOrNull(compareBy<FinanceRule> { it.signature.length }.thenBy { it.id })

    fun learn(raw: String?, query: String): String {
        val o = runCatching { JSONObject(query) }.getOrNull()
            ?: return "Для finance_learn нужен JSON: sender, sample, signature и поля amount/balance/category/card/merchant."
        val sender = o.optString("sender").trim()
        val sample = o.optString("sample")
        if (sender.isBlank()) return "Укажи sender — отправителя SMS."
        if (sample.isBlank()) return "Добавь sample — исходный текст SMS, на котором проверим правило."
        val id = (fromJson(raw).maxOfOrNull { it.id } ?: 0L) + 1
        val rule = FinanceRule(
            id = id,
            sender = sender,
            signature = o.optString("signature"),
            operation = o.optString("operation"),
            amount = field(o, "amount"),
            balance = field(o, "balance"),
            category = field(o, "category"),
            card = field(o, "card"),
            merchant = field(o, "merchant")
        )
        if (rule.structured(sample).isBlank()) {
            return "Правило пока ничего не извлекло из sample. Уточни after/before или фиксированные значения."
        }
        val rules = fromJson(raw).filterNot { it.sender.equals(sender, true) && it.signature == rule.signature } + rule
        return JSONObject()
            .put("saved", true)
            .put("id", id)
            .put("check", rule.structured(sample))
            .put("rules", toJson(rules))
            .toString()
    }

    fun list(raw: String?): String {
        val rules = fromJson(raw)
        if (rules.isEmpty()) return "Пользовательских правил SMS пока нет. Покажи Walter пример SMS и объясни поля."
        return rules.joinToString("\n") {
            "#${it.id} ${it.sender} [${it.signature}] ${it.operation}".trim()
        }
    }

    fun delete(raw: String?, id: Long): Pair<String, String> {
        val rules = fromJson(raw)
        val remaining = rules.filterNot { it.id == id }
        return if (remaining.size == rules.size) {
            "Правило #$id не найдено." to toJson(rules)
        } else {
            "Правило #$id удалено." to toJson(remaining)
        }
    }
}
