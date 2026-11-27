package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanFlowTest {

    private val plan = "открой openvpn -> подтверди -> собери новости fetch_url из каналов: https://example.com/news, " +
        "сагрегируй по тематикам и напиши сводку -> подтверди -> открой openvpn"

    @Test
    fun detectsPlanAndCountsConfirms() {
        assertTrue(PlanFlow.isPlan(plan))
        assertFalse(PlanFlow.isPlan("покажи погоду"))
        assertEquals(2, PlanFlow.confirms(plan))
    }

    @Test
    fun remainderAfterEachConfirm() {
        val r1 = PlanFlow.remainder(plan, 1)
        assertTrue(r1.contains("собери новости"))
        assertTrue(r1.contains("открой openvpn")) // финал ещё впереди
        val r2 = PlanFlow.remainder(plan, 2)
        assertTrue(r2.contains("открой openvpn"))
        assertFalse(r2.contains("собери новости"))
    }

    @Test
    fun nextStepIsSegmentUntilNextConfirm() {
        assertEquals("собери новости fetch_url из каналов: https://example.com/news, " +
            "сагрегируй по тематикам и напиши сводку",
            PlanFlow.nextStep(plan, 1))
        assertEquals("открой openvpn", PlanFlow.nextStep(plan, 2))
    }

    @Test
    fun forcedConfirmText() {
        // после 1-го подтверждения следующий шаг — сбор новостей
        val t1 = PlanFlow.forcedConfirmText(plan, 1)
        assertTrue(t1.startsWith("Готово\n**Следующий шаг плана:**"))
        assertTrue(t1.contains("собери новости"))
        assertTrue(t1.contains("**Подтверди**"))
        // после 2-го — финал
        val t2 = PlanFlow.forcedConfirmText(plan, 2)
        assertTrue(t2.contains("открой openvpn"))
    }

    @Test
    fun distinguishesQuestionInputAndConfirmationSteps() {
        assertTrue(PlanFlow.isQuestionStep("спроси пользователя имя файла и что с ним сделать"))
        assertFalse(PlanFlow.isUserInputStep("спроси пользователя имя файла"))
        assertTrue(PlanFlow.isUserInputStep("дождись пока пользователь введет имя файла"))
        assertTrue(PlanFlow.isUserInputStep("подожди ответа пользователя"))
        assertFalse(PlanFlow.isUserInputStep("выполни то что просит пользователь"))
        assertTrue(PlanFlow.isConfirmation("да"))
        assertTrue(PlanFlow.isConfirmation("подтверждаю"))
        assertFalse(PlanFlow.isConfirmation("файл TODO-3, удали последнюю строку"))
    }

    @Test
    fun semanticStepRequiresItsOwnEvidence() {
        val read = "прочитай последнюю строку файла"
        assertEquals(false, PlanFlow.stepSatisfied(read, emptySet()))
        assertEquals(false, PlanFlow.stepSatisfied(read, setOf("find_files")))
        assertEquals(true, PlanFlow.stepSatisfied(read, setOf("find_files", "read_file")))
        val write = "добавь ее в начало файла"
        assertEquals(false, PlanFlow.stepSatisfied(write, setOf("read_file")))
        assertEquals(true, PlanFlow.stepSatisfied(write, setOf("read_file", "write_file")))
        assertEquals(true, PlanFlow.stepSatisfied("открой приложение почта", setOf("open_app")))
        val q = "открой приложение почта -> подтверди -> спроси пользователя имя файла -> " +
            "прочитай последнюю строку файла -> добавь ее в начало файла -> подтверди -> открой приложение почта"
        assertEquals(4, PlanFlow.completedActionIndex(q, 3, setOf("find_files", "read_file", "write_file")))
        assertEquals(3, PlanFlow.completedActionIndex(q, 3, setOf("find_files", "read_file")))
        assertEquals(3, PlanFlow.completedActionIndex(q, 3, setOf("find_files")))
        assertEquals(true, PlanFlow.segmentComplete(q, 3, setOf("find_files", "read_file", "write_file")))
        assertEquals(false, PlanFlow.segmentComplete(q, 3, setOf("find_files", "read_file")))
    }

    @Test
    fun nextActionTracksToolsWithinSegment() {
        val q = "открой приложение почта -> подтверди -> спроси пользователя имя файла -> " +
            "прочитай последнюю строку файла -> добавь ее в начало файла -> подтверди -> открой приложение почта"
        assertEquals("подтверди", PlanFlow.nextActionStep(q, 1))
        assertEquals("прочитай последнюю строку файла", PlanFlow.nextActionStep(q, 3))
        assertEquals("добавь ее в начало файла", PlanFlow.nextActionStep(q, 4))
        assertEquals("подтверди", PlanFlow.nextActionStep(q, 5))
    }

    @Test
    fun followingStepAfterQuestionIsActionNotNextConfirm() {
        val q = "открой приложение почта -> подтверди -> спроси пользователя имя файла -> " +
            "прочитай последнюю строку файла -> добавь ее в начало файла -> подтверди -> открой приложение почта"
        assertEquals("прочитай последнюю строку файла", PlanFlow.followingStep(q, 1))
        assertEquals("открой приложение почта", PlanFlow.nextStep(q, 2))
    }

    @Test
    fun countsTypoConfirmWithoutSkippingExecution() {
        val q = "открой приложение почта -> подтверди -> спроси пользователя имя файла и что с ним сделать -> " +
            "подтверди -> дождись пока пользователь введет имя файла и что с ним сделать -> подверди -> " +
            "выполни то что просит пользователь -> подтверди -> открой приложение почта"
        assertEquals(4, PlanFlow.confirms(q))
        assertEquals("выполни то что просит пользователь", PlanFlow.nextStep(q, 3))
        assertEquals("открой приложение почта", PlanFlow.nextStep(q, 4))
    }
}
