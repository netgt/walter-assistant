package com.example.dispatcher

import android.os.Environment
import java.io.File
import java.util.Locale

/** Файл или папка на диске. */
data class FileEntry(
    val name: String,
    val path: String, // absolutePath
    val isDir: Boolean,
    val size: Long,
    val mtime: Long
)

/**
 * Доступ ко всему хранилищу напрямую (java.io.File).
 * Требует разрешения «Все файлы» (MANAGE_EXTERNAL_STORAGE).
 */
object FileStore {

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun storageRoot(): File = Environment.getExternalStorageDirectory()

    /** Обход дерева с бюджетом узлов. pathOf вынесен для тестируемости. */
    fun walkLegacy(
        root: File,
        query: String?,
        recentLimit: Int,
        searchLimit: Int,
        pathOf: (File) -> String = { it.absolutePath }
    ): List<FileEntry> {
        val all = mutableListOf<FileEntry>()
        var budget = 8000
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty() && budget > 0) {
            val dir = queue.removeFirst()
            budget--
            val kids = try { dir.listFiles() } catch (e: Exception) { null } ?: continue
            for (f in kids) {
                if (f.isDirectory) queue.add(f)
                else all += FileEntry(f.name, pathOf(f), false, f.length(), f.lastModified())
            }
        }
        return if (query != null) {
            all.filter { it.name.contains(query, ignoreCase = true) }.take(searchLimit)
        } else {
            all.sortedByDescending { it.mtime }.take(recentLimit)
        }
    }

    fun recentAll(limit: Int = 30): List<FileEntry> =
        walkLegacy(storageRoot(), null, limit, 0)

    fun searchAll(query: String, limit: Int = 30): List<FileEntry> =
        walkLegacy(storageRoot(), query, 0, limit)

    /** Подпапки первого уровня. */
    fun topDirs(root: File): List<FileEntry> {
        val kids = try { root.listFiles() } catch (e: Exception) { null } ?: return emptyList()
        return kids.filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
            .map { FileEntry(it.name, it.absolutePath, true, 0L, it.lastModified()) }
    }

    /** Содержимое папки: сначала подпапки, потом файлы, по алфавиту. */
    fun listDir(dir: File, limit: Int = 40): List<FileEntry> {
        val kids = try { dir.listFiles() } catch (e: Exception) { null } ?: return emptyList()
        return kids.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .take(limit)
            .map {
                FileEntry(it.name, it.absolutePath, it.isDirectory,
                    if (it.isDirectory) 0L else it.length(), it.lastModified())
            }
    }

    /** Найти папку: абсолютный путь, путь от корня или поиск по имени. */
    fun resolveDir(root: File, nameOrPath: String): File? {
        val q = nameOrPath.trim()
        if (q.isEmpty()) return null
        val direct = File(q)
        if (direct.isDirectory) return direct
        val rel = File(root, q)
        if (rel.isDirectory) return rel
        var budget = 3000
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty() && budget > 0) {
            val dir = queue.removeFirst()
            budget--
            val kids = try { dir.listFiles() } catch (e: Exception) { null } ?: continue
            for (f in kids) {
                if (!f.isDirectory) continue
                if (f.name.equals(q, ignoreCase = true)) return f
                queue.add(f)
            }
        }
        return null
    }

    fun fmtSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024L * 1024 -> String.format(Locale("ru", "RU"), "%.1f КБ", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale("ru", "RU"), "%.1f МБ", bytes / 1024.0 / 1024)
        else -> String.format(Locale("ru", "RU"), "%.2f ГБ", bytes / 1024.0 / 1024 / 1024)
    }

    // ─── Текстовые файлы: поиск по содержимому, чтение, редактирование ───

    private val TEXT_EXTS = setOf(
        "txt", "md", "log", "csv", "json", "xml", "html", "htm", "kt", "java", "py", "js",
        "ts", "css", "yml", "yaml", "ini", "cfg", "conf", "sh", "bat", "properties", "gradle",
        "toml", "sql", "tex", "srt", "vtt", "c", "cpp", "h", "rs", "go", "php", "rb"
    )

    /** Тип файла для карточек в чате. */
    fun kindOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in TEXT_EXTS -> "text"
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> "image"
            "apk" -> "apk"
            "mp4", "mkv", "avi", "mov", "webm", "3gp" -> "video"
            "docx" -> "doc"
            "xlsx" -> "sheet"
            "pptx" -> "slides"
            "pdf" -> "pdf"
            else -> "other"
        }
    }

    fun isTextFile(name: String): Boolean = kindOf(name) == "text"

    private const val GREP_MAX_FILE = 2_000_000L // не читаем файлы больше 2 МБ

    /** Поиск текста внутри текстовых файлов; возвращает пары (файл, фрагмент строки с вхождением). */
    fun grepText(root: File, text: String, limit: Int = 15): List<Pair<FileEntry, String>> {
        if (text.isBlank()) return emptyList()
        val q = text.lowercase()
        val out = mutableListOf<Pair<FileEntry, String>>()
        var budget = 8000
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty() && budget > 0 && out.size < limit) {
            val dir = queue.removeFirst()
            budget--
            val kids = try { dir.listFiles() } catch (e: Exception) { null } ?: continue
            for (f in kids) {
                if (out.size >= limit) break
                if (f.isDirectory) { queue.add(f); continue }
                if (!isTextFile(f.name)) continue
                if (f.length() > GREP_MAX_FILE) continue
                val content = try { f.readText() } catch (e: Exception) { continue }
                val idx = content.lowercase().indexOf(q)
                if (idx < 0) continue
                val start = (idx - 60).coerceAtLeast(0)
                val end = (idx + q.length + 60).coerceAtMost(content.length)
                val frag = content.substring(start, end).replace("\n", " ")
                out += FileEntry(f.name, f.absolutePath, false, f.length(), f.lastModified()) to frag
            }
        }
        return out
    }

    /** Первые maxBytes текстового файла или извлечённый текст офисного (для сниппета в чате). */
    fun readText(path: String, maxBytes: Int = 30_000): String {
        val f = File(path)
        if (!f.exists() || !f.isFile) return ""
        val kind = kindOf(f.name)
        if (kind == "doc" || kind == "sheet" || kind == "slides" || kind == "pdf") {
            return OfficeStore.extractText(path, maxBytes)
        }
        return try {
            f.inputStream().use { ins ->
                val buf = ByteArray(maxBytes)
                val n = ins.read(buf)
                if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }

    /** Полная перезапись файла тем, что подготовила модель: text — как есть, docx — пересоздание. */
    fun writeText(path: String, content: String): String {
        val f = File(path)
        if (!f.exists() || !f.isFile) return "Файл «$path» не найден."
        if (content.isBlank()) return "Не указано новое содержимое."
        val kind = kindOf(f.name)
        return when (kind) {
            "doc" -> safe("Не удалось записать файл") { OfficeStore.writeDocx(path, content) }
            "sheet" -> safe("Не удалось записать файл") { OfficeStore.writeXlsx(path, content) }
            "slides" -> safe("Не удалось записать файл") { OfficeStore.writePptx(path, content) }
            "pdf" -> safe("Не удалось записать файл") { OfficeStore.writePdf(path, content) }
            "text" -> {
                if (f.length() > GREP_MAX_FILE) return "Файл слишком большой для перезаписи."
                try {
                    f.writeText(content)
                    "Файл записан: ${f.name}"
                } catch (e: Exception) {
                    "Не удалось записать файл: ${e.message}"
                }
            }
            else -> "«${f.name}» — неподдерживаемый формат; перезапись доступна для text, docx, xlsx, pptx, pdf."
        }
    }

    /** Создать новый файл с содержимым, подготовленным моделью (text/docx/xlsx/pptx/pdf). */
    fun createText(path: String, content: String): String {
        if (path.isBlank()) return "Не указан путь (path)."
        if (content.isBlank()) return "Не указано содержимое (new)."
        val f = File(path)
        if (f.exists()) return "Файл уже существует: ${f.name} — для изменения используй write_file."
        val parent = f.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return "Не удалось создать папку для файла: $parent"
        }
        val kind = if (f.extension.isBlank()) "text" else kindOf(f.name)
        return when (kind) {
            "text" -> try {
                f.writeText(content)
                "Файл создан: ${f.name}"
            } catch (e: Exception) {
                "Не удалось создать файл: ${e.message}"
            }
            "doc" -> safe("Не удалось создать файл") { OfficeStore.writeDocx(path, content) }
            "sheet" -> safe("Не удалось создать файл") { OfficeStore.writeXlsx(path, content) }
            "slides" -> safe("Не удалось создать файл") { OfficeStore.writePptx(path, content) }
            "pdf" -> safe("Не удалось создать файл") { OfficeStore.writePdf(path, content) }
            else -> "Неподдерживаемое расширение для создания: .${f.extension}"
        }
    }

    /** Защита вызова: любая ошибка записи (включая нехватку памяти) превращается в текст. */
    private fun safe(msg: String, block: () -> String): String =
        try { block() } catch (t: Throwable) { "$msg: ${t.message ?: t.javaClass.simpleName}" }

    /**
     * Найти файл по запросу: абсолютный путь → путь от корня хранилища →
     * поиск по имени во всём дереве. root вынесен для тестируемости.
     */
    fun resolveFilePath(q: String, root: File = storageRoot()): File? {
        val t = q.trim()
        if (t.isEmpty()) return null
        val direct = File(t)
        if (direct.isFile) return direct
        val rel = File(root, t)
        if (rel.isFile) return rel
        val rel2 = File(root, t.trimStart('/'))
        if (rel2.isFile) return rel2
        val name = t.substringAfterLast('/')
        var budget = 3000
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty() && budget > 0) {
            val dir = queue.removeFirst()
            budget--
            val kids = try { dir.listFiles() } catch (e: Exception) { null } ?: continue
            for (f in kids) {
                if (f.isDirectory) { queue.add(f); continue }
                if (f.name.equals(t, ignoreCase = true) || f.name.equals(name, ignoreCase = true)) return f
            }
        }
        return null
    }
}
