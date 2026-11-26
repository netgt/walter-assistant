package com.example.dispatcher

import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceRulesTest {
    private val sample = "Покупка 83 RUB Баланс 561 169,43 RUB Московский транспорт ··2017"

    private fun rule() = FinanceRule(
        id = 1,
        sender = "Alfa-Bank",
        signature = "Покупка",
        operation = "purchase",
        amount = FinanceFieldRule(after = "Покупка ", before = " RUB"),
        balance = FinanceFieldRule(after = "Баланс ", before = " RUB"),
        category = FinanceFieldRule(value = "Такси и транспорт"),
        card = FinanceFieldRule(after = "··")
    )

    @Test
    fun userAnchorsExtractFieldsAndFixedCategory() {
        val parsed = rule().structured(sample)
        assertTrue(parsed.contains("amount=83,00"))
        assertTrue(parsed.contains("balance=561 169,43"))
        assertTrue(parsed.contains("category=Такси и транспорт"))
        assertTrue(parsed.contains("card=2017"))
    }

    @Test
    fun learnValidatesSampleAndReturnsSavedRule() {
        val query = """
            {"sender":"Alfa-Bank","sample":"$sample","signature":"Покупка","operation":"purchase",
             "amount":{"after":"Покупка ","before":" RUB"},
             "balance":{"after":"Баланс ","before":" RUB"},
             "category":"Такси и транспорт","card":{"after":"··"}}
        """.trimIndent()
        val result = FinanceRules.learn(null, query)
        assertTrue(result.contains("\"saved\":true"))
        assertTrue(result.contains("amount=83,00"))
        assertTrue(FinanceRules.fromJson(org.json.JSONObject(result).getString("rules")).size == 1)
    }
}
