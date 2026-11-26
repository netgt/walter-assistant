package com.example.dispatcher

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Входящее событие: SMS или пуш приложения. */
data class EventInfo(
    val kind: String,   // "sms" | "push"
    val source: String, // отправитель SMS или имя приложения
    val title: String,  // заголовок уведомления
    val text: String,   // текст
    val at: Long,       // timestamp
    val note: String? = null, // локальная аннотация (например, категория расхода)
    val pi: String? = null,   // base64 упакованного PendingIntent (contentIntent пуша — кнопка «Ответить»)
    val pkg: String? = null   // пакет приложения пуша (фолбэк — запуск главного экрана)
)

/** Сообщение чата: пользователь, агент или карточка события. */
/** Карточка файла в чате (снипет, как в harness DeepSeek). */
data class FileRef(
    val name: String,
    val path: String,
    val kind: String, // "text" | "image" | "apk" | "video" | "doc" | "sheet" | "slides" | "pdf" | "dir" | "other"
    val size: Long,
    val isDir: Boolean = false,
    val diskPath: String? = null, // путь на Яндекс Диске (карточка Диска вместо локальной)
    val diskAcc: String? = null   // логин аккаунта Диска, которому принадлежит файл
)

data class Msg(
    val fromUser: Boolean,
    val text: String,
    val event: EventInfo? = null,
    val files: List<FileRef> = emptyList(),
    val events: List<EventInfo> = emptyList(),
    val helpKind: String? = null, // служебный блок интерфейса, например список инструментов
    val askConfirm: String? = null, // ожидание подтверждения: текст кнопки «Подтвердить»
    val resumeMsgs: String? = null, // сериализованная история цикла для продолжения после подтверждения
    val resumePlan: Boolean = false, // продолжение многошагового плана
    val resumePlanQ: String? = null, // исходный текст плана
    val resumePlanDone: Int = 0, // сколько подтверждений уже выполнено
    val bubbleUid: Long = 0, // бабл запуска для плана
    val bubblePrompt: String = "", // запрос плана для бабла/повтора
    val toolsChain: List<String> = emptyList(), // цепочка инструментов плашки (вне текста)
    val planInput: Boolean = false, // шаг ждёт инструкции пользователя, а не простое подтверждение
    val planInputText: String = "" // инструкции, введённые пользователем для выполняемого шага
)

/** Разобранный интент из ответа модели. */
data class IntendedAction(
    val action: String,
    val app: String?,
    val title: String?,
    val whenStr: String?,
    val address: String?,
    val reply: String?,
    val query: String? = null,
    val path: String? = null,
    val old: String? = null,
    val new: String? = null,
    val from: String? = null,
    val to: String? = null
)

/** Чистая функция: JSON-строка -> интент. Тестируется без сети. */
fun parseAction(content: String): IntendedAction {
    val j = JSONObject(content)
    return IntendedAction(
        action = j.optString("action", "chat"),
        app = j.optNullable("app"),
        title = j.optNullable("title"),
        whenStr = j.optNullable("when"),
        address = j.optNullable("address"),
        reply = j.optNullable("reply"),
        query = j.optNullable("query"),
        path = j.optNullable("path"),
        old = j.optNullable("old"),
        new = j.optNullable("new"),
        from = j.optNullable("from"),
        to = j.optNullable("to")
    )
}

private fun JSONObject.optNullable(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }

