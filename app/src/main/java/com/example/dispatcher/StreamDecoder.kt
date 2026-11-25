package com.example.dispatcher

/**
 * Инкрементальный декодер JSON-escape из стрима.
 * Корректно обрабатывает escape-последовательности, разрезанные границей чанка:
 * "привет\" + "nмир" даёт "привет\nмир", а не сырой "\n" на экране.
 * Останавливается на закрывающей кавычке значения reply (дальше идут JSON-поля —
 * они на экран не выводятся). Чистый класс без Android — тестируется в JVM.
 */
class StreamDecoder {
    private val raw = StringBuilder()

    private val disp = StringBuilder()

    // reply закончился: закрывающая кавычка + ("," или "}") встречены —
    // остаток JSON-полей (app/title/…/to) на экран не выводится
    private var finished = false

    /**
     * Декодирует чанк и возвращает готовую к показу часть.
     * Последние 2 символа придерживаются в буфере: в конце потока там окажутся
     * закрывающие JSON («"}»), и их показывать не нужно.
     */
    fun display(chunk: String): String {
        disp.append(push(chunk))
        if (finished) {
            val rest = disp.toString()
            disp.setLength(0)
            return rest
        }
        if (disp.length <= 2) return ""
        val out = disp.substring(0, disp.length - 2)
        disp.delete(0, disp.length - 2)
        return out
    }

    /** Декодирует чанк и возвращает готовый декодированный текст. */
    fun push(chunk: String): String {
        if (finished) return ""
        raw.append(chunk)
        val s = raw.toString()
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\') {
                if (i + 1 >= s.length) break // незавершённый escape — ждём следующего чанка
                when (val n = s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    'u' -> {
                        if (i + 5 < s.length) {
                            val hex = s.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                            } else {
                                sb.append(c); i++
                            }
                        } else break // ждём остаток \uXXXX
                    }
                    else -> { sb.append(c); i++ } // неизвестный escape — как есть
                }
            } else if (c == '"') {
                if (i + 1 < s.length && (s[i + 1] == ',' || s[i + 1] == '}')) {
                    // закрывающая кавычка значения reply — дальше JSON-поля, их не показываем
                    finished = true
                    break
                }
                if (i + 1 >= s.length) break // последний символ чанка: ждём ',' или '}' в следующем
                sb.append(c); i++ // кавычка внутри текста — показываем как есть
            } else {
                sb.append(c); i++
            }
        }
        raw.delete(0, i)
        return sb.toString()
    }
}
