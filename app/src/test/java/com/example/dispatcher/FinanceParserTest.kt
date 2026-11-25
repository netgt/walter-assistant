package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceParserTest {

    @Test
    fun detectsFinancialSms() {
        assertTrue(FinanceParser.looksFinancial("Покупка 1 234,50 ₽ в Магните. Баланс: 45 000 ₽"))
        assertFalse(FinanceParser.looksFinancial("Привет, как дела?"))
    }

    @Test
    fun extractsAmount() {
        assertEquals(1234.5, FinanceParser.extractAmount("Покупка 1 234,50 ₽")!!, 0.001)
        assertEquals(500.0, FinanceParser.extractAmount("Списание 500 ₽")!!, 0.001)
        assertNull(FinanceParser.extractAmount("без цифр"))
    }

    @Test
    fun categorizes() {
        assertEquals("Еда и кафе", FinanceParser.categoryOf("Покупка в кафе Быстроном"))
        assertEquals("АЗС и авто", FinanceParser.categoryOf("АЗС Газпромнефть"))
        assertEquals("Маркетплейсы", FinanceParser.categoryOf("OZON.RU заказ"))
        assertEquals("Прочее", FinanceParser.categoryOf("какая-то покупка"))
    }

    @Test
    fun classifiesSpending() {
        val s = FinanceParser.classifySms("Списание 500 ₽ АЗС Газпромнефть")
        assertNotNull(s)
        assertTrue(s!!.contains("Расход"))
        assertTrue(s.contains("АЗС и авто"))
    }

    @Test
    fun classifiesBalance() {
        val s = FinanceParser.classifySms("Остаток по счёту: 45 000,00 ₽")
        assertNotNull(s)
        assertTrue(s!!.contains("Остаток"))
    }

    @Test
    fun analyzeSummary() {
        val now = System.currentTimeMillis()
        val sms = listOf(
            Triple("T-Bank", "Покупка 1 000,00 ₽ кафе", now),
            Triple("T-Bank", "Списание 500 ₽ АЗС", now),
            Triple("T-Bank", "Остаток 5 000,00 ₽", now),
            Triple("T-Bank", "Зачисление 10 000 ₽", now)
        )
        val s = FinanceParser.analyze(sms, 30)
        assertEquals(1500.0, s.total, 0.001)
        assertEquals(2, s.count)
        assertEquals(1, s.balances.size)
        assertEquals(2, s.byCategory.size)
    }
}