val ACTION_PATTERN = Regex(""""action"\s*:\s*"([a-z_]+)"""")

/** Ремонт битого/обрезанного JSON от модели: вытаскиваем поля регулярками. */
fun extractAction(content: String): IntendedAction? {
    val am = ACTION_PATTERN.find(content) ?: return null
    val action = am.groupValues[1]
    fun field(name: String): String? {
        val pattern = Regex(""""$name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val fm = pattern.find(content) ?: return null
        return fm.groupValues[1]
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
    }
    return IntendedAction(action, field("app"), field("title"), field("when"), field("address"), field("reply"), field("query"), field("path"), field("old"), field("new"), field("from"), field("to"))
}

/** Текст события для контекста модели (без эмодзи). */
fun eventToText(e: EventInfo): String {
    val head = when (e.kind) {
        "sms" -> "SMS от «${e.source}»"
        "watch" -> "Следилка «${e.source}»"
        else -> "Пуш из «${e.source}»"
    }
    val parts = listOf(e.title, e.text).map { it.trim() }.filter { it.isNotBlank() }.distinct().take(2)
    val b = StringBuilder(head)
    parts.forEach { b.append(": «").append(it.take(300)).append("»") }
    e.note?.let { b.append(" [").append(it).append("]") }
    return b.toString()
}

/** Ключ потока обсуждения для события. */
fun eventKey(e: EventInfo): String = "${e.kind}|${e.source}|${e.at}"

/** Ключ карточки события из списка тула: уникален по позиции, треды живут под ним. */
fun eventListKey(e: EventInfo, idx: Int): String = eventKey(e) + "|lst$idx"

/** Дата и время события для карточки: пуши/SMS — с секундами, остальные — как раньше. */
fun eventTime(e: EventInfo): String =
    SimpleDateFormat(
        if (e.kind == "push" || e.kind == "sms") "dd.MM.yyyy HH:mm:ss" else "HH:mm",
        Locale.getDefault()
    ).format(Date(e.at))


/** Машина многошаговых планов «шаг -> подтверди -> шаг …» — чистая, без Android (JVM-тесты). */
object PlanFlow {
    private val CONFIRM = Regex("(?i)подтверди|подтвержд|подверди|подвержд")

    fun isPlan(q: String): Boolean =
        q.contains("->") || q.contains("→") || CONFIRM.containsMatchIn(q)

    fun confirms(q: String): Int = CONFIRM.findAll(q).count()

    /** Остаток плана после done выполненных подтверждений. */
    fun remainder(q: String, done: Int): String {
        val parts = q.split(Regex("\\s*(?:->|→)\\s*")).map { it.trim() }.filter { it.isNotBlank() }
        if (done <= 0) return parts.joinToString(" -> ")
        var seen = 0
        parts.forEachIndexed { i, step ->
            if (CONFIRM.containsMatchIn(step)) {
                seen++
                if (seen == done) return parts.drop(i + 1).joinToString(" -> ")
            }
        }
        return ""
    }

    /** Следующий шаг после done подтверждений: стрелка — разделитель, подтверждение — граница. */
    fun nextStep(q: String, done: Int): String {
        val parts = q.split(Regex("\\s*(?:->|→)\\s*")).map { it.trim() }.filter { it.isNotBlank() }
        if (done <= 0) return parts.firstOrNull().orEmpty()
        var seen = 0
        parts.forEachIndexed { i, step ->
            if (CONFIRM.containsMatchIn(step)) {
                seen++
                if (seen == done) return parts.getOrElse(i + 1) { "" }.trim()
            }
        }
        return ""
    }

    /** Действие по его смысловому индексу в сценарии, а не по числу вызванных инструментов. */
    fun nextActionStep(q: String, actionIndex: Int): String =
        q.split(Regex("\\s*(?:->|→)\\s*")).map { it.trim() }.filter { it.isNotBlank() }
            .getOrElse(actionIndex) { "" }.trim()

    /** Шаг сразу после текущего (нужен для вопроса перед действием, не дожидаясь следующего подтверждения). */
    fun followingStep(q: String, done: Int): String {
        val parts = q.split(Regex("\\s*(?:->|→)\\s*")).map { it.trim() }.filter { it.isNotBlank() }
        return parts.getOrElse(done * 2 + 1) { "" }.trim()
    }

    /** Последний подряд выполненный смысловой шаг: модель могла вызвать read_file и write_file без chat между ними. */
    fun completedActionIndex(q: String, startIndex: Int, tools: Set<String>): Int {
        var idx = startIndex
        while (stepSatisfied(nextActionStep(q, idx), tools)) {
            val next = nextActionStep(q, idx + 1)
            if (next.isBlank() || CONFIRM.containsMatchIn(next) || !stepSatisfied(next, tools)) break
            idx++
        }
        return idx
    }

    /** Смысловой шаг завершён только нужным инструментом, а не любым промежуточным вызовом. */
    fun stepSatisfied(step: String, tools: Set<String>): Boolean {
        val t = step.lowercase()
        val actual = tools - setOf("chat", "ask_confirm")
        if (actual.isEmpty()) return false
        return when {
            Regex("прочитай|последнюю строку|содержим").containsMatchIn(t) ->
                actual.any { it == "read_file" || it == "disk_read" }
            Regex("добавь|запиши|перезапиши|измени|удали|создай|вставь").containsMatchIn(t) ->
                actual.any { it == "write_file" || it == "create_file" || it == "disk_write" || it == "disk_upload" }
            t.contains("открой") -> "open_app" in actual
            Regex("найди|поиск").containsMatchIn(t) ->
                actual.any { it == "find_files" || it == "web_search" || it == "grep_files" }
            else -> true
        }
    }

    /** Все действия до следующего подтверждения уже выполнены; повторные файловые вызовы не нужны. */
    fun segmentComplete(q: String, startIndex: Int, tools: Set<String>): Boolean {
        val current = nextActionStep(q, startIndex)
        if (!stepSatisfied(current, tools)) return false
        val next = nextActionStep(q, completedActionIndex(q, startIndex, tools) + 1)
        return CONFIRM.containsMatchIn(next)
    }

    /** Шаг, на котором нужны собственные инструкции пользователя, а не «да/подтверди». */
    fun isUserInputStep(step: String): Boolean {
        val t = step.lowercase()
        return Regex("дождись|подожди|ждём|ждем|введет|введёт|напишет|ответит|получи от пользователя|получить от пользователя")
            .containsMatchIn(t)
    }

    /** Шаг, на котором модель сама должна сформулировать вопрос пользователю. */
    fun isQuestionStep(step: String): Boolean {
        val t = step.lowercase()
        return t.contains("спроси пользователя") || t.contains("задай вопрос") || t.contains("уточни у пользователя")
    }

    /** Короткое подтверждение, а не инструкции для плана. */
    fun isConfirmation(text: String): Boolean {
        val t = text.trim().lowercase().removeSuffix(".").removeSuffix("!")
        return t in setOf("да", "давай", "ок", "окей", "хорошо", "ага", "подтверди", "подтверждаю",
            "подтверждение", "продолжай", "готово", "yes", "y", "ok")
    }

    /** Текст подтверждения в едином UX плана: Готово + следующий шаг + Подтверди. */
    fun forcedConfirmText(q: String, done: Int): String {
        val rest = nextStep(q, done)
        return if (rest.isNotBlank()) "Готово\n**Следующий шаг плана:** $rest\n**Подтверди**"
        else "Готово\n**Подтверди продолжение**"
    }
}
