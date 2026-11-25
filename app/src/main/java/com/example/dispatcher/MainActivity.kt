package com.example.dispatcher

import com.example.dispatcher.BuildConfig

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BASE_URL = "https://api.deepseek.com/chat/completions"
private const val MODEL = "deepseek-chat"
private const val YANDEX_SEARCH_URL = "https://searchapi.api.cloud.yandex.net/v2/web/search"
/** Действия чтения данных — исполняются локально, результат возвращается модели. */
/** Описание инструмента чтения: id, статус в чате, описание для хелпа. */
private data class ToolDesc(val id: String, val status: String, val desc: String)

/** Единый реестр инструментов: набор, статусы и хелп строятся из одного списка. */
private val READ_TOOLS = listOf(
    ToolDesc("read_sms", "Читаю SMS…", "последние SMS: отправитель, текст, время; query — необязательный фильтр по отправителю/тексту"),
    ToolDesc("read_pushes", "Читаю уведомления…", "последние уведомления: источник, заголовок, текст; query — необязательный фильтр по источнику/тексту, «Walter» — только алерты Walter"),
    ToolDesc("read_apps", "Смотрю установленные приложения…", "список установленных приложений"),
    ToolDesc("open_capability", "Открываю по функции…", "открыть приложение по функции, включая одиночные запросы (галерея, переводы, навигация, счётчик калорий, повтор заказа); deep link/главный экран/уточнение выбирает локальный capability-роутер; NotFound возвращает установленные приложения"),
    ToolDesc("read_files", "Ищу файлы…", "недавние файлы: имя, размер, дата изменения"),
    ToolDesc("read_dirs", "Читаю папки…", "папки первого уровня в памяти устройства"),
    ToolDesc("read_dir", "Открываю папку…", "содержимое указанной папки (имя или путь — в query)"),
    ToolDesc("find_files", "Ищу файл…", "поиск файла по имени по всей памяти (имя — в query)"),
    ToolDesc("read_system_prompt", "Читаю системный промпт…", "полный текст системного промпта Walter (генерируется приложением на устройстве)"),
    ToolDesc("search_history", "Ищу по всей истории…", "поиск по полной истории основного чата и всех веток событий; query — что найти"),
    ToolDesc("alert_create", "Создаю оповещение…", "создать оповещение Walter; query — JSON: name, rule (когда и на что срабатывать), channel (ПУШ/СМС/ФОН), tool и every для фонового канала"),
    ToolDesc("alert_list", "Показываю оповещения…", "список оповещений Walter: название, канал, правило, дата последнего запуска и статус"),
    ToolDesc("alert_edit", "Изменяю оповещение…", "изменить оповещение Walter (id/имя в query, новое правило или новое имя в newName)"),
    ToolDesc("alert_delete", "Удаляю оповещение…", "удалить оповещение Walter (id/имя в query)"),
    ToolDesc("alert_enable", "Включаю оповещение…", "включить/выключить оповещение Walter (id/имя в query)"),
    ToolDesc("web_search", "Ищу в интернете…", "веб-поиск сразу в двух движках (Яндекс + DuckDuckGo): объединённые заголовки, ссылки и сниппеты по запросу в query"),
    ToolDesc("read_calendar", "Читаю календарь…", "события календарей: системный календарь устройства + несколько Яндекс-календарей по CalDAV; query — сегодня/завтра/неделя/месяц или YYYY-MM-DD..YYYY-MM-DD"),
    ToolDesc("read_mail", "Читаю почту…", "письма из всех настроенных почтовых аккаунтов (IMAP, несколько ящиков); письма показываются карточками с ветками ответов; query — сколько последних писем с каждого"),
    ToolDesc("fetch_url", "Читаю страницу…", "прямой HTTPS GET произвольной HTTP/HTTPS-страницы по полному URL; без специальных обработчиков сервисов"),
    ToolDesc("geocode", "Ищу адрес…", "геокодинг места (Яндекс Геокодер): query — адрес или название места; возвращает координаты и точный адрес"),
    ToolDesc("route_info", "Строю маршрут…", "маршрут между точками (Яндекс Маршрутизация, ПЛАТНЫЙ сервис): from и to — адреса; время и дистанция; бесплатная альтернатива — distance (по прямой)"),
    ToolDesc("reverse_geocode", "Определяю адрес…", "адрес по координатам (обратный геокодинг): query — широта,долгота"),
    ToolDesc("distance", "Считаю расстояние…", "расстояние по прямой между адресами (бесплатно): from и to — адреса; километры и направление"),
    ToolDesc("location_now", "Определяю местоположение…", "текущее местоположение устройства: координаты и адрес"),
    ToolDesc("grep_files", "Ищу в содержимом файлов…", "поиск текста внутри текстовых файлов (искомый текст — в query; папку или файл можно указать в path)"),
    ToolDesc("read_file", "Читаю файл…", "полное содержимое файла по пути (path); текст и офисные форматы docx/xlsx/pptx/pdf"),
    ToolDesc("write_file", "Записываю файл…", "полная перезапись файла: весь новый текст целиком в new (path — путь); поддерживаются text, docx, xlsx, pptx, pdf"),
    ToolDesc("create_file", "Создаю файл…", "создать новый файл с полным содержимым из new (path — полный путь с именем); откажет, если файл уже есть; text/docx/xlsx/pptx/pdf"),
    ToolDesc("disk_list", "Читаю папку на Диске…", "содержимое папки Яндекс Диска (путь папки — в query; пусто — корень Диска): имена, размеры, даты, полные пути; при нескольких аккаунтах — сводка по всем; показывается карточками (папку можно открыть тапом, файл — раскрыть и скачать)"),
    ToolDesc("disk_files", "Ищу файл на Диске…", "поиск файла по имени на всём Яндекс Диске (имя — в query); отдаёт полные пути; при нескольких аккаунтах — по всем; результаты — карточками (файл раскрывается тапом)"),
    ToolDesc("disk_read", "Читаю файл с Диска…", "полное содержимое файла с Яндекс Диска (путь файла — в query; первый настроенный аккаунт); текстовые и офисные форматы docx/xlsx/pptx/pdf; файлы больше 10 МБ не читаются — используй disk_download"),
    ToolDesc("disk_download", "Скачиваю с Диска…", "скачать файл с Яндекс Диска на устройство (путь файла на Диске — в query; локальную папку можно указать в path, по умолчанию Download); файл показывается карточкой"),
    ToolDesc("disk_write", "Записываю на Диск…", "создать или перезаписать файл на Яндекс Диске (первый настроенный аккаунт): подготовь ПОЛНОЕ содержимое в new (путь файла на Диске — в path); text/docx/xlsx/pptx/pdf; существующий файл перезапишется"),
    ToolDesc("disk_upload", "Загружаю на Диск…", "загрузить локальный файл устройства на Яндекс Диск (локальный путь — в path, путь на Диске — в to; без to — в корень Диска)"),
)

private val ALERT_ACTIONS = setOf("alert_list", "alert_create", "alert_edit", "alert_delete", "alert_enable")
private val DATA_ACTIONS = (READ_TOOLS.map { it.id } + "open_app" + ALERT_ACTIONS).toSet()

/** Финальные действия (завершают запрос, исполняются на устройстве). */
private val TERMINAL_TOOLS = listOf(
    "open_app" to "открыть приложение (точное имя — в app)",
    "capability_rule" to "правило capability-роутера: query JSON {capability, package, mode=prefer|exclude|reset, alias?}; предпочтение/исключение приложения для функции",
    "add_event" to "создать напоминание в календаре (суть — title, когда — when)",
    "navigate" to "открыть карты с маршрутом (адрес — address)",
    "disk_delete" to "удалить файл или папку на Яндекс Диске (путь на Диске — в path)",
    "disk_move" to "переместить файл или папку на Яндекс Диске (откуда — from, куда — to)",
    "disk_publish" to "создать публичную ссылку на файл Яндекс Диска (путь — в path); требует OAuth-токен (настройки → «Яндекс Диск»); в ответ придёт ссылка",
    "chat" to "развёрнутый ответ пользователю (текст — в reply)",
)

/** Локальные команды (исполняются на устройстве, без вызова модели). */
private val LOCAL_TOOLS = listOf(
    "remember" to "запомнить факт в память (локально, без вызова модели)",
    "forget" to "удалить факты из памяти (локально, без вызова модели)",
    "memory" to "показать содержимое памяти (локально, без вызова модели)",
)

private const val ALL_FILES_HINT = "ДОСТУП «ВСЕ ФАЙЛЫ» ВЫКЛЮЧЕН — скажи пользователю включить его: «+» → «Настройки» → «Все файлы»."

private const val DISK_HINT = "ЯНДЕКС ДИСК НЕ НАСТРОЕН — скажи пользователю настроить командой «настрой диск» (логин + пароль приложения)."

private const val DISK_PUBLISH_HINT = "ПУБЛИЧНЫЕ ССЫЛКИ НЕ НАСТРОЕНЫ — скажи пользователю настроить командой «настрой ссылки» (OAuth-токен; выдаётся на oauth.yandex.ru с правом cloud_api:disk.read)."

// ─── Палитра Walter «Моно · мягкий» (2026): белый фон, чёрно-серые акценты.
// Цвет — только у пузыря сообщений пользователя (мягкий синий). ──
private val DsBlue = Color(0xFF26282D) // основной акцент: тёмно-серый (кнопки, отправка, подсветки)
private val DsAccent = Color(0xFFF1F3F5) // светло-серая подложка (аватары, теги, треды, чипы)
private val DsBg = Color(0xFFFFFFFF) // фон — чистый белый
private val DsSoftBg = Color(0xFFF1F3F5) // вторичная поверхность
private val DsBorder = Color(0xFFE9EBEF)
private val DsText = Color(0xFF111214)
private val DsHint = Color(0xFF8A8F98)
private val DsIcon = Color(0xFF8A8F98)
private val DsSendDisabled = Color(0xFFE9EBEF)
private val DsUserBubble = Color(0xFFE3ECFB) // пузырь пользователя — мягкий синий
private val DsUserBubbleDeep = Color(0xFFD6E4F7) // сообщение пользователя в ветке события
private val Mono = FontFamily.SansSerif // системный шрифт всего UI (имя Mono — наследие терминальной темы)

/** Маркер списка инструментов в тексте ответа: «[[tools:a,b]]» → плашка справа от текста. */
private val TOOLS_MARK = Regex("""\[\[tools:([^\]]*)]\]""")

/** Разделяет текст ответа и список инструментов из маркера. */
fun splitTools(text: String): Pair<String, List<String>> {
    val m = TOOLS_MARK.find(text) ?: return text to emptyList()
    return text.substring(0, m.range.first).trimEnd() to
        m.groupValues[1].split(",").map { it.trim() }.filter { it.isNotBlank() }
}

/** Составной возврат «текст шага\u0000CONFIRM::…»: текст шага и текст плашки. */
fun splitConfirmReply(r: String): Pair<String, String> {
    val i = r.indexOf("\u0000CONFIRM::")
    return if (i >= 0) r.substring(0, i).trim() to r.substring(i).removePrefix("\u0000CONFIRM::").trim()
    else "" to r.removePrefix("CONFIRM::").trim()
}

/** Плавающий трекер задач: id — индекс сообщения в ленте (для веток — hash ключа + threadKey). */
data class ActiveTask(val id: Int, val label: String, val threadKey: String? = null)

/** Запись в истории задач (правый верхний угол): имя, якорь, шаг/всего, статус. */
data class TaskEntry(
    val uid: Long,
    val name: String,
    val anchorMsg: Int,
    val step: Int,
    val total: Int,
    val done: Boolean,
    val running: Boolean,
    val prompt: String = "" // исходный запрос для кнопки «повторить»
)

fun taskEntriesToJson(list: List<TaskEntry>): String = org.json.JSONArray().apply {
    list.forEach { e ->
        put(org.json.JSONObject()
            .put("uid", e.uid).put("name", e.name).put("anchor", e.anchorMsg)
            .put("step", e.step).put("total", e.total)
            .put("done", e.done).put("running", false).put("p", e.prompt))
    }
}.toString()

fun taskEntriesFromJson(raw: String?): List<TaskEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val a = org.json.JSONArray(raw)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            TaskEntry(
                o.optLong("uid"),
                o.optString("name"),
                o.optInt("anchor"),
                o.optInt("step"),
                o.optInt("total"),
                o.optBoolean("done"),
                running = false,
                o.optString("p", "")
            )
        }.filter { it.name.isNotBlank() }
    }.getOrDefault(emptyList())
}

/** Итоговая подпись завершённой задачи по последнему статусу. */
fun taskFinishLabel(status: String): String {
    val st = status.removeSuffix("…").trim()
    return when {
        st.contains("канал", ignoreCase = true) -> "Готово: каналы"
        st.startsWith("Ищу в") -> "Готово: поиск"
        st.startsWith("Читаю") -> "Готово: файлы"
        st.startsWith("Думаю") -> "Готово"
        else -> "Готово: " + st.take(24)
    }
}

/** Строка ленты чата: обычное сообщение или блок события (карточка + встроенная ветка). */
private sealed class Row {    data class M(val msg: Msg) : Row()
    data class E(
        val event: EventInfo,
        val key: String,
        val expanded: Boolean,
        val thread: List<Msg>
    ) : Row()
}

/** Действия карточек событий, файлов, оповещений и настроек инструментов. */
private data class CardActions(
    val onReply: (EventInfo) -> Unit,
    val openDiskDir: (FileRef) -> Unit,
    val diskListDir: suspend (FileRef) -> List<FileRef>?,
    val diskSnippet: suspend (FileRef) -> String?,
    val diskFile: suspend (FileRef) -> String?,
    val diskPreview: suspend (FileRef) -> ImageBitmap?,
    val diskDownload: suspend (FileRef) -> String?,
    val diskReplaced: (FileRef, String) -> Unit,
    val alertsList: () -> List<AlertDef>,
    val toolsData: () -> List<ToolCardView>,
    val toolChoices: () -> List<Pair<String, String>>,
    val updateAlert: (Long, String, String) -> Unit,
    val onWatchStart: (EventInfo) -> Unit,
    val onWatchDismiss: (EventInfo) -> Unit,
    val deleteAlert: (Long) -> Unit,
    val saveToolSetting: (String, String) -> Unit,
    val addToolAccount: (String, List<String>) -> Unit,
    val toggleWatch: (Long) -> Unit,
    val watchDisabled: () -> Set<Long>
)

class MainActivity : ComponentActivity() {
    /** Наш экран на переднем плане? (для фолбэка «contentIntent не открыл экран») */
    @Volatile var walterForeground = true
        private set

    override fun onResume() {
        super.onResume()
        walterForeground = true
    }

    override fun onPause() {
        super.onPause()
        walterForeground = false
    }


    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS) // стрим может долго молчать между чанками
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var appIndex: String = ""

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DebugLog.log("e2e", "onNewIntent: ${intent.getStringExtra("e2e_b64")?.take(20)}")
        setIntent(intent)
        val b64 = intent.getStringExtra("e2e_b64")
        if (b64 != null && BuildConfig.DEBUG) {
            intent.removeExtra("e2e_b64")
            val text = runCatching {
                String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
            }.getOrDefault("")
            if (text.isNotBlank()) {
                pendingE2e.value = text
            }
        }
        val watch64 = intent.getStringExtra("e2e_watch_b64")
        if (watch64 != null && BuildConfig.DEBUG) {
            DebugLog.log("e2e-watch", "intent получен")
            intent.removeExtra("e2e_watch_b64")
            pendingWatch.value = runCatching {
                String(android.util.Base64.decode(watch64, android.util.Base64.DEFAULT), Charsets.UTF_8)
            }.getOrDefault("")
        }
    }

    private val pendingWatch = androidx.compose.runtime.mutableStateOf<String?>(null)

    private val pendingE2e = androidx.compose.runtime.mutableStateOf<String?>(null)
    // Фактическая высота клавиатуры из нативных WindowInsets (не зависит от Compose-инсетов).
    private val imeInsetBottomPx = androidx.compose.runtime.mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // краши — в walter_debug.log (иначе стек теряется вместе с процессом)
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                DebugLog.log("crash", "FATAL: ${e.javaClass.name}: ${e.message}\n" +
                    e.stackTrace.take(25).joinToString("\n") { it.toString() })
            }
            prevHandler?.uncaughtException(t, e)
        }
        // PDFBox-порт на Android: инициализация ресурсов (CMAP для извлечения текста из PDF)
        try { PDFBoxResourceLoader.init(applicationContext) } catch (t: Throwable) { /* PDF-чтение будет с ошибкой */ }
        appIndex = installedApps().joinToString(", ")
        Log.d("Dispatcher", "Visible launcher apps: ${appIndex.split(", ").size}")
        val ctx = this
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val storedKey = prefs.getString(Prefs.KEY_API, "") ?: ""

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Единый механизм: система окно не двигает (adjustNothing), высоту клавиатуры
        // измеряем нативно и сами уменьшаем нижнюю границу чата.
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val applied = (ime - nav).coerceAtLeast(0)
            if (imeInsetBottomPx.value != applied) {
                imeInsetBottomPx.value = applied
                DebugLog.log("ime-diag", "ime=${ime}px nav=${nav}px applied=${applied}px")
            }
            insets
        }
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = DsBlue,
                    background = DsBg,
                    surface = DsBg
                )
            ) {
                var apiKey by remember { mutableStateOf(storedKey) }
                var apiUrl by remember { mutableStateOf(prefs.getString(Prefs.KEY_BASE_URL, BASE_URL) ?: BASE_URL) }
                var showSettings by remember { mutableStateOf(storedKey.isBlank()) }
                var smsGranted by remember { mutableStateOf(false) }
                var readSmsGranted by remember { mutableStateOf(false) }
                var pushAccess by remember { mutableStateOf(false) }
                var allFilesAccess by remember { mutableStateOf(false) }
                var calendarGranted by remember { mutableStateOf(false) }
                // настройки почты (IMAP)
                // почта: несколько IMAP-аккаунтов (Яндекс/Gmail, пароли приложений)
                var mailAccounts by remember { mutableStateOf(MailAccounts.fromJson(prefs.getString(Prefs.KEY_MAIL_ACCOUNTS, null))) }
                // геокодер: отдельный бесплатный ключ (фолбэк — поисковый ключ Яндекса)
                var geoKey by remember { mutableStateOf(prefs.getString(Prefs.KEY_GEO_KEY, "") ?: "") }
                var geoUrl by remember { mutableStateOf(prefs.getString(Prefs.KEY_GEO_URL, "https://geocode-maps.yandex.ru/v1/") ?: "https://geocode-maps.yandex.ru/v1/") }
                var locationGranted by remember { mutableStateOf(false) }
                // календари: системный (разрешение) + несколько Яндекс-аккаунтов по CalDAV
                var caldavAccounts by remember { mutableStateOf(CalDavAccounts.fromJson(prefs.getString(Prefs.KEY_CALDAV_ACCOUNTS, null))) }
                // Каналы Telegram хранятся отдельно в настройках инструмента.
                var tgChannels by remember { mutableStateOf(TelegramChannels.channelsFromJson(prefs.getString(Prefs.KEY_TG_CHANNELS, null))) }
                // Яндекс Диск: WebDAV-аккаунты (логин + пароль приложения) + OAuth-токен для публичных ссылок
                var diskAccounts by remember { mutableStateOf(DiskAccounts.fromJson(prefs.getString(Prefs.KEY_DISK_ACCOUNTS, null))) }
                var diskToken by remember { mutableStateOf(prefs.getString(Prefs.KEY_DISK_TOKEN, "") ?: "") }

                // миграция одиночных настроек (старых версий) в списки аккаунтов
                LaunchedEffect(Unit) {
                    if (prefs.getString(Prefs.KEY_MAIL_ACCOUNTS, null).isNullOrBlank()) {
                        val h = prefs.getString(Prefs.KEY_MAIL_HOST, "") ?: ""
                        val u = prefs.getString(Prefs.KEY_MAIL_USER, "") ?: ""
                        val p = prefs.getString(Prefs.KEY_MAIL_PASS, "") ?: ""
                        val port = prefs.getString(Prefs.KEY_MAIL_PORT, "993")?.toIntOrNull() ?: 993
                        val legacy = MailAccounts.fromLegacy(h, port, u, p)
                        if (legacy.isNotEmpty()) {
                            prefs.edit().putString(Prefs.KEY_MAIL_ACCOUNTS, MailAccounts.toJson(legacy)).apply()
                        }
                        prefs.edit().remove(Prefs.KEY_MAIL_HOST).remove(Prefs.KEY_MAIL_PORT)
                            .remove(Prefs.KEY_MAIL_USER).remove(Prefs.KEY_MAIL_PASS).apply()
                        mailAccounts = MailAccounts.fromJson(prefs.getString(Prefs.KEY_MAIL_ACCOUNTS, null))
                    }
                    if (prefs.getString(Prefs.KEY_CALDAV_ACCOUNTS, null).isNullOrBlank()) {
                        val u = prefs.getString(Prefs.KEY_CALDAV_USER, "") ?: ""
                        val p = prefs.getString(Prefs.KEY_CALDAV_PASS, "") ?: ""
                        if (u.isNotBlank() && p.isNotBlank()) {
                            val url = prefs.getString(Prefs.KEY_CALDAV_URL, CalDavStore.DEFAULT_SERVER) ?: CalDavStore.DEFAULT_SERVER
                            val legacy = listOf(CalDavAccount(1L, u.substringBefore('@'), url, u, p))
                            prefs.edit().putString(Prefs.KEY_CALDAV_ACCOUNTS, CalDavAccounts.toJson(legacy)).apply()
                        }
                        prefs.edit().remove(Prefs.KEY_CALDAV_URL).remove(Prefs.KEY_CALDAV_USER).remove(Prefs.KEY_CALDAV_PASS).apply()
                        caldavAccounts = CalDavAccounts.fromJson(prefs.getString(Prefs.KEY_CALDAV_ACCOUNTS, null))
                    }
                }
                var alerts by remember { mutableStateOf(AlertStore.load(prefs)) }
                // Выключенные оповещения хранятся как набор id; по умолчанию все включены.
                var watchDisabledIds by remember { mutableStateOf(WatchEngine.disabledFromJson(prefs.getString(WatchEngine.KEY_DISABLED, null))) }
                var searchProvider by remember { mutableStateOf(prefs.getString(Prefs.KEY_SEARCH_PROVIDER, "ddg") ?: "ddg") }
                var yandexKey by remember { mutableStateOf(prefs.getString(Prefs.KEY_YANDEX_KEY, "") ?: "") }
                var yandexUrl by remember { mutableStateOf(prefs.getString(Prefs.KEY_YANDEX_URL, YANDEX_SEARCH_URL) ?: YANDEX_SEARCH_URL) }
                var streamTick by remember { mutableStateOf(0) }
                var isStreaming by remember { mutableStateOf(false) }
                // плавающий трекер долгих задач (правый верхний угол)
                var activeTasks by remember { mutableStateOf(listOf<ActiveTask>()) }
                var finishedTasks by remember { mutableStateOf(listOf<ActiveTask>()) }
                var scrollTarget by remember { mutableStateOf<Int?>(null) }
                // авто-прокрутка к задаче: индекс сообщения (чат) или ключ ветки (событие)
                var scrollMsg by remember { mutableStateOf<Int?>(null) }
                var scrollThread by remember { mutableStateOf<String?>(null) }
                // последние 5 задач — всегда в правом верхнем углу
                var taskHistory by remember { mutableStateOf(listOf<TaskEntry>()) }
                var tasksExpanded by remember { mutableStateOf(false) }
                fun upsertTask(uid: Long, name: String, anchor: Int, step: Int, total: Int, done: Boolean, running: Boolean, prompt: String = "") {
                    val e = TaskEntry(uid, name, anchor, step, total, done, running, prompt)
                    taskHistory = (listOf(e) + taskHistory.filter { it.uid != uid }).take(5)
                    prefs.edit().putString(Prefs.KEY_TASK_HISTORY, taskEntriesToJson(taskHistory)).apply()
                }
                fun removeTask(uid: Long) {
                    taskHistory = taskHistory.filter { it.uid != uid }
                    prefs.edit().putString(Prefs.KEY_TASK_HISTORY, taskEntriesToJson(taskHistory)).apply()
                }
                LaunchedEffect(Unit) {
                    if (taskHistory.isEmpty()) {
                        taskHistory = taskEntriesFromJson(prefs.getString(Prefs.KEY_TASK_HISTORY, null))
                    }
                }
                var input by remember { mutableStateOf("") }
                var lastUser by remember { mutableStateOf(prefs.getString(Prefs.KEY_LAST, "") ?: "") }
                val messages = remember {
                    mutableStateListOf<Msg>().apply {
                        // при старте ожидания подтверждений не восстанавливаем: иначе ввод заперт «в никуда»
                        addAll(loadMessages(prefs).map { if (it.askConfirm != null || it.planInput) it.copy(resumeMsgs = null) else it })
                    }
                }
                var threads by remember {
                    mutableStateOf(loadAllThreads(prefs).mapValues { (_, list) ->
                        list.map { if (it.askConfirm != null || it.planInput) it.copy(resumeMsgs = null) else it }
                    })
                }
                var memoryState by remember { mutableStateOf(MemoryStore.load(prefs)) }
                val expandedKeys = remember { mutableStateListOf<String>() }
                // высоты карточек событий (для фокуса на конце ветки при раскрытии)
                val cardHeights = remember { mutableStateMapOf<String, Int>() }
                // «Обработать»: подходящие промпты из реальной истории пользователя по каждому событию
                val eventSuggestions = remember { mutableStateMapOf<String, List<String>>() }
                val eventSuggestionLoading = remember { mutableStateListOf<String>() }
                fun processEventSuggestion(ev: EventInfo, rowKey: String? = null) {
                    val key = rowKey ?: eventKey(ev)
                    DebugLog.log("suggest", "start: $key")
                    if (eventSuggestionLoading.contains(key)) return
                    val candidates = messages.filter { it.fromUser }
                        .map { it.text.trim() }
                        .filter { it.isNotBlank() && it.length <= 500 }
                        .distinct().takeLast(30)
                    if (candidates.isEmpty()) {
                        eventSuggestions[key] = emptyList()
                        return
                    }
                    eventSuggestionLoading.add(key)
                    lifecycleScope.launch {
                        val sys = "Ты выбираешь подходящие промпты для входящего пуша ТОЛЬКО из списка уже введённых пользователем. " +
                            "Верни СТРОГО JSON {\"prompt_ids\":[номера]} — 1-4 id самых подходящих запросов по смыслу, иначе пустой массив. Ничего не придумывай."
                        val user = "ПУШ: ${ev.source}\n${ev.title}\n${ev.text}\n\nПРЕЖНИЕ ПРОМПТЫ:\n" +
                            candidates.mapIndexed { i, p -> "${i + 1}. $p" }.joinToString("\n")
                        val raw = runCatching { ModelApi.raw(sys, user, apiKey, apiUrl, client) }.getOrNull()
                        DebugLog.log("suggest", "model: $key -> ${raw?.take(100) ?: "null"}")
                        val ids = runCatching {
                            val arr = org.json.JSONObject(raw).optJSONArray("prompt_ids")
                            if (arr == null) emptyList() else (0 until arr.length()).map { arr.optInt(it) }
                        }.getOrDefault(emptyList())
                        eventSuggestions[key] = ids.mapNotNull { id -> candidates.getOrNull(id - 1) }.distinct().take(4)
                        eventSuggestionLoading.remove(key)
                        DebugLog.log("suggest", "done: $key ids=$ids")
                    }
                }
                // ветка, в которую сейчас отвечает основное поле ввода
                var activeKey by remember { mutableStateOf<String?>(null) }
                // сигнал «новый контент внизу» — прокрутка только по нему,
                // раскрытие/сворачивание тредов ленту не дёргает
                var scrollSignal by remember { mutableStateOf(0) }
                // ответ в тред — фокус на последнем сообщении ветки
                var scrollThreadSignal by remember { mutableStateOf(0) }
                val clipboard = LocalClipboardManager.current

                // Два штатных оповещения добавляются один раз; правки и удаления сохраняются.
                LaunchedEffect(Unit) {
                    AlertStore.ensurePresets(prefs)
                    alerts = AlertStore.load(prefs)
                }

                // приветствие дворецкого — один раз при первом запуске
                LaunchedEffect(Unit) {
                    if (!prefs.getBoolean(Prefs.KEY_WELCOME, false)) {
                        prefs.edit().putBoolean(Prefs.KEY_WELCOME, true).apply()
                        if (messages.isEmpty()) {
                            messages += Msg(
                                false,
                                "Добрый день. Я Walter — ваш персональный ассистент. Чем могу помочь?"
                            )
                            val arr = JSONArray()
                            messages.forEach { m -> arr.put(msgToJson(m)) }
                            prefs.edit().putString(Prefs.KEY_MESSAGES, arr.toString()).apply()
                            scrollSignal++
                        }
                    }
                }

                var notifGranted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                    )
                }
                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { g -> notifGranted = g }
                val smsPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> smsGranted = granted }

                val pushSettingsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    pushAccess = NotificationManagerCompat
                        .getEnabledListenerPackages(ctx).contains(ctx.packageName)
                }
                val lastImeBottom = imeInsetBottomPx.value
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val obs = LifecycleEventObserver { _, e ->
                        if (e == Lifecycle.Event.ON_RESUME) {
                            pushAccess = NotificationManagerCompat
                                .getEnabledListenerPackages(ctx).contains(ctx.packageName)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(obs)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
                }
                val readSmsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> readSmsGranted = granted }

                val calendarLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> calendarGranted = granted }

                val locationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { res ->
                    locationGranted = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                }

                LaunchedEffect(showSettings) {
                    if (showSettings) {
                        smsGranted = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        readSmsGranted = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.READ_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        calendarGranted = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.READ_CALENDAR
                        ) == PackageManager.PERMISSION_GRANTED
                        locationGranted = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        pushAccess = NotificationManagerCompat
                            .getEnabledListenerPackages(ctx).contains(ctx.packageName)
                        allFilesAccess = FileStore.hasAllFilesAccess()
                    }
                }

                val persist = {
                    val arr = JSONArray()
                    messages.forEach { m -> arr.put(msgToJson(m)) }
                    prefs.edit()
                        .putString(Prefs.KEY_MESSAGES, arr.toString())
                        .putString(Prefs.KEY_LAST, lastUser)
                        .apply()
                }

                // периодическая оценка следилок по «тянущим» каналам (почта/каналы/календарь)
                suspend fun evaluateWatchPull() {
                    val key = apiKey
                    if (key.isBlank()) return
                    val disabled = WatchEngine.disabledFromJson(prefs.getString(WatchEngine.KEY_DISABLED, null))
                    val ws = alerts.filter { WatchEngine.isEnabled(it.id, disabled) }
                    if (ws.isEmpty()) return
                    val now = System.currentTimeMillis()
                    for (s in ws) {
                        if (WatchEngine.snoozed(prefs, s.id)) continue
                        if (now - WatchEngine.lastFire(prefs, s.id) < WatchEngine.MIN_INTERVAL_MS) continue
                        // 1) фоновый инструмент (канал «фоновый инструмент»):
                        // модель сама вызывает инструменты по инструкциям (параметры/ссылки — там же)
                        if (s.watchChannel == "tool" && s.watchTool.isNotBlank() && s.watchEvery > 0) {
                            val last = WatchEngine.lastPull(prefs, s.id)
                            if (now - last < s.watchEvery * 60_000L) continue
                            WatchEngine.markPull(prefs, s.id, now)
                            val sysExtra =
                                "\n\nРЕЖИМ СЛЕДИЛКИ «${s.name}». ПРАВИЛО: ${WatchEngine.ruleOf(s)}\n" +
                                "ОСНОВНОЙ ИНСТРУМЕНТ: ${s.watchTool}\n" +
                                "ИНСТРУКЦИИ ОПОВЕЩЕНИЯ (откуда брать данные и с какими параметрами вызывать инструменты):\n${s.instructions}\n\n" +
                                "Вызови инструменты, чтобы получить данные, затем ответь СТРОГО одним JSON: {\"fire\":true,\"text\":\"...\"} " +
                                "или {\"fire\":false}. Если сработало — text это короткое сообщение пользователю."
                            val reply = runCatching {
                                router(
                                    "Проверь правило следилки", key, emptyList(), "", sysExtra,
                                    initialStatus = "Проверяю «${s.name}»…",
                                    onStatus = { }, onDelta = { }, onFiles = { }, onTools = { }, onEvents = { }
                                )
                            }.getOrDefault("")
                            val (fire, text) = WatchEngine.parseFire(reply)
                            if (fire && !text.isNullOrBlank()) {
                                WatchEngine.markFired(prefs, s.id, now)
                                WatchEngine.deliver(this@MainActivity, s, text, WatchEngine.parseFirePrompt(reply))
                            }
                            continue
                        }
                        // 2) Периодическая проверка по инструментам оповещения.
                        val tools = s.tools
                        val data = when {
                            tools.contains("read_mail") -> "ПОЧТА:\n" + readMailData("5") { }
                            tools.contains("fetch_url") -> "TELEGRAM-КАНАЛЫ:\n" + readTelegramChannelsData("")
                            tools.contains("read_calendar") -> "КАЛЕНДАРЬ:\n" + readCalendarData("неделя") { }
                            else -> continue
                        }
                        val user = "ПРАВИЛО СЛЕДИЛКИ «${s.name}»:\n${WatchEngine.ruleOf(s)}\n\n" +
                            "ДАННЫЕ КАНАЛА:\n${data.take(4000)}\n\n" +
                            "Проверь, срабатывает ли правило. Отвечай СТРОГО одним JSON: {\"fire\":true,\"text\":\"...\"} " +
                            "или {\"fire\":false}. Если сработало — text это короткое сообщение пользователю."
                        val reply = runCatching {
                            ModelApi.raw(WatchEngine.SYSTEM_PROMPT, user, key, apiUrl, client)
                        }.getOrNull()
                        val (fire, text) = reply?.let { WatchEngine.parseFire(it) } ?: (false to null)
                        if (fire && !text.isNullOrBlank()) {
                            WatchEngine.markFired(prefs, s.id, now)
                            WatchEngine.deliver(this@MainActivity, s, text, reply?.let { WatchEngine.parseFirePrompt(it) })
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    while (true) {
                        delay(10 * 60_000L)
                        try {
                            evaluateWatchPull()
                        } catch (t: Throwable) {
                            DebugLog.log("watch-pull", t.message ?: t.javaClass.simpleName)
                        }
                    }
                }

                fun activeEvent(): EventInfo? {
                    val k = activeKey ?: return null
                    val direct = messages.firstOrNull { it.event != null && eventKey(it.event) == k }
                    if (direct != null) return direct.event
                    for (m in messages) {
                        m.events.forEachIndexed { i, e ->
                            if (eventListKey(e, i) == k) return e
                        }
                    }
                    return null
                }

                val activeLabel: String? = activeEvent()?.let { e ->
                    (when (e.kind) {
                        "sms" -> "SMS"
                        "mail" -> "Почта"
                        "calendar" -> "Событие"
                        else -> "Пуш"
                    }) + " · " + e.source
                }
                fun expandOnly(key: String) {
                    expandedKeys.clear()
                    expandedKeys.add(key)
                    // activeKey не трогаем: карточки статичны — ни подсветки, ни метки «ветки»
                }

                // события, пришедшие пока чат был закрыт
                LaunchedEffect(Unit) {
                    EventHub.drain(ctx).forEach { e ->
                        messages += Msg(false, "", e)
                        persist()
                        scrollSignal++
                    }
                }

                // Живые события добавляются карточкой в чат без автораскрытия.
                DisposableEffect(Unit) {
                    EventHub.attach { e ->
                        messages += Msg(false, "", e)
                        persist()
                        scrollSignal++
                    }
                    onDispose { EventHub.attach(null) }
                }

                fun rememberText(role: String, text: String, source: String) {
                    // Перечитываем хранилище: действия памяти, выбранные моделью, уже могли его изменить.
                    memoryState = MemoryStore.append(MemoryStore.load(prefs), role, text, source)
                    MemoryStore.save(prefs, memoryState)
                }


                /** Сохранение поля оповещения из его карточки. */
                fun saveAlertField(id: Long, field: String, value: String) {
                    AlertStore.update(prefs, id, field, value)
                    alerts = AlertStore.load(prefs)
                }

                fun deleteAlertById(id: Long) {
                    AlertStore.remove(prefs, id)
                    alerts = AlertStore.load(prefs)
                }

                /** Сохранение настройки инструмента из карточки (editKey → prefs). */
                fun saveToolSetting(key: String, value: String) {
                    val v = value.trim()
                    when (key) {
                        "search.provider" -> if (v == "ddg" || v == "yandex") {
                            searchProvider = v
                            prefs.edit().putString(Prefs.KEY_SEARCH_PROVIDER, v).apply()
                        }
                        "search.key" -> {
                            yandexKey = v
                            prefs.edit().putString(Prefs.KEY_YANDEX_KEY, v).apply()
                        }
                        "search.url" -> {
                            yandexUrl = v
                            prefs.edit().putString(Prefs.KEY_YANDEX_URL, v).apply()
                        }
                        "geo.key" -> {
                            geoKey = v
                            prefs.edit().putString(Prefs.KEY_GEO_KEY, v).apply()
                        }
                        "geo.url" -> {
                            geoUrl = v
                            prefs.edit().putString(Prefs.KEY_GEO_URL, v).apply()
                        }
                        "oauth" -> {
                            diskToken = v
                            prefs.edit().putString(Prefs.KEY_DISK_TOKEN, v).apply()
                        }
                        "tg.channels" -> {
                            val channels = TelegramChannels.parseChannelsInput(value)
                            tgChannels = channels
                            prefs.edit().putString(Prefs.KEY_TG_CHANNELS, TelegramChannels.channelsToJson(channels)).apply()
                        }
                    }
                    // редактирование данных аккаунта: ключи mail.N.* / caldav.N.* / disk.N.*
                    if (key.startsWith("mail.")) {
                        val p = key.split('.')
                        val i = p.getOrNull(1)?.toIntOrNull() ?: return
                        val list = MailAccounts.fromJson(prefs.getString(Prefs.KEY_MAIL_ACCOUNTS, null)).toMutableList()
                        if (i < list.size) {
                            val a = list[i]
                            list[i] = when (p.getOrNull(2)) {
                                "host" -> {
                                    val hp = v.split(':')
                                    val host = hp.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() } ?: a.host
                                    val port = hp.getOrNull(1)?.trim()?.toIntOrNull() ?: a.port
                                    a.copy(host = host, port = port)
                                }
                                "user" -> a.copy(user = v)
                                "pass" -> a.copy(pass = v)
                                else -> a
                            }
                            mailAccounts = list
                            prefs.edit().putString(Prefs.KEY_MAIL_ACCOUNTS, MailAccounts.toJson(list)).apply()
                        }
                    } else if (key.startsWith("caldav.")) {
                        val p = key.split('.')
                        val i = p.getOrNull(1)?.toIntOrNull() ?: return
                        val list = CalDavAccounts.fromJson(prefs.getString(Prefs.KEY_CALDAV_ACCOUNTS, null)).toMutableList()
                        if (i < list.size) {
                            val a = list[i]
                            list[i] = when (p.getOrNull(2)) {
                                "label" -> a.copy(label = v)
                                "url" -> a.copy(url = v)
                                "user" -> a.copy(user = v)
                                "pass" -> a.copy(pass = v)
                                else -> a
                            }
                            caldavAccounts = list
                            prefs.edit().putString(Prefs.KEY_CALDAV_ACCOUNTS, CalDavAccounts.toJson(list)).apply()
                        }
                    } else if (key.startsWith("disk.")) {
                        val p = key.split('.')
                        val i = p.getOrNull(1)?.toIntOrNull() ?: return
                        val list = DiskAccounts.fromJson(prefs.getString(Prefs.KEY_DISK_ACCOUNTS, null)).toMutableList()
                        if (i < list.size) {
                            val a = list[i]
                            list[i] = when (p.getOrNull(2)) {
                                "server" -> a.copy(baseUrl = v)
                                "user" -> a.copy(user = v)
                                "pass" -> a.copy(pass = v)
                                else -> a
                            }
                            diskAccounts = list
                            prefs.edit().putString(Prefs.KEY_DISK_ACCOUNTS, DiskAccounts.toJson(list)).apply()
                        }
                    }
                }

                /** Добавление аккаунта из карточки инструмента. */
                fun addToolAccount(toolId: String, values: List<String>) {
                    when (toolId) {
                        "read_mail" -> {
                            val host = values.getOrElse(0) { "" }.trim()
                            val user = values.getOrElse(2) { "" }.trim()
                            val pass = values.getOrElse(3) { "" }.trim()
                            if (host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                                val port = values.getOrElse(1) { "993" }.trim().toIntOrNull() ?: 993
                                val next = MailAccounts.add(mailAccounts, host, port, user, pass)
                                mailAccounts = next
                                prefs.edit().putString(Prefs.KEY_MAIL_ACCOUNTS, MailAccounts.toJson(next)).apply()
                            }
                        }
                        "read_calendar" -> {
                            val label = values.getOrElse(0) { "" }.trim()
                            val url = values.getOrElse(1) { "" }.trim()
                            val user = values.getOrElse(2) { "" }.trim()
                            val pass = values.getOrElse(3) { "" }.trim()
                            if (url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                                val next = CalDavAccounts.add(caldavAccounts, label, url, user, pass)
                                caldavAccounts = next
                                prefs.edit().putString(Prefs.KEY_CALDAV_ACCOUNTS, CalDavAccounts.toJson(next)).apply()
                            }
                        }
                        "disk_list" -> {
                            val server = values.getOrElse(0) { "" }.trim()
                            val user = values.getOrElse(1) { "" }.trim()
                            val pass = values.getOrElse(2) { "" }.trim()
                            if (user.isNotBlank() && pass.isNotBlank()) {
                                val next = DiskAccounts.add(diskAccounts, server, user, pass)
                                diskAccounts = next
                                prefs.edit().putString(Prefs.KEY_DISK_ACCOUNTS, DiskAccounts.toJson(next)).apply()
                            }
                        }
                    }
                }

                fun threadReplyLocal(ev: EventInfo, q: String, replyText: String) {
                    val key = eventKey(ev)
                    val cur = threads[key] ?: emptyList()
                    val withUser = cur + Msg(true, q)
                    threads = threads + (key to withUser)
                    saveThread(prefs, key, withUser)
                    scrollThreadSignal++
                    rememberText("user", q, key)
                    val withReply = (threads[key] ?: withUser) + Msg(false, replyText)
                    threads = threads + (key to withReply)
                    saveThread(prefs, key, withReply)
                    scrollThreadSignal++
                    rememberText("agent", replyText, key)
                }

                fun threadSend(ev: EventInfo, q0: String, extra: String, initialStatus: String = "Думаю", threadKeyOverride: String? = null) {
                    val q = q0.trim()
                    if (q.isEmpty()) return
                    // для карточек списков тула ключ — eventListKey (позиция в списке), а не eventKey
                    val key = threadKeyOverride ?: eventKey(ev)
                    val cur = threads[key] ?: emptyList()
                    val runUid = System.nanoTime()
                    val runLabel = q.take(26)
                    if (PlanFlow.isPlan(q)) {
                        upsertTask(runUid, runLabel, 0, 1, 1, done = false, running = true, prompt = q)
                    }
                    val withUser = cur + Msg(true, q)
                    threads = threads + (key to withUser)
                    saveThread(prefs, key, withUser)
                    scrollThreadSignal++
                    rememberText("user", q, key)
                    val withPlaceholder = (threads[key] ?: withUser) + Msg(false, "")
                    threads = threads + (key to withPlaceholder)
                    val apiK = apiKey
                    val mem = MemoryStore.buildMemoryBlock(memoryState, threads)
                    activeTasks = activeTasks + ActiveTask(key.hashCode(), initialStatus, threadKey = key)
                    scrollThread = key // отматываем к карточке события, где выполняется задача
                    lifecycleScope.launch {
                        isStreaming = true
                        try {
                            var replyFiles: List<FileRef> = emptyList()
                            var usedTools: Set<String> = emptySet()
                            var replyEvents: List<EventInfo> = emptyList()
                            val reply = threadRouter(
                                ev, q, apiK, withUser, mem, extra,
                                initialStatus = initialStatus,
                                onStatus = { st ->
                                    val curList = threads[key] ?: return@threadRouter
                                    threads = threads + (key to (curList.dropLast(1) + Msg(false, st)))
                                    activeTasks = activeTasks.map { if (it.threadKey == key) it.copy(label = st) else it }
                                    streamTick++
                                },
                                onDelta = { d ->
                                    val curList = threads[key] ?: return@threadRouter
                                    val last = curList.lastOrNull()?.text ?: ""
                                    threads = threads + (key to (curList.dropLast(1) + Msg(false, last + d)))
                                    streamTick++
                                },
                                onFiles = { replyFiles = it },
                                onTools = { usedTools = it; planTools += it },
                                onEvents = { replyEvents = it },
                                onTool = { t -> usedTools = usedTools + t; planTools += t }
                            )
                            val cardEvents = if (usedTools.any { it in EVENT_LIST_ACTIONS }) replyEvents else emptyList()
                            val shownTools = usedTools.filter { it != "chat" }
                            val tLine = if (shownTools.isNotEmpty()) "[[tools:" + shownTools.joinToString(",") + "]]" else ""
                            if (reply.contains("\u0000CONFIRM::") || reply.startsWith("CONFIRM::")) {
                                val (_, confirmText0) = splitConfirmReply(reply)
                                val confirmText = confirmText0
                                val nextStep = PlanFlow.nextStep(planQ, planConfirmDone)
                                val full = if (nextStep.isNotBlank() && !confirmText.contains(nextStep))
                                    confirmText + "\n\nСледующий шаг плана: $nextStep" else confirmText
                                if (usedTools.any { it != "chat" && it != "ask_confirm" }) {
                                    upsertTask(runUid, runLabel, 0, 1, 1, done = false, running = false, prompt = q)
                                }
                                val confirmList = (threads[key] ?: withUser).dropLast(1) +
                                    Msg(false, "", askConfirm = full.ifBlank { "Подтвердить" },
                                        resumeMsgs = confirmMsgs?.toString(), resumePlan = planMode,
                                        resumePlanQ = planQ, resumePlanDone = planConfirmDone,
                                        bubbleUid = runUid, bubblePrompt = q,
                                    planInput = planMode && PlanFlow.isUserInputStep(PlanFlow.nextStep(planQ, planConfirmDone)))
                                threads = threads + (key to confirmList)
                                saveThread(prefs, key, confirmList)
                                activeTasks = activeTasks.filter { it.threadKey != key }
                                scrollThread = key
                                scrollThreadSignal++
                                return@launch
                            }
                            val finalList = (threads[key] ?: withUser).dropLast(1) + Msg(false, reply, files = replyFiles, events = cardEvents, toolsChain = shownTools)
                            threads = threads + (key to finalList)
                            saveThread(prefs, key, finalList)
                            rememberText("agent", reply, key)
                            if (usedTools.any { it != "chat" && it != "ask_confirm" }) {
                                upsertTask(runUid, runLabel, 0, 1, 1, done = true, running = false, prompt = q)
                            }
                            val t = activeTasks.firstOrNull { it.threadKey == key }
                            activeTasks = activeTasks.filter { it.threadKey != key }
                            if (t != null) finishedTasks = finishedTasks + ActiveTask(t.id, taskFinishLabel(t.label), threadKey = key)
                            scrollThread = key // готово: отматываем на результат в ветке
                        } catch (e: Exception) {
                            val curList = threads[key] ?: withUser
                            threads = threads + (key to (curList.dropLast(1) + Msg(false, "Ошибка: ${e.message ?: e.javaClass.simpleName}")))
                            activeTasks = activeTasks.filter { it.threadKey != key }
                        } finally {
                            isStreaming = false
                            scrollThreadSignal++
                        }
                    }
                }

                /** Короткое имя бабла (1-3 слова) придумывает модель. */
                fun bubbleSummary(uid: Long, anchor: Int, q: String) {
                    lifecycleScope.launch {
                        val summary = runCatching {
                            val sys = "Дай название из 1-3 слов (без кавычек, без точки в конце) для действия, которое ассистент выполнил по запросу пользователя: «$q»"
                            ModelApi.raw(sys, q, apiKey, apiUrl, client).trim().take(30)
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                        if (!summary.isNullOrBlank()) {
                            upsertTask(uid, summary, anchor, 1, 1, done = true, running = false, prompt = q)
                        }
                    }
                }

                /** Если модель пропустила печать итога шага — генерируем его отдельным вызовом. */
                fun fillSkippedSummary(idx: Int, planText: String, remainderText: String) {
                    lifecycleScope.launch {
                        val sum = runCatching {
                            val sys = "Ты выполнял шаг многошагового плана пользователя. Дай ПОЛНЫЙ итог выполненного шага для пользователя (как твой reply):\nПЛАН: «$planText»\nОСТАЛОСЬ ПОСЛЕ ШАГА: «$remainderText»\nВерни только текст итога, без JSON и кавычек."
                            ModelApi.raw(sys, "итог шага", apiKey, apiUrl, client).trim()
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                        if (!sum.isNullOrBlank()) {
                            val cur = messages.getOrNull(idx)
                            if (cur != null && (cur.text.isBlank() || cur.text == "Думаю")) {
                                messages[idx] = cur.copy(text = "Готово\n" + sum)
                                persist()
                                streamTick++
                            }
                        }
                    }
                }

                fun resumeConfirm(m: Msg, userText: String) {
                    DebugLog.log("plan-resume", "start: $userText")
                    val mi = messages.indexOfFirst { it === m }
                    if (mi < 0) {
                        DebugLog.log("plan-resume", "сообщение не найдено")
                        return
                    }
                    // плашка остаётся репликой Walter; ответ пользователя — обычным сообщением
                    messages[mi] = m.copy(resumeMsgs = null)
                    messages += Msg(true, userText)
                    // ответ модели печатается новым сообщением в общий чат
                    messages += Msg(false, "")
                    val newIdx = messages.lastIndex
                    persist()
                    scrollSignal++
                    var msgs2 = runCatching { org.json.JSONArray(m.resumeMsgs) }.getOrNull()
                    val plan = m.resumePlan
                    val planQ0 = m.resumePlanQ ?: ""
                    val planDone = m.resumePlanDone
                    DebugLog.log("plan-resume", "msgs2=${msgs2 != null} plan=$plan done=$planDone")
                    if (m.planInput) {
                        if (m.askConfirm != null && PlanFlow.isConfirmation(userText)) {
                            // Подтверждение только включает режим ввода: модель не вызываем.
                            messages[mi] = m.copy(resumeMsgs = m.resumeMsgs)
                            messages[newIdx] = messages[newIdx].copy(text = "Готово")
                            persist()
                            scrollSignal++
                            return
                        }
                        if (!PlanFlow.isQuestionStep(PlanFlow.nextStep(planQ0, planDone))) {
                            // Явное «дождись…»: после ввода показываем подтверждение следующего шага.
                            messages.removeAt(newIdx)
                            messages += Msg(false, "",
                                askConfirm = PlanFlow.forcedConfirmText(planQ0, planDone + 1),
                                resumeMsgs = m.resumeMsgs,
                                resumePlan = plan,
                                resumePlanQ = planQ0,
                                resumePlanDone = planDone + 1,
                                bubbleUid = m.bubbleUid,
                                bubblePrompt = m.bubblePrompt,
                                planInputText = userText)
                            persist()
                            scrollSignal++
                            return
                        }
                        // После вопроса ответ — данные текущего сегмента: выполняем его сразу,
                        // без перехода к следующему подтверждению и без пропуска действий.
                        messages[mi] = m.copy(resumeMsgs = m.resumeMsgs, planInputText = userText)
                    }
                    if (msgs2 == null) {
                        if (messages.getOrNull(newIdx) != null) {
                            messages[newIdx] = messages[newIdx].copy(text = "Не удалось продолжить — состояние не сохранилось.")
                        }
                        persist()
                        scrollSignal++
                        return
                    }
                    val key = apiKey
                    // остаток плана после уже выполненных подтверждений — чтобы модель не повторяла шаги
                    val remainder = PlanFlow.remainder(planQ0, planDone)
                    val nextHint = if (remainder.isNotBlank())
                        " ОСТАВШИЙСЯ ПЛАН (выполни строго это, не повторяй уже сделанное): «$remainder»."
                    else ""
                    // финальный шаг «открой приложение X» — исполняем напрямую, модель не нужна
                    val finalPart = remainder.trim().removePrefix("->").trim()
                    val appMatch = Regex("(?i)^открой\\s+(?:приложение\\s+)?([\\p{L}0-9][\\p{L}0-9 .-]{0,40}?)\\s*$").find(finalPart)
                    val directApp = appMatch?.groupValues?.get(1)?.trim()
                    val directFinal = plan && planDone >= PlanFlow.confirms(planQ0) &&
                        directApp != null && !finalPart.contains("fetch_url") &&
                        !Regex("(?i)read_file|write_file|find_files|web_search").containsMatchIn(finalPart)
                    DebugLog.log("plan-resume", "directFinal=$directFinal finalPart=$finalPart")
                    // после ответа модели на этот сегмент: если подтверждения ещё остались — плашку ставим САМИ
                    val needConfirm = plan && planDone < PlanFlow.confirms(planQ0)
                    val suppliedInput = if (m.planInputText.isNotBlank()) m.planInputText
                        else if (m.planInput && PlanFlow.isQuestionStep(PlanFlow.nextStep(planQ0, planDone))) userText else ""
                    val questionHint = if (suppliedInput.isBlank() && PlanFlow.isQuestionStep(PlanFlow.nextStep(planQ0, planDone)))
                        " Сейчас верни action=chat с текстом вопроса пользователю (без инструментов и без ask_confirm)." else ""
                    val inputHint = if (suppliedInput.isNotBlank())
                        " ИНСТРУКЦИИ ПОЛЬЗОВАТЕЛЯ ДЛЯ ЭТАПА: «$suppliedInput». Выполни их в этом шаге." else ""
                    lifecycleScope.launch {
                        if (directFinal) {
                            // финальный шаг плана — простое действие: выполняем сами
                            val res = runCatching {
                                execTerminal(IntendedAction(action = "open_app", app = directApp,
                                    title = null, whenStr = null, address = null, reply = null))
                            }.getOrDefault("Не удалось открыть: $directApp")
                            planTools += "open_app"
                            val cur = messages.getOrNull(newIdx)
                            if (cur != null) {
                                val ok = res.startsWith("Открыл:")
                                messages[newIdx] = cur.copy(
                                    text = if (ok) "Готово" else "Готово\n" + res,
                                    toolsChain = listOf("open_app")
                                )
                            }
                            if (m.bubbleUid != 0L) {
                                upsertTask(m.bubbleUid, m.bubblePrompt.ifBlank { "план" }.take(26), newIdx, 1, 1, done = true, running = false, prompt = m.bubblePrompt)
                                bubbleSummary(m.bubbleUid, newIdx, m.bubblePrompt)
                            }
                            persist()
                            scrollSignal++
                            scrollMsg = newIdx
                            return@launch
                        }
                        if (plan && suppliedInput.isBlank() && questionHint.isNotBlank()) {
                            val step = PlanFlow.nextStep(planQ0, planDone)
                            val question = planQuestionText(planQ0, step, key, apiUrl, client)
                            messages[newIdx] = messages[newIdx].copy(
                                text = "Готово\n" + question,
                                resumeMsgs = msgs2?.toString(), resumePlan = true, resumePlanQ = planQ0,
                                resumePlanDone = planDone, bubbleUid = m.bubbleUid, bubblePrompt = m.bubblePrompt,
                                planInput = true)
                            persist()
                            scrollSignal++
                            return@launch
                        }
                        msgs2!!.put(org.json.JSONObject().put("role", "user")
                            .put("content",
                                "СНАЧАЛА оцени ответ пользователя: подтверждение это или нет. Пользователь написал: «$userText». Если это подтверждение — продолжай выполнение плана; " +
                                "если отмена или другой запрос — отвечай по существу (при отмене останови план и сообщи об этом)." +
                                " ЕСЛИ сводка/итог предыдущего шага ещё не была показана пользователю — верни её СЕЙЧАС ПОЛНОСТЬЮ в reply.$nextHint$questionHint$inputHint"))
                        val used = mutableSetOf<String>()
                        var liveStatus = "Думаю"
                        var deltaStarted = false
                        var curIdx2 = newIdx
                        var pendingTool2: String? = null
                        val out = runCatching {
                            resumeLoop(
                                msgs2!!, userText, key, plan = plan,
                                planQ0 = planQ0, planDone = planDone,
                                planActionStart = planDone * 2 + if (suppliedInput.isNotBlank() && PlanFlow.isQuestionStep(PlanFlow.nextStep(planQ0, planDone))) 1 else 0,
                                onStatus = { st ->
                                    liveStatus = st
                                    val cur = messages.getOrNull(curIdx2) ?: return@resumeLoop
                                    if (cur.text.isBlank()) {
                                        messages[curIdx2] = cur.copy(text = st)
                                        streamTick++
                                    }
                                },
                                onDelta = { d ->
                                    val cur = messages.getOrNull(curIdx2) ?: return@resumeLoop
                                    if (!deltaStarted) {
                                        deltaStarted = true
                                        messages[curIdx2] = cur.copy(text = d)
                                    } else {
                                        messages[curIdx2] = cur.copy(text = cur.text + d)
                                    }
                                    streamTick++
                                },
                                onFiles = { }, onTools = { used += it; planTools += it }, onEvents = { },
                                onTool = { t ->
                                    used += t
                                    planTools += t
                                    if (t != "chat" && t != "ask_confirm") {
                                        val prev = pendingTool2
                                        if (prev != null) {
                                            messages[curIdx2] = messages[curIdx2].copy(text = "Готово", toolsChain = listOf(prev))
                                            messages += Msg(false, "Думаю")
                                            curIdx2 = messages.lastIndex
                                        }
                                        pendingTool2 = t
                                        streamTick++
                                    }
                                }
                            )
                        }.getOrDefault("Ошибка: не удалось продолжить")
                        val lastTool = listOfNotNull(pendingTool2)
                        val cur = messages.getOrNull(curIdx2)
                        if (cur != null) {
                            if (out.contains("\u0000CONFIRM::") || out.startsWith("CONFIRM::")) {
                                // вложенное подтверждение: новая плашка следом
                                val (stepText, confirmText) = splitConfirmReply(out)
                                DebugLog.log("plan-step", "out=${out.take(100)} step=${stepText.take(80)}")
                                val openOnly = used.contains("open_app") && used.all {
                                    it == "open_app" || it == "chat" || it == "ask_confirm"
                                }
                                if (openOnly) {
                                    messages[curIdx2] = cur.copy(text = "Готово", toolsChain = lastTool)
                                } else if (stepText.isNotBlank()) {
                                    messages[curIdx2] = cur.copy(text = "Готово\n" + stepText, toolsChain = lastTool)
                                } else if (lastTool.isNotEmpty()) {
                                    messages[curIdx2] = cur.copy(text = "Готово", toolsChain = lastTool)
                                } else if (cur.text.isBlank()) {
                                    messages.removeAt(curIdx2)
                                }
                                val questionInput = plan && suppliedInput.isBlank() && PlanFlow.isQuestionStep(PlanFlow.nextStep(planQ0, planDone))
                                val acText = if (questionInput) {
                                    "Готово\n**Следующий шаг плана:** ${PlanFlow.followingStep(planQ0, planDone)}\n**Подтверди**"
                                } else if (plan) PlanFlow.forcedConfirmText(planQ0, planDone + 1)
                                else confirmText.ifBlank { "Подтверди продолжение" }
                                messages += Msg(
                                    false, "",
                                    askConfirm = acText,
                                    resumeMsgs = confirmMsgs?.toString(),
                                    resumePlan = plan,
                                    resumePlanQ = planQ0,
                                    resumePlanDone = if (questionInput) planDone else planDone + 1,
                                    bubbleUid = m.bubbleUid,
                                    bubblePrompt = m.bubblePrompt,
                                    planInput = questionInput || (plan && PlanFlow.isUserInputStep(PlanFlow.nextStep(planQ0, planDone + 1)))
                                )
                            } else if (needConfirm) {
                                // сегмент завершён ответом chat, подтверждения ещё остались — реплика следующего шага
                                if (cur != null && out.isNotBlank()) {
                                    messages[curIdx2] = cur.copy(text = "Готово\n" + out, toolsChain = lastTool)
                                }
                                val questionInput = plan && suppliedInput.isBlank() && PlanFlow.isQuestionStep(PlanFlow.nextStep(planQ0, planDone))
                                val acText = if (questionInput) {
                                    "Готово\n**Следующий шаг плана:** ${PlanFlow.followingStep(planQ0, planDone)}\n**Подтверди**"
                                } else PlanFlow.forcedConfirmText(planQ0, planDone + 1)
                                messages += Msg(
                                    false, "",
                                    askConfirm = acText,
                                    resumeMsgs = confirmMsgs?.toString(),
                                    resumePlan = plan,
                                    resumePlanQ = planQ0,
                                    resumePlanDone = if (questionInput) planDone else planDone + 1,
                                    bubbleUid = m.bubbleUid,
                                    bubblePrompt = m.bubblePrompt,
                                    planInput = questionInput || (plan && PlanFlow.isUserInputStep(PlanFlow.nextStep(planQ0, planDone + 1)))
                                )
                            } else {
                                val allTools = planTools.filter { it != "chat" && it != "ask_confirm" }
                                    .fold(mutableListOf<String>()) { acc, t -> if (acc.lastOrNull() != t) acc.add(t); acc }
                                messages[curIdx2] = cur.copy(text = "Готово\n" + out, toolsChain = lastTool)
                                // план завершён: бабл «выполнено» + саммари
                                if (m.bubbleUid != 0L) {
                                    upsertTask(m.bubbleUid, m.bubblePrompt.ifBlank { "план" }.take(26), curIdx2, 1, 1, done = true, running = false, prompt = m.bubblePrompt)
                                    bubbleSummary(m.bubbleUid, curIdx2, m.bubblePrompt)
                                }
                            }
                        }
                        persist()
                        scrollSignal++
                        scrollMsg = mi
                    }
                }

                /** Продолжение плана внутри ветки пуша после подтверждения текстом. */
                fun resumeThreadConfirm(ev: EventInfo, key: String, m: Msg, userText: String) {
                    val tList = threads[key].orEmpty()
                    val mi = tList.indexOfFirst { it === m }
                    if (mi < 0) return
                    // плашка остаётся с «Ты: …»; ответ печатается новым сообщением ветки
                    val updated = tList.toMutableList()
                    updated[mi] = m.copy(resumeMsgs = null, text = "Ты: $userText")
                    updated += Msg(false, "")
                    val newIdx = updated.lastIndex
                    threads = threads + (key to updated)
                    saveThread(prefs, key, updated)
                    scrollThreadSignal++
                    var msgs2 = runCatching { org.json.JSONArray(m.resumeMsgs) }.getOrNull()
                    val plan = m.resumePlan
                    val planQ0 = m.resumePlanQ ?: ""
                    val planDone = m.resumePlanDone
                    DebugLog.log("plan-resume", "msgs2=${msgs2 != null} plan=$plan done=$planDone")
                    if (m.planInput) {
                        if (m.askConfirm != null && PlanFlow.isConfirmation(userText)) {
                            // Подтверждение только включает режим ввода: модель не вызываем.
                            messages[mi] = m.copy(resumeMsgs = m.resumeMsgs)
                            messages[newIdx] = messages[newIdx].copy(text = "Готово")
                            persist()
                            scrollSignal++
                            return
                        }
                        if (!PlanFlow.isQuestionStep(PlanFlow.nextStep(planQ0, planDone))) {
                            // Явное «дождись…»: после ввода показываем подтверждение следующего шага.
                            messages.removeAt(newIdx)
                            messages += Msg(false, "",
                                askConfirm = PlanFlow.forcedConfirmText(planQ0, planDone + 1),
                                resumeMsgs = m.resumeMsgs,
                                resumePlan = plan,
                                resumePlanQ = planQ0,
                                resumePlanDone = planDone + 1,
                                bubbleUid = m.bubbleUid,
                                bubblePrompt = m.bubblePrompt,
                                planInputText = userText)
                            persist()
                            scrollSignal++
                            return
                        }
                        // После вопроса ответ — данные текущего сегмента: выполняем его сразу,
                        // без перехода к следующему подтверждению и без пропуска действий.
                        messages[mi] = m.copy(resumeMsgs = m.resumeMsgs, planInputText = userText)
                    }
                    if (msgs2 == null) {
                        val l2 = (threads[key] ?: updated).toMutableList()
                        if (l2.getOrNull(newIdx) != null) {
                            l2[newIdx] = l2[newIdx].copy(text = "Не удалось продолжить — состояние не сохранилось.")
                            threads = threads + (key to l2)
                            saveThread(prefs, key, l2)
                            scrollThreadSignal++
                        }
                        return
                    }
                    val apiK = apiKey
                    val remainder = PlanFlow.remainder(planQ0, planDone)
                    val nextHint = if (remainder.isNotBlank())
                        " ОСТАВШИЙСЯ ПЛАН (выполни строго это, не повторяй уже сделанное): «$remainder»."
                    else ""
                    val finalPart = remainder.trim().removePrefix("->").trim()
                    val appMatch = Regex("(?i)^открой\\s+(?:приложение\\s+)?([\\p{L}0-9][\\p{L}0-9 .-]{0,40}?)\\s*$").find(finalPart)
                    val directApp = appMatch?.groupValues?.get(1)?.trim()
                    val directFinal = plan && planDone >= PlanFlow.confirms(planQ0) &&
                        directApp != null && !finalPart.contains("fetch_url") &&
                        !Regex("(?i)read_file|write_file|find_files|web_search").containsMatchIn(finalPart)
                    DebugLog.log("plan-resume", "directFinal=$directFinal finalPart=$finalPart")
                    val needConfirm = plan && planDone < PlanFlow.confirms(planQ0)
                    val suppliedInput = if (m.planInputText.isNotBlank()) m.planInputText
                        else if (m.planInput && PlanFlow.isQuestionStep(PlanFlow.nextStep(planQ0, planDone))) userText else ""
                    val questionHint = if (suppliedInput.isBlank() && PlanFlow.isQuestionStep(PlanFlow.nextStep(planQ0, planDone)))
                        " Сейчас верни action=chat с текстом вопроса пользователю (без инструментов и без ask_confirm)." else ""
                    val inputHint = if (suppliedInput.isNotBlank())
                        " ИНСТРУКЦИИ ПОЛЬЗОВАТЕЛЯ ДЛЯ ЭТАПА: «$suppliedInput». Выполни их в этом шаге." else ""
                    lifecycleScope.launch {
                        if (directFinal) {
                            val res = runCatching {
                                execTerminal(IntendedAction(action = "open_app", app = directApp,
                                    title = null, whenStr = null, address = null, reply = null))
                            }.getOrDefault("Не удалось открыть: $directApp")
                            planTools += "open_app"
                            val l2 = (threads[key] ?: updated).toMutableList()
                            if (l2.getOrNull(newIdx) != null) {
                                val ok = res.startsWith("Открыл:")
                                l2[newIdx] = l2[newIdx].copy(
                                    text = if (ok) "Готово" else "Готово\n" + res,
                                    toolsChain = listOf("open_app")
                                )
                            }
                            threads = threads + (key to l2)
                            saveThread(prefs, key, l2)
                            if (m.bubbleUid != 0L) {
                                upsertTask(m.bubbleUid, m.bubblePrompt.ifBlank { "план" }.take(26), 0, 1, 1, done = true, running = false, prompt = m.bubblePrompt)
                                bubbleSummary(m.bubbleUid, 0, m.bubblePrompt)
                            }
                            scrollThread = key
                            scrollThreadSignal++
                            return@launch
                        }
                        if (plan && suppliedInput.isBlank() && questionHint.isNotBlank()) {
                            val step = PlanFlow.nextStep(planQ0, planDone)
                            val question = planQuestionText(planQ0, step, apiK, apiUrl, client)
                            val l2 = (threads[key] ?: updated).toMutableList()
                            if (l2.getOrNull(newIdx) != null) l2[newIdx] = l2[newIdx].copy(
                                text = "Готово\n" + question,
                                resumeMsgs = msgs2?.toString(), resumePlan = true, resumePlanQ = planQ0,
                                resumePlanDone = planDone, bubbleUid = m.bubbleUid, bubblePrompt = m.bubblePrompt,
                                planInput = true)
                            threads = threads + (key to l2)
                            saveThread(prefs, key, l2)
                            scrollThread = key
                            scrollThreadSignal++
                            return@launch
                        }
                        msgs2!!.put(org.json.JSONObject().put("role", "user")
                            .put("content",
                                "СНАЧАЛА оцени ответ пользователя: подтверждение это или нет. Пользователь написал: «$userText». Если это подтверждение — продолжай выполнение плана; " +
                                "если отмена или другой запрос — отвечай по существу (при отмене останови план и сообщи об этом)." +
                                " ЕСЛИ сводка/итог предыдущего шага ещё не была показана пользователю — верни её СЕЙЧАС ПОЛНОСТЬЮ в reply.$nextHint$questionHint$inputHint"))
                        val used = mutableSetOf<String>()
                        var tDeltaStarted = false
                        val out = runCatching {
                            resumeLoop(
                                msgs2!!, userText, apiK, plan = plan,
                                planQ0 = planQ0, planDone = planDone,
                                planActionStart = planDone * 2 + if (suppliedInput.isNotBlank() && PlanFlow.isQuestionStep(PlanFlow.nextStep(planQ0, planDone))) 1 else 0,
                                onStatus = { st ->
                                    val l2 = threads[key] ?: return@resumeLoop
                                    val ll = l2.toMutableList()
                                    if (ll.getOrNull(newIdx) != null && ll[newIdx].text.isBlank() && !tDeltaStarted) {
                                        ll[newIdx] = ll[newIdx].copy(text = st)
                                        threads = threads + (key to ll)
                                        streamTick++
                                    }
                                },
                                onDelta = { d ->
                                    val l2 = threads[key] ?: return@resumeLoop
                                    val ll = l2.toMutableList()
                                    if (ll.getOrNull(newIdx) != null) {
                                        if (!tDeltaStarted) {
                                            tDeltaStarted = true
                                            ll[newIdx] = ll[newIdx].copy(text = d)
                                        } else {
                                            ll[newIdx] = ll[newIdx].copy(text = ll[newIdx].text + d)
                                        }
                                        threads = threads + (key to ll)
                                        streamTick++
                                    }
                                },
                                onFiles = { }, onTools = { used += it; planTools += it }, onEvents = { },
                                onTool = { t ->
                                    used += t
                                    planTools += t
                                }
                            )
                        }.getOrDefault("Ошибка: не удалось продолжить")
                        val l3 = (threads[key] ?: updated).toMutableList()
                        val cur = l3.getOrNull(newIdx)
                        if (cur != null) {
                            if (out.startsWith("CONFIRM::")) {
                                val confirmText = out.removePrefix("CONFIRM::").trim()
                                if (cur.text.isBlank() || cur.text == "Думаю") {
                                    val idxF = newIdx
                                    lifecycleScope.launch {
                                        val sum = runCatching {
                                            val sys = "Ты выполнял шаг многошагового плана пользователя. Дай ПОЛНЫЙ итог выполненного шага для пользователя (как твой reply):\nПЛАН: «$planQ0»\nОСТАЛОСЬ ПОСЛЕ ШАГА: «${PlanFlow.remainder(planQ0, planDone + 1)}»\nВерни только текст итога, без JSON и кавычек."
                                            ModelApi.raw(sys, "итог шага", apiK, apiUrl, client).trim()
                                        }.getOrNull()?.takeIf { it.isNotBlank() }
                                        if (!sum.isNullOrBlank()) {
                                            val l = (threads[key] ?: emptyList()).toMutableList()
                                            if (l.getOrNull(idxF) != null) {
                                                l[idxF] = l[idxF].copy(text = sum)
                                                threads = threads + (key to l)
                                                saveThread(prefs, key, l)
                                                scrollThreadSignal++
                                            }
                                        }
                                    }
                                }
                                l3 += Msg(false, "",
                                    askConfirm = confirmText.ifBlank { "Подтверди продолжение" },
                                    resumeMsgs = confirmMsgs?.toString(),
                                    resumePlan = plan,
                                    resumePlanQ = planQ0,
                                    resumePlanDone = planDone + 1,
                                    bubbleUid = m.bubbleUid,
                                    bubblePrompt = m.bubblePrompt,
                                    planInput = plan && PlanFlow.isUserInputStep(PlanFlow.nextStep(planQ0, planDone + 1)))
                            } else if (needConfirm) {
                                l3 += Msg(false, "",
                                    askConfirm = PlanFlow.forcedConfirmText(planQ0, planDone + 1),
                                    resumeMsgs = confirmMsgs?.toString(),
                                    resumePlan = plan,
                                    resumePlanQ = planQ0,
                                    resumePlanDone = planDone + 1,
                                    bubbleUid = m.bubbleUid,
                                    bubblePrompt = m.bubblePrompt,
                                    planInput = plan && PlanFlow.isUserInputStep(PlanFlow.nextStep(planQ0, planDone + 1)))
                            } else {
                                val lastTool = used.filter { it != "chat" && it != "ask_confirm" }.lastOrNull()
                                l3[newIdx] = cur.copy(text = cur.text + "\n\n" + out, toolsChain = listOfNotNull(lastTool))
                                if (m.bubbleUid != 0L) {
                                    upsertTask(m.bubbleUid, m.bubblePrompt.ifBlank { "план" }.take(26), 0, 1, 1, done = true, running = false, prompt = m.bubblePrompt)
                                    bubbleSummary(m.bubbleUid, 0, m.bubblePrompt)
                                }
                            }
                            threads = threads + (key to l3)
                            saveThread(prefs, key, l3)
                        }
                        scrollThread = key
                        scrollThreadSignal++
                    }
                }

                /** Отправка в прямой чат: локальные команды, оповещения, планы и инструменты. */
                fun dispatchMessage(q0: String) {
                    DebugLog.log("plan-capture", "enter: $q0")
                    val q = q0.trim()
                    if (q.isEmpty()) return

                    // Текстовое подтверждение продолжает сохранённый план.
                    val confirmMsg = messages.lastOrNull { (it.askConfirm != null || it.planInput) && it.resumeMsgs != null }
                    DebugLog.log("plan-capture", "q=$q confirm=${confirmMsg != null} lastAsk=${messages.lastOrNull()?.askConfirm != null}")
                    if (confirmMsg != null) {
                        input = ""
                        lastUser = q
                        resumeConfirm(confirmMsg, q)
                        return
                    }

                    input = ""
                    lastUser = q
                    val hist = messages.toList()
                    messages += Msg(true, q)
                    persist()
                    scrollSignal++
                    rememberText("user", q, "main")
                    messages += Msg(false, "")
                    val idx = messages.lastIndex
                    val key = apiKey
                    val mem = MemoryStore.buildMemoryBlock(memoryState, threads)
                    val runUid = System.nanoTime()
                    val runLabel = q.take(26)
                    var bubbleDone = false
                    upsertTask(runUid, runLabel, idx, 1, 1, done = false, running = true, prompt = q)
                    bubbleDone = true
                    activeTasks = activeTasks + ActiveTask(idx, "Думаю")
                    scrollMsg = idx
                    lifecycleScope.launch {
                        isStreaming = true
                        try {
                            var replyFiles: List<FileRef> = emptyList()
                            var usedTools: Set<String> = emptySet()
                            var replyEvents: List<EventInfo> = emptyList()
                            var lastStatus = "Думаю"
                            var replySoFar = ""
                            var curIdx = idx
                            var pendingTool: String? = null
                            fun paintLine() {
                                val body = if (replySoFar.isBlank()) lastStatus else replySoFar
                                messages[curIdx] = Msg(false, body)
                                streamTick++
                            }
                            val reply = router(
                                q, key, hist, mem, "",
                                initialStatus = "Думаю",
                                onStatus = { st ->
                                    lastStatus = st
                                    activeTasks = activeTasks.map { if (it.id == idx) it.copy(label = st) else it }
                                    paintLine()
                                },
                                onDelta = { d ->
                                    replySoFar += d
                                    paintLine()
                                },
                                onFiles = { replyFiles = it },
                                onTools = { usedTools = it; planTools += it; paintLine() },
                                onEvents = { replyEvents = it },
                                onTool = { t ->
                                    usedTools = usedTools + t
                                    planTools += t
                                    if (t != "chat" && t != "ask_confirm") {
                                        val prev = pendingTool
                                        if (prev != null) {
                                            messages[curIdx] = messages[curIdx].copy(text = "Готово", toolsChain = listOf(prev))
                                            messages += Msg(false, "Думаю")
                                            curIdx = messages.lastIndex
                                        }
                                        pendingTool = t
                                        streamTick++
                                    }
                                }
                            )
                            if (reply.contains("\u0000CONFIRM::") || reply.startsWith("CONFIRM::")) {
                                val (stepText, _) = splitConfirmReply(reply)
                                val openOnly = usedTools.contains("open_app") && usedTools.all {
                                    it == "open_app" || it == "chat" || it == "ask_confirm"
                                }
                                when {
                                    // Простое открытие приложения — только «Готово» и его плашка, без лишнего текста.
                                    openOnly -> {
                                        messages[curIdx] = messages[curIdx].copy(text = "Готово", toolsChain = listOfNotNull(pendingTool))
                                    }
                                    stepText.isNotBlank() -> {
                                        messages[curIdx] = messages[curIdx].copy(text = "Готово\n" + stepText, toolsChain = listOfNotNull(pendingTool))
                                    }
                                    pendingTool != null -> {
                                        messages[curIdx] = messages[curIdx].copy(text = "Готово", toolsChain = listOfNotNull(pendingTool))
                                    }
                                }
                                if (usedTools.any { it != "chat" && it != "ask_confirm" }) {
                                    upsertTask(runUid, runLabel, idx, 1, 1, done = false, running = true, prompt = q)
                                    bubbleDone = true
                                }
                                // Подтверждение — отдельная реплика после инструмента/сводки, ничего не затираем.
                                val acText = if (planMode) PlanFlow.forcedConfirmText(planQ, planConfirmDone)
                                    else "Готово\n**Подтверди**"
                                messages += Msg(false, "", askConfirm = acText,
                                    resumeMsgs = confirmMsgs?.toString(), resumePlan = planMode,
                                    resumePlanQ = planQ, resumePlanDone = planConfirmDone,
                                    bubbleUid = runUid, bubblePrompt = q,
                                    planInput = planMode && PlanFlow.isUserInputStep(PlanFlow.nextStep(planQ, planConfirmDone)))
                                persist()
                                scrollSignal++
                                activeTasks = activeTasks.filter { it.id != idx }
                                scrollMsg = messages.lastIndex
                                return@launch
                            }
                            if (usedTools.any { it != "chat" && it != "ask_confirm" }) {
                                upsertTask(runUid, runLabel, idx, 1, 1, done = true, running = false, prompt = q)
                                bubbleDone = true
                                lifecycleScope.launch {
                                    val summary = runCatching {
                                        val sys = "Придумай очень короткое название (2-5 слов, без кавычек, без точки в конце) для действия, которое ассистент выполнил по запросу пользователя: «$q»"
                                        ModelApi.raw(sys, q, apiKey, apiUrl, client).trim().take(40)
                                    }.getOrNull()?.takeIf { it.isNotBlank() }
                                    if (!summary.isNullOrBlank()) {
                                        upsertTask(runUid, summary, idx, 1, 1, done = true, running = false, prompt = q)
                                    }
                                }
                            } else {
                                removeTask(runUid)
                                bubbleDone = false
                            }
                            val cardEvents = if (usedTools.any { it in EVENT_LIST_ACTIONS }) replyEvents else emptyList()
                            messages[curIdx] = Msg(false, "Готово\n" + reply, files = replyFiles, events = cardEvents, toolsChain = listOfNotNull(pendingTool))
                            persist()
                            rememberText("agent", reply, "main")
                            alerts = AlertStore.load(prefs)
                            watchDisabledIds = WatchEngine.disabledFromJson(prefs.getString(WatchEngine.KEY_DISABLED, null))
                            val t = activeTasks.firstOrNull { it.id == idx }
                            activeTasks = activeTasks.filter { it.id != idx }
                            if (t != null) finishedTasks = finishedTasks + ActiveTask(idx, taskFinishLabel(t.label))
                            scrollMsg = idx
                        } catch (e: Exception) {
                            messages[idx] = Msg(false, "Ошибка: ${e.message ?: e.javaClass.simpleName}")
                            if (bubbleDone) upsertTask(runUid, runLabel, idx, 1, 1, done = true, running = false, prompt = q)
                            activeTasks = activeTasks.filter { it.id != idx }
                        } finally {
                            isStreaming = false
                            scrollSignal++
                        }
                    }
                }

                val send: (String) -> Unit = { dispatchMessage(it) }

                // e2e (DEBUG): текст из intent — отправляем как сообщение пользователя
                var e2eText by pendingE2e
                LaunchedEffect(Unit) {
                    val b64 = intent.getStringExtra("e2e_b64")
                    if (b64 != null && BuildConfig.DEBUG) {
                        intent.removeExtra("e2e_b64")
                        val text = runCatching {
                            String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                        }.getOrDefault("")
                        if (text.isNotBlank()) e2eText = text
                    }
                }
                LaunchedEffect(e2eText) {
                    val text = e2eText ?: return@LaunchedEffect
                    DebugLog.log("e2e", "dispatch: $text")
                    delay(800)
                    dispatchMessage(text)
                    e2eText = null
                }
                // e2e (DEBUG): симуляция сработавшего оповещения (проверка read_pushes)
                LaunchedEffect(Unit) {
                    if (intent.getStringExtra("e2e_fire_alert") != null && BuildConfig.DEBUG) {
                        intent.removeExtra("e2e_fire_alert")
                        delay(1200)
                        val alert = alerts.firstOrNull()
                        if (alert != null) {
                            WatchEngine.deliver(this@MainActivity, alert, "Письмо с вложением и просьбой отредактировать документ", "открой письмо и посмотри документ")
                        }
                    }
                }
                // e2e (DEBUG): прямой запуск движка алерта с полным текстом события
                var e2eWatch by pendingWatch
                LaunchedEffect(e2eWatch) {
                    val raw = e2eWatch ?: return@LaunchedEffect
                    DebugLog.log("e2e-watch", "запуск: $raw")
                    val parts = raw.split('|', limit = 3)
                    if (parts.size == 3) {
                        val event = WatchEvent(parts[0], parts[1], parts[2], System.currentTimeMillis())
                        DebugLog.log("e2e-watch", "алертов=${alerts.size}, key=${apiKey.take(4)}, state=${alerts.map { WatchEngine.usesFileState(it) }}")
                        val fired = WatchEngine.evaluateForEvent(prefs, client, alerts, event)
                        DebugLog.log("e2e-watch", "сработало=${fired.size}")
                        fired.forEach { (alert, text, prompt) ->
                            WatchEngine.deliver(this@MainActivity, alert, text, prompt)
                        }
                        scrollSignal++
                    }
                    e2eWatch = null
                }
                // e2e (DEBUG): произвольный входящий пуш для проверки «Обработать»
                LaunchedEffect(Unit) {
                    val b64 = intent.getStringExtra("e2e_push_b64")
                    if (b64 != null && BuildConfig.DEBUG) {
                        intent.removeExtra("e2e_push_b64")
                        delay(1200)
                        val text = runCatching {
                            String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                        }.getOrDefault("")
                        if (text.isNotBlank()) {
                            messages += Msg(false, "", EventInfo("push", "Тест", "", text, System.currentTimeMillis()))
                            persist()
                            scrollSignal++
                        }
                    }
                }
                // e2e (DEBUG): план внутри ветки тестового пуша
                LaunchedEffect(Unit) {
                    val b64 = intent.getStringExtra("e2e_thread_b64")
                    if (b64 != null && BuildConfig.DEBUG) {
                        intent.removeExtra("e2e_thread_b64")
                        delay(1500)
                        val text = runCatching {
                            String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                        }.getOrDefault("")
                        if (text.isNotBlank()) {
                            val ev = EventInfo("push", "Тест", "", "тестовый пуш для e2e", System.currentTimeMillis())
                            messages += Msg(false, "", ev)
                            persist()
                            val k = eventKey(ev)
                            activeKey = k
                            expandedKeys.clear()
                            expandedKeys.add(k)
                            threadSend(ev, text, "", "Думаю")
                        }
                    }
                }

                fun toggleEvent(key: String) {
                    val confirmPending = messages.any { (it.askConfirm != null || it.planInput) && it.resumeMsgs != null } ||
                        threads.values.any { l -> l.any { (it.askConfirm != null || it.planInput) && it.resumeMsgs != null } }
                    if (confirmPending) return // ввод привязан к плану — карточки не переключают цель
                    if (expandedKeys.contains(key)) {
                        // сворачиваем: снимаем и раскрытие, и подсветку
                        expandedKeys.remove(key)
                        if (activeKey == key) activeKey = null
                    } else {
                        // раскрываем только эту: остальные закрываются, подсветка переходит на неё
                        expandOnly(key)
                    }
                }

                // ─── действия карточек: пуш «Ответить» и карточки Диска ───
                fun openDiskDirCard(ref: FileRef) {
                    val acc = diskAccounts().firstOrNull { it.user == ref.diskAcc } ?: return
                    val path = ref.diskPath ?: return
                    // листинг заменяет сообщение с карточкой на месте — без «случайного места» в ленте
                    val idx = messages.indexOfFirst { m ->
                        m.files.any { it.diskPath == path && it.path == path }
                    }
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { DiskStore.list(client, acc, path) }
                        }
                        result.fold(
                            onSuccess = { items ->
                                val refs = mutableListOf<FileRef>()
                                // «..» — вернуться в родительскую папку
                                if (path != "/") {
                                    val parent = path.substringBeforeLast('/').ifBlank { "/" }
                                    refs += FileRef("..", parent, "dir", 0L, true, parent, acc.user)
                                }
                                refs += items.map {
                                    FileRef(it.name, it.path, if (it.isDir) "dir" else FileStore.kindOf(it.name),
                                        it.size, it.isDir, it.path, acc.user)
                                }
                                val header = "Папка ${YandexDisk.folderDisplay(path)} — ${items.size} объектов"
                                if (idx >= 0) messages[idx] = Msg(false, header, files = refs)
                                else messages += Msg(false, header, files = refs)
                            },
                            onFailure = {
                                val err = "Яндекс Диск: ${YandexDisk.describeWebdavError(it)}"
                                if (idx >= 0) messages[idx] = Msg(false, err)
                                else messages += Msg(false, err)
                            }
                        )
                        persist()
                        scrollSignal++
                    }
                }

                suspend fun diskSnippetFor(ref: FileRef): String? {
                    val acc = diskAccounts().firstOrNull { it.user == ref.diskAcc } ?: return null
                    val path = ref.diskPath ?: return null
                    return runCatching {
                        val meta = DiskStore.meta(client, acc, path)
                        if (meta.isDir || meta.size > YandexDisk.MAX_CHAT_READ) return@runCatching null
                        val f = File(cacheDir, "disk_card/" + meta.name)
                        DiskStore.download(client, acc, path, f)
                        FileStore.readText(f.absolutePath, 500_000).ifBlank { null }
                    }.getOrNull()
                }

                /** Локальный путь файла с Диска в кэше — для «офисного вида» карточек. */
                suspend fun diskFileFor(ref: FileRef): String? {
                    val acc = diskAccounts().firstOrNull { it.user == ref.diskAcc } ?: return null
                    val path = ref.diskPath ?: return null
                    return runCatching {
                        val meta = DiskStore.meta(client, acc, path)
                        if (meta.isDir || meta.size > YandexDisk.MAX_CHAT_READ) return@runCatching null
                        val f = File(cacheDir, "disk_card/" + meta.name)
                        DiskStore.download(client, acc, path, f)
                        f.absolutePath
                    }.getOrNull()
                }

                suspend fun diskPreviewFor(ref: FileRef): ImageBitmap? {
                    val acc = diskAccounts().firstOrNull { it.user == ref.diskAcc } ?: return null
                    val path = ref.diskPath ?: return null
                    return runCatching {
                        val meta = DiskStore.meta(client, acc, path)
                        if (meta.isDir || meta.size > YandexDisk.MAX_CHAT_READ) return@runCatching null
                        val f = File(cacheDir, "disk_card/" + meta.name)
                        DiskStore.download(client, acc, path, f)
                        decodePreview(f.absolutePath)
                    }.getOrNull()
                }

                suspend fun diskDownloadFor(ref: FileRef): String? {
                    val acc = diskAccounts().firstOrNull { it.user == ref.diskAcc } ?: return null
                    val path = ref.diskPath ?: return null
                    return runCatching {
                        val meta = DiskStore.meta(client, acc, path)
                        if (meta.isDir) return@runCatching null
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        dir.mkdirs()
                        val dest = File(dir, meta.name)
                        DiskStore.download(client, acc, path, dest)
                        dest.absolutePath
                    }.getOrNull()
                }

                fun replaceDiskCard(ref: FileRef, localPath: String) {
                    val f = File(localPath)
                    val local = FileRef(f.name, f.absolutePath, FileStore.kindOf(f.name), f.length())
                    for (i in messages.indices) {
                        val m = messages[i]
                        if (m.files.any { it.diskPath == ref.diskPath && it.path == ref.path }) {
                            messages[i] = m.copy(
                                files = m.files.map {
                                    if (it.diskPath == ref.diskPath && it.path == ref.path) local else it
                                }
                            )
                        }
                    }
                    persist()
                }

                val cardActions = CardActions(
                    onReply = { e ->
                        if (e.kind == "sms") openApp("Сообщения") else PushReply.open(e, ctx)
                    },
                    openDiskDir = { ref -> openDiskDirCard(ref) },
                    diskListDir = { ref ->
                        withContext(Dispatchers.IO) {
                            val acc = diskAccounts().firstOrNull { it.user == ref.diskAcc }
                            val path = ref.diskPath
                            if (acc == null || path == null) null
                            else runCatching { DiskStore.list(client, acc, path) }
                                .getOrNull()?.map {
                                    FileRef(it.name, it.path, if (it.isDir) "dir" else FileStore.kindOf(it.name),
                                        it.size, it.isDir, it.path, acc.user)
                                }
                        }
                    },
                    diskSnippet = { ref -> diskSnippetFor(ref) },
                    diskFile = { ref -> diskFileFor(ref) },
                    diskPreview = { ref -> diskPreviewFor(ref) },
                    diskDownload = { ref -> diskDownloadFor(ref) },
                    diskReplaced = { ref, localPath -> replaceDiskCard(ref, localPath) },
                    alertsList = { alerts },
                    toolChoices = { READ_TOOLS.map { it.id to it.desc } + TERMINAL_TOOLS + LOCAL_TOOLS },
                    updateAlert = { id, field, value -> saveAlertField(id, field, value) },
                    onWatchStart = { ev ->
                        // «Предлагаю: …» подставляет промпт в поле ввода — пользователь отправляет сам
                        input = (ev.note ?: "").trim()
                    },
                    onWatchDismiss = { ev ->
                        // снуз обсуждается в промпте настройки оповещения
                        DebugLog.log("watch-snooze", "кнопки больше нет: ${ev.source}")
                    },
                    deleteAlert = { id -> deleteAlertById(id) },
                    saveToolSetting = { key, value -> saveToolSetting(key, value) },
                    addToolAccount = { toolId, values -> addToolAccount(toolId, values) },
                    toggleWatch = { id ->
                        var d = WatchEngine.disabledFromJson(prefs.getString(WatchEngine.KEY_DISABLED, null))
                        d = if (id in d) d - id else d + id
                        prefs.edit().putString(WatchEngine.KEY_DISABLED, WatchEngine.disabledToJson(d)).apply()
                        watchDisabledIds = d
                    },
                    watchDisabled = { watchDisabledIds },
                    toolsData = {
                        ToolSettings.build(
                            READ_TOOLS.map { it.id to it.desc },
                            TERMINAL_TOOLS,
                            LOCAL_TOOLS,
                            prefs.getString(Prefs.KEY_SEARCH_PROVIDER, "ddg") ?: "ddg",
                            prefs.getString(Prefs.KEY_YANDEX_KEY, "") ?: "",
                            prefs.getString(Prefs.KEY_YANDEX_URL, YANDEX_SEARCH_URL) ?: YANDEX_SEARCH_URL,
                            prefs.getString(Prefs.KEY_GEO_KEY, "") ?: "",
                            prefs.getString(Prefs.KEY_GEO_URL, "https://geocode-maps.yandex.ru/v1/") ?: "https://geocode-maps.yandex.ru/v1/",
                            locationGranted,
                            calendarGranted,
                            allFilesAccess,
                            MailAccounts.fromJson(prefs.getString(Prefs.KEY_MAIL_ACCOUNTS, null)),
                            CalDavAccounts.fromJson(prefs.getString(Prefs.KEY_CALDAV_ACCOUNTS, null)),
                            DiskAccounts.fromJson(prefs.getString(Prefs.KEY_DISK_ACCOUNTS, null)),
                            prefs.getString(Prefs.KEY_DISK_TOKEN, "") ?: "",
                            tgChannels.joinToString("\n") { "@$it" }
                        )
                    }
                )

                val rows = buildRows(messages, threads, expandedKeys.toSet())
                if (showSettings) {
                    SettingsScreen(
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        onModelSave = { url, key ->
                            prefs.edit().putString(Prefs.KEY_BASE_URL, url).apply()
                            prefs.edit().putString(Prefs.KEY_API, key).apply()
                            apiUrl = url
                            apiKey = key
                        },
                        cardActions = cardActions,
                        rsshubUrl = prefs.getString(Prefs.KEY_RSSHUB_URL, "") ?: "",
                        corsUrl = prefs.getString(Prefs.KEY_CORS_LOL_URL, "") ?: "",
                        onRsshubChange = { v -> prefs.edit().putString(Prefs.KEY_RSSHUB_URL, v).apply() },
                        onCorsChange = { v -> prefs.edit().putString(Prefs.KEY_CORS_LOL_URL, v).apply() },
                        smsGranted = smsGranted,
                        readSmsGranted = readSmsGranted,
                        pushAccess = pushAccess,
                        allFilesAccess = allFilesAccess,
                        calendarGranted = calendarGranted,
                        onRequestCalendar = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) },
                        locationGranted = locationGranted,
                        onRequestLocation = {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onRequestSms = { smsPermLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                        notificationsGranted = notifGranted,
                        onRequestNotifications = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onRequestReadSms = { readSmsLauncher.launch(Manifest.permission.READ_SMS) },
                        onRequestAllFiles = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        },
                        onOpenPushSettings = {
                            pushSettingsLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onClose = { showSettings = false }
                    )
                } else {
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                // «прилипание к низу»: обновляется по фактическому завершении жеста пользователя.
                val stickToBottom = remember { mutableStateOf(true) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(DsBg)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(bottom = with(LocalDensity.current) {
                            if (stickToBottom.value) lastImeBottom.toDp() else 0.dp
                        })
                ) {
                    // Внизу — держим последние сообщения над панелью ввода; вверху — позицию не трогаем.
                    LaunchedEffect(lastImeBottom) {
                        if (lastImeBottom > 0 && stickToBottom.value && rows.isNotEmpty()) {
                            repeat(4) {
                                withFrameNanos { }
                                listState.scrollToItem(Int.MAX_VALUE)
                            }
                        }
                    }
                    // Отслеживаем ТОЛЬКО ручной drag: служебные scrollToItem не меняют режим.
                    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
                    var userDraggedOnce by remember { mutableStateOf(false) }
                    LaunchedEffect(isUserDragging) {
                        if (isUserDragging) {
                            userDraggedOnce = true
                        } else if (userDraggedOnce) {
                            val li = listState.layoutInfo
                            val last = li.visibleItemsInfo.lastOrNull()
                            stickToBottom.value = last == null || last.index >= li.totalItemsCount - 1
                        }
                    }
                    val atBottom by remember {
                        derivedStateOf {
                            val li = listState.layoutInfo
                            val last = li.visibleItemsInfo.lastOrNull()
                            last == null || last.index >= li.totalItemsCount - 1
                        }
                    }
                    // старт: после загрузки сообщений и первого кадра — в самый низ
                    LaunchedEffect(Unit) {
                        while (rows.isEmpty()) delay(60)
                        withFrameNanos { }
                        listState.scrollToItem(Int.MAX_VALUE)
                    }
                    // новый контент — вниз, если пользователь не отматывал вверх
                    LaunchedEffect(scrollSignal) {
                        if (scrollSignal > 0 && rows.isNotEmpty() && stickToBottom.value) {
                            withFrameNanos { }
                            listState.scrollToItem(Int.MAX_VALUE)
                        }
                    }
                    // тап по завершённой задаче — прокрутка к её результату
                    LaunchedEffect(scrollTarget) {
                        val target = scrollTarget ?: return@LaunchedEffect
                        if (target >= 0 && target < rows.size) {
                            listState.animateScrollToItem(target)
                        }
                        scrollTarget = null
                    }
                    // мгновенная докрутка при потоке токенов (стриминг)
                    // плавное следование за потоком токенов: прокручиваем только когда
                    // новый текст вышел за нижнюю кромку — короткой анимацией, без рывков
                    LaunchedEffect(streamTick) {
                        if (streamTick > 0) {
                            val info = listState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()
                            if (last != null && last.offset + last.size > info.viewportEndOffset - 8) {
                                listState.animateScrollToItem(Int.MAX_VALUE)
                            }
                        }
                    }
                    // после ответа в тред — показываем последнее сообщение ветки
                    LaunchedEffect(scrollThreadSignal) {
                        if (scrollThreadSignal > 0) {
                            val k = activeKey
                            if (k != null) {
                                delay(80) // даём карточке перерисоваться с новым сообщением
                                val idx = rows.indexOfFirst { it is Row.E && it.key == k }
                                if (idx >= 0) {
                                    if (idx >= rows.lastIndex - 1) {
                                        // ветка внизу — едем в самый конец, сообщение над полем ввода
                                        listState.animateScrollToItem(Int.MAX_VALUE)
                                    } else {
                                        // ветка в середине — подводим её карточку в видимую зону
                                        listState.animateScrollToItem(idx)
                                    }
                                }
                            }
                        }
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(tasksExpanded) {
                                    detectTapGestures(onTap = { if (tasksExpanded) tasksExpanded = false })
                                },
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {                            itemsIndexed(rows) { index, r ->
                                when (r) {
                                    is Row.M -> {
                                        // плашка подтверждения — обычная реплика Walter, без карточки
                                        val lastRow = rows.lastOrNull()
                                        val isLastAgent = index == rows.lastIndex &&
                                            (lastRow as? Row.M)?.msg?.let { !it.fromUser } == true
                                        val prevUser = rows.subList(0, index).asReversed()
                                            .firstOrNull { r -> (r as? Row.M)?.msg?.fromUser == true }
                                            ?.let { (it as Row.M).msg.text }
                                            ?.takeIf { it.isNotBlank() }
                                        MessageRow(
                                            m = if (r.msg.askConfirm != null)
                                                r.msg.copy(text = r.msg.askConfirm ?: "", askConfirm = null)
                                            else r.msg,
                                            canRegenerate = isLastAgent && lastUser.isNotEmpty(),
                                            canRepeat = r.msg.fromUser && r.msg.text.isNotBlank(),
                                            streaming = isStreaming && index == rows.lastIndex && !r.msg.fromUser,
                                            cardActions = cardActions,
                                            onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                                            onRepeat = { send(r.msg.text) },
                                            onRegenerate = {
                                                val last = messages.lastOrNull()
                                                if (last != null && !last.fromUser && last.event == null) {
                                                    messages.removeAt(messages.lastIndex)
                                                    persist()
                                                }
                                                activeKey = null // регенерация всегда в общий чат
                                                send(lastUser)
                                            }
                                        )
                                    }
                                    is Row.E -> EventBlock(
                                        event = r.event,
                                        expanded = r.expanded,
                                        thread = r.thread,
                                        highlighted = r.key == activeKey,
                                        streaming = isStreaming,
                                        cardActions = cardActions,
                                        onHeight = { h -> cardHeights[r.key] = h },
                                        suggestions = eventSuggestions[r.key].orEmpty(),
                                        processing = eventSuggestionLoading.contains(r.key),
                                        onProcess = { processEventSuggestion(r.event, r.key) },
                                        onSuggestion = { prompt -> input = prompt },
                                        onHeaderClick = {
                                            // только сворачивание/разворачивание — экран не перематываем
                                            toggleEvent(r.key)
                                        }
                                    )
                                }
                            }
                        }
                        // плавающий трекер долгих задач: активные со спиннером, завершённые — тап прокручивает к результату
                        TasksOverlay(
                            active = activeTasks,
                            finished = finishedTasks,
                            entries = taskHistory,
                            expanded = tasksExpanded,
                            onToggle = { tasksExpanded = !tasksExpanded },
                            onRepeat = { e ->
                                val q = e.prompt.ifBlank { e.name }
                                send(q)
                            },
                            onTaskTap = { e ->
                                val row = rows.indexOfFirst { it is Row.M && it.msg === messages.getOrNull(e.anchorMsg) }
                                if (row >= 0) scrollTarget = row
                            },
                            onFinishedTap = { t ->
                                val row = if (t.threadKey != null) {
                                    rows.indexOfFirst { it is Row.E && it.key == t.threadKey }.takeIf { it >= 0 }
                                } else {
                                    rows.indexOfFirst { it is Row.M && it.msg === messages.getOrNull(t.id) }.takeIf { it >= 0 }
                                }
                                if (row != null) scrollTarget = row
                            },
                            onActiveTap = { t ->
                                val row = if (t.threadKey != null) {
                                    rows.indexOfFirst { it is Row.E && it.key == t.threadKey }.takeIf { it >= 0 }
                                } else {
                                    rows.indexOfFirst { it is Row.M && it.msg === messages.getOrNull(t.id) }.takeIf { it >= 0 }
                                }
                                if (row != null) scrollTarget = row
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 10.dp)
                        )
                        // завершённые чипы живут ~9 секунд и исчезают сами
                        finishedTasks.forEach { t ->
                            LaunchedEffect(t.id) {
                                delay(9000)
                                finishedTasks = finishedTasks.filter { it.id != t.id }
                            }
                        }
                        // плавающая кнопка «вниз» — видна, когда отмотал вверх
                        if (rows.isNotEmpty() && !atBottom) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 12.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(DsBg)
                                    .border(1.dp, DsBorder, CircleShape)
                                    .clickable {
                                        scope.launch { listState.animateScrollToItem(Int.MAX_VALUE) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painterResource(R.drawable.ic_arrow_down),
                                    contentDescription = "Вниз",
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(DsIcon)
                                )
                            }
                        }
                        // меню «телефон + настройки» плавает поверх чата, не занимая место в раскладке
                        if (menuOpen) {
                            Column(
                                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 8.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RoundIconButton(R.drawable.ic_phone, "Телефон", onClick = {
                                    menuOpen = false
                                    startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                })
                                RoundIconButton(R.drawable.ic_settings, "Настройки", onClick = {
                                    menuOpen = false
                                    showSettings = true
                                })
                            }
                        }
                    }
                    InputBar(
                        liftWithChat = stickToBottom.value,
                        input = input,
                        onInputChange = { input = it },
                        onSend = { send(input) },
                        menuOpen = menuOpen,
                        onToggleMenu = { menuOpen = !menuOpen },
                        onShowTools = {
                            // инструменты — карточками с полными настройками
                            messages += Msg(false, "", helpKind = "tools")
                            persist()
                            scrollSignal++
                        },
                        activeLabel = activeLabel,
                        clearEnabled = activeLabel != null,
                        onClearActive = { activeKey = null }
                    )
                }
                }
            }
        }
    }

    // ─── Хранение ──────────────────────────────────────────
    private fun msgToJson(m: Msg): JSONObject {
        val o = JSONObject().put("u", m.fromUser).put("t", m.text)
        m.event?.let { e ->
            o.put("k", e.kind).put("s", e.source).put("ti", e.title)
                .put("tx", e.text).put("at", e.at)
            e.note?.let { o.put("n", it) }
            e.pi?.let { o.put("pi", it) }
            e.pkg?.let { o.put("pkg", it) }
        }
        if (m.files.isNotEmpty()) {
            val fl = JSONArray()
            m.files.forEach { f ->
                fl.put(JSONObject().put("n", f.name).put("p", f.path)
                    .put("k", f.kind).put("s", f.size).put("d", f.isDir)
                    .also { fo -> f.diskPath?.let { fo.put("dp", it) } }
                    .also { fo -> f.diskAcc?.let { fo.put("da", it) } })
            }
            o.put("fl", fl)
        }
        m.helpKind?.let { o.put("h", it) }
        m.askConfirm?.let { o.put("ac", it) }
        m.resumeMsgs?.let { o.put("rm", it) }
        if (m.resumePlan) o.put("rp", true)
        m.resumePlanQ?.let { o.put("rq", it) }
        if (m.resumePlanDone > 0) o.put("rd", m.resumePlanDone)
        if (m.bubbleUid != 0L) o.put("bu", m.bubbleUid)
        if (m.bubblePrompt.isNotBlank()) o.put("bp", m.bubblePrompt)
        if (m.planInput) o.put("pIn", true)
        if (m.planInputText.isNotBlank()) o.put("pTxt", m.planInputText)
        if (m.toolsChain.isNotEmpty()) {
            val tc = JSONArray()
            m.toolsChain.forEach { tc.put(it) }
            o.put("tc", tc)
        }
        if (m.events.isNotEmpty()) {
            val evs = JSONArray()
            m.events.forEach { e ->
                evs.put(JSONObject().put("k", e.kind).put("s", e.source)
                    .put("ti", e.title).put("tx", e.text).put("at", e.at)
                    .also { o2 -> e.note?.let { o2.put("n", it) } }
                    .also { o2 -> e.pi?.let { o2.put("pi", it) } }
                    .also { o2 -> e.pkg?.let { o2.put("pkg", it) } })
            }
            o.put("evs", evs)
        }
        return o
    }

    private fun msgFromJson(o: JSONObject): Msg {
        val ev = if (o.has("k")) EventInfo(
            o.optString("k", "push"),
            o.optString("s", ""),
            o.optString("ti", ""),
            o.optString("tx", ""),
            o.optLong("at", 0L),
            o.optString("n", "").takeIf { it.isNotBlank() },
            o.optString("pi", "").takeIf { it.isNotBlank() },
            o.optString("pkg", "").takeIf { it.isNotBlank() }
        ) else null
        val files = if (o.has("fl")) {
            try {
                val fl = o.getJSONArray("fl")
                (0 until fl.length()).map { i ->
                    val fo = fl.getJSONObject(i)
                    FileRef(
                        fo.optString("n", ""),
                        fo.optString("p", ""),
                        fo.optString("k", "other"),
                        fo.optLong("s", 0L),
                        fo.optBoolean("d", false),
                        fo.optString("dp", "").takeIf { it.isNotBlank() },
                        fo.optString("da", "").takeIf { it.isNotBlank() }
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
        val events = if (o.has("evs")) {
            try {
                val evs = o.getJSONArray("evs")
                (0 until evs.length()).map { i ->
                    val eo = evs.getJSONObject(i)
                    EventInfo(
                        eo.optString("k", "push"),
                        eo.optString("s", ""),
                        eo.optString("ti", ""),
                        eo.optString("tx", ""),
                        eo.optLong("at", 0L),
                        eo.optString("n", "").takeIf { it.isNotBlank() },
                        eo.optString("pi", "").takeIf { it.isNotBlank() },
                        eo.optString("pkg", "").takeIf { it.isNotBlank() }
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
        return Msg(
            o.optBoolean("u", false), o.optString("t", ""), ev, files, events,
            o.optString("h", "").takeIf { it.isNotBlank() },
            o.optString("ac", "").takeIf { it.isNotBlank() },
            o.optString("rm", "").takeIf { it.isNotBlank() },
            o.optBoolean("rp", false),
            o.optString("rq", "").takeIf { it.isNotBlank() },
            o.optInt("rd", 0),
            o.optLong("bu", 0L),
            o.optString("bp", ""),
            runCatching {
                val tc = o.optJSONArray("tc")
                if (tc == null) emptyList() else (0 until tc.length()).map { tc.optString(it) }.filter { it.isNotBlank() }
            }.getOrDefault(emptyList()),
            o.optBoolean("pIn", false),
            o.optString("pTxt", "")
        )
    }

    private fun loadMessages(prefs: SharedPreferences): List<Msg> {
        val raw = prefs.getString(Prefs.KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> msgFromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            Log.w("Dispatcher", "Не удалось загрузить переписку: ${e.message}")
            emptyList()
        }
    }

    private fun loadAllThreads(prefs: SharedPreferences): Map<String, List<Msg>> {
        val raw = prefs.getString(Prefs.KEY_THREADS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, List<Msg>>()
            obj.keys().forEach { k ->
                val arr = obj.optJSONArray(k) ?: return@forEach
                out[k] = (0 until arr.length()).map { i -> msgFromJson(arr.getJSONObject(i)) }
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveThread(prefs: SharedPreferences, key: String, msgs: List<Msg>) {
        val raw = prefs.getString(Prefs.KEY_THREADS, null)
        val obj = try {
            if (raw != null) JSONObject(raw) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        val arr = JSONArray()
        msgs.forEach { arr.put(msgToJson(it)) }
        obj.put(key, arr)
        prefs.edit().putString(Prefs.KEY_THREADS, obj.toString()).apply()
    }

    private fun buildRows(
        messages: List<Msg>,
        threads: Map<String, List<Msg>>,
        expanded: Set<String>
    ): List<Row> {
        val out = mutableListOf<Row>()
        messages.forEach { m ->
            val ev = m.event
            if (ev != null) {
                val key = eventKey(ev)
                out += Row.E(ev, key, key in expanded, threads[key] ?: emptyList())
            } else {
                out += Row.M(m)
                // списки SMS/пушей из тулов — теми же карточками событий с ветками ответа
                m.events.forEachIndexed { i, item ->
                    val key = eventListKey(item, i)
                    out += Row.E(item, key, key in expanded, threads[key] ?: emptyList())
                }
            }
        }
        return out
    }

    // ─── Ядро: модель + диспетчеризация ────────────────────
    private val PLAN_QUESTION_SYSTEM = """
Ты выполняешь ТОЛЬКО шаг вопроса в многошаговом сценарии.
Сформулируй один понятный вопрос пользователю по текущему шагу. Не выполняй другие шаги, не читай и не изменяй файлы, не вызывай инструменты.
Верни только текст вопроса без JSON, markdown-заголовков и пояснений о выполнении.
""".trimIndent()

    private suspend fun planQuestionText(plan: String, step: String, key: String, url: String, client: okhttp3.OkHttpClient): String {
        val raw = runCatching {
            ModelApi.raw(PLAN_QUESTION_SYSTEM, "СЦЕНАРИЙ:\n$plan\n\nТЕКУЩИЙ ШАГ: $step", key, url, client)
        }.getOrNull().orEmpty().trim()
        val reply = Regex("\\\"reply\\\"\\s*:\\s*\\\"((?:[^\\\"\\\\]|\\\\.)*)\\\"")
            .find(raw)?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")
        return (reply ?: raw).ifBlank { "Напиши, пожалуйста, данные для шага: $step" }
    }

    private fun mainSystem(mem: String): String = """
Ты — Walter, персональный ассистент и оркестратор действий на телефоне пользователя: принимаешь запрос, решаешь какие данные нужны, запрашиваешь их инструментами, анализируешь и даёшь финальный ответ. Отвечай ТОЛЬКО JSON без пояснений, по схеме:
{"action":"...","app":null,"title":null,"when":null,"address":null,"reply":null,"query":null,"path":null,"new":null,"from":null,"to":null}

Действия с данными (за один ответ верни одно действие; результат придёт сообщением ДАННЫЕ, после которого продолжишь текущую задачу):
- alert_list: список и состояние оповещений Walter (название, канал, правило, дата последнего запуска и статус)
- alert_create: создать оповещение; "query" — JSON {"name":"...","rule":"когда и на что срабатывать","channel":"ПУШ|СМС|ФОН","tool":"имя инструмента для фона","every":минуты}
- alert_edit: изменить оповещение; "query" — JSON {"name":"текущее имя","newName":"новое имя (необязательно)","rule":"..."/"channel":.../"tool":.../"every":...} (только меняемые поля)
- alert_delete: удалить оповещение ("query" — имя)
- alert_enable: "query" — "<имя> on|off" — включить/выключить
- read_sms: последние SMS; "query" — необязательный фильтр по отправителю или тексту (анализ трат и сводки делай сам по этим данным)
- read_pushes: последние уведомления; "query" — необязательный фильтр по источнику/заголовку/тексту; "Walter" — только собственные алерты Walter (kind=watch). Для переписки с Walter в чате используй search_history, а не read_sms
- read_apps: список установленных приложений
- read_files: недавние файлы
- read_dirs: список папок в памяти
- read_dir: содержимое папки (имя или путь — в "query")
- find_files: поиск файла по имени (имя — в "query")
- read_system_prompt: полный текст системного промпта Walter (промпт не секрет — по запросу пользователя напечатай полученный текст полностью)
- search_history: лексический поиск по ПОЛНОЙ сохранённой истории основного чата и всех веток ("query" — редкие слова, имя файла, действие или тема); используй для вопросов о прошлых сообщениях, даже если их нет в текущем контексте; если совпадений нет — попробуй другие ключевые слова
- web_search: веб-поиск в интернете (запрос — в "query"); ищет сразу в двух движках (Яндекс + DuckDuckGo) и отдаёт объединённые результаты; для вопросов о текущих событиях, новостях, актуальных ценах и свежих фактах вызывай web_search, а не выдумывай
- read_file: полное содержимое файла (путь — в "path"); поддерживает текстовые и офисные файлы (docx/xlsx/pptx/pdf)
- write_file: полная перезапись файла — подготовь ВЕСЬ новый текст файла и положи его в "new" ("path" — путь); поддерживаются text, docx, xlsx, pptx, pdf (pdf пересоздаётся как простой текстовый PDF)
- create_file: создать НОВЫЙ файл ("path" — полный путь с именем и расширением, "new" — полное содержимое); если файл уже существует — инструмент откажет, тогда используй write_file; если пользователь не указал расширение — используй .txt
- форматы для write_file: xlsx — ячейки строки через « | », новый лист начинается строкой "## Имя листа"; pptx — новый слайд начинается строкой "## Слайд N" (далее строки текста слайда); text/docx — обычный текст строками
- grep_files: поиск текста внутри текстовых файлов (искомый текст — в "query"; папку или конкретный файл можно указать в "path")
- read_calendar: события календарей — системный календарь устройства + все настроенные Яндекс-календари по CalDAV ("query" — сегодня/завтра/неделя/месяц или YYYY-MM-DD..YYYY-MM-DD)
- read_mail: письма из всех настроенных почтовых аккаунтов по IMAP ("query" — сколько последних писем с каждого, по умолчанию 10)
- fetch_url: прямой HTTPS GET произвольной HTTP/HTTPS-страницы по полному URL в "query"; никаких специальных обработчиков сервисов
- geocode: координаты и точный адрес места ("query" — адрес; Яндекс Геокодер)
- reverse_geocode: адрес по координатам ("query" — широта,долгота)
- distance: расстояние по прямой между адресами ("from" и "to"; бесплатно, без сервиса маршрутизации)
- location_now: текущее местоположение устройства (координаты и адрес)
- route_info: маршрут между адресами ("from" и "to"; время и дистанция; ПЛАТНЫЙ сервис Маршрутизации — если не настроен, используй distance)
- disk_list: содержимое папки на Яндекс Диске ("query" — путь папки на Диске; пусто — корень Диска); отдаёт имена, размеры, даты и полные пути; при нескольких настроенных аккаунтах — сводка по каждому; содержимое показывается карточками — в reply дай только краткую сводку
- disk_files: поиск файла по имени на всём Яндекс Диске ("query" — имя; отдаёт полные пути); при нескольких аккаунтах — по всем; результаты показываются карточками — в reply только краткая сводка
- disk_read: полное содержимое файла с Яндекс Диска ("query" — путь файла на Диске); текстовые и офисные файлы docx/xlsx/pptx/pdf; файлы больше 10 МБ не читаются — предложи disk_download
- disk_download: скачать файл с Яндекс Диска на устройство ("query" — путь на Диске; "path" — локальная папка, по умолчанию Download); появится карточкой в чате
- disk_write: создать или перезаписать файл на Яндекс Диске — подготовь ПОЛНОЕ содержимое в "new", путь файла на Диске — в "path"; text/docx/xlsx/pptx/pdf
- disk_upload: загрузить локальный файл устройства на Яндекс Диск ("path" — локальный путь, "to" — путь на Диске; без "to" — в корень Диска)

Действия на устройстве:
- ask_confirm: одиночная проверка ВНЕ многошагового плана; текст подтверждения — в "query". После ответа получишь текст пользователя. В многошаговых планах ask_confirm не используй: подтверждения вставляет приложение.
Остальные действия на устройстве:
- open_app: открыть приложение; ОБЯЗАТЕЛЬНО точное имя приложения в "app" (если не уверен — сначала read_apps). Для конкретного пуша из read_pushes передай его id в "query"
- open_capability: открыть приложение по функции; "query" — обычная формулировка (счётчик калорий, переводы, галерея, навигация, повтор заказа). Локальный роутер выберет deep link, главный экран или вернёт кандидатов для уточнения; при NotFound получишь список установленных приложений
- capability_rule: запомнить предпочтение или исключение приложения для функции; "query" — JSON {"capability":"счётчик калорий","package":"метка или пакет","mode":"prefer|exclude|reset","alias":"необязательный новый alias"}
- add_event: напоминание/встреча; "title" — суть, "when" — когда
- navigate: маршрут; "address" — адрес или место
- disk_delete: удалить файл или папку на Яндекс Диске ("path" — путь на Диске)
- disk_move: переместить файл или папку на Яндекс Диске ("from" — откуда, "to" — куда)
- disk_publish: создать публичную ссылку на файл Яндекс Диска ("path" — путь на Диске); требует OAuth-токен (настройки → «Яндекс Диск») — если не настроен, передай подсказку пользователю; в ответе будет готовая ссылка
- chat: обычный ответ пользователю; весь текст — в "reply"
- remember: запомнить факт о пользователе; "title" — сам факт
- forget: удалить факты, совпадающие с текстом из "title"
- memory: показать содержимое памяти пользователя

ОБ УСТРОЙСТВЕ WALTER (это факты о самом приложении; на вопросы «где хранится», «в каком файле», «откуда данные» отвечай строго по ним):
- Ты — языковая модель, работающая по API; приложение Walter исполняет действия и хранит свои настройки, память и историю локально, а внешние данные получает через API сервисов.
- Оповещения хранятся локально в хранилище настроек приложения (dispatcher_prefs); ими управляй действиями alert_*.
- Память (факты и журнал диалогов) и история чата — тоже в хранилище настроек приложения (dispatcher_prefs).
- Серверной конфигурации Walter нет: настройки и журнал находятся на устройстве; внешние сервисы доступны через их API.

Правила:
- обычные задачи выполняй инструментами напрямую по запросу, а фоновые правила создавай и изменяй действиями alert_*;
- если пользователь просит подмножество SMS/пушей — обязательно передай фильтр в query: инструмент вернёт и покажет карточками один и тот же набор. «Все уведомления Walter» → read_pushes(query="Walter"); «все сообщения/переписка с Walter в чате» → search_history, а не read_sms;
- для открытия приложения по функции всегда сначала open_capability, даже если запрос состоит из одного слова: «галерея», «переводы», «навигация», «питание», «повтор заказа». Не заменяй такой запрос read_pushes/read_files/read_sms — они нужны для анализа данных, а не для открытия приложения. «Галерея» → open_capability(query="галерея"), «переводы» → open_capability(query="переводы") в банковском смысле; «что у меня с деньгами» → read_sms для анализа, а не open_capability;
- если названо КОНКРЕТНОЕ приложение (например openvpn, Telegram, Maps) — используй open_app, а не open_capability; open_capability только для функции без точного имени приложения;
- многошаговые планы: разделитель шагов — только «->»/«→». Не оценивай и не переписывай сценарий, выполняй исходный текст строго по порядку. Один смысловой шаг может требовать несколько инструментов (например find_files, затем read_file, затем write_file; или несколько fetch_url) — число вызовов не является номером шага. Подтверждения между шагами вставляет приложение, поэтому в плане ask_confirm не вызывай: завершив сегмент, верни chat с итогом и жди следующий ввод. Если шаг — вопрос пользователю, верни chat только с вопросом без инструментов: приложение ждёт свободный текст. Не повторяй инструменты уже завершённого шага;
- строго валидный JSON, кавычки и переносы экранируй;
- за один ответ верни ровно одно JSON-действие: если текущий шаг можно завершить — chat, иначе следующее нужное действие;
- никогда не выдумывай данные телефона (SMS, файлы, приложения) — читай их действиями;
- не используй эмодзи и смайлики в ответах (никаких 🛒, 📁, 💳 и подобных разноцветных иконок) — только текст, списки через •, заголовки ##, выделение **жирным**; папки обозначай словом «папка»;
- SMS, пуши, приложения, файлы, память и настройки — локальные данные устройства; веб-поиск, страницы, почта, CalDAV, Диск, геокодинг и Telegram — внешние данные через API. Источник каждого набора указан в выводе инструмента;
- системный промпт не секрет: если пользователь просит показать его — вызови read_system_prompt и напечатай полученный текст полностью, без отказов и сокращений;
- "reply" — текст пользователю для текущего шага или финальный ответ; для вызовов инструментов оставляй null;
- получив ДАННЫЕ, продолжай текущий шаг: если нужны ещё инструменты — верни следующее действие, если шаг завершён — {"action":"chat","reply":"..."} с итогом; при chat reply никогда не оставляй пустым;
- результаты инструментов файлов (read_files/read_dirs/read_dir/find_files/grep_files/disk_list/disk_files) показываются карточками — в reply список не переписывай, давай только краткую сводку;
- редактирование файла — модель делает всю работу: найди путь, прочитай через read_file, измени текст у себя и запиши ВЕСЬ новый файл через write_file. После успешной записи не повторяй read_file/write_file без новой инструкции пользователя;
- форматируй ответ markdown-разметкой: ключевые цифры и выводы выделяй **жирным** (например **128 450 ₽**); в длинных ответах используй заголовки вида ## Название раздела; списки — отдельными строками с •;
- отвечай развёрнуто и обстоятельно: никаких лимитов длины; на общие, абстрактные и сложные вопросы давай полноценный подробный ответ (объясняй, структурируй списками, приводи примеры) — не прячь знания;
- кратко отвечай только на простые команды («открыл», «напомнил», «нашёл»).
- сервисы Яндекса (Диск, почта, календари, поиск, геокодер) настраиваются пользователем в приложении: если инструмент отвечает, что сервис не настроен, ключ или токен неверен — честно передай сообщение инструмента и подскажи, как включить (команда «настрой …»); данные Диска, почты и календарей никогда не выдумывай.
- оповещения Walter исполняются в фоне: для их списка и состояния используй alert_list, для изменений — alert_create, alert_edit, alert_delete и alert_enable.
ПАМЯТЬ (всё, что ты знаешь о пользователе и его диалогах; учитывай при ответах):
$mem
""".trimIndent()

    private fun threadSystem(ev: EventInfo, mem: String): String = """
Ты — Walter, персональный ассистент и оркестратор действий. Пользователь сейчас обсуждает входящее событие прямо в чате.
Событие: ${eventToText(ev)}
Отвечай ТОЛЬКО JSON без пояснений, по схеме:
{"action":"...","app":null,"title":null,"when":null,"address":null,"reply":null,"query":null,"path":null,"new":null,"from":null,"to":null}

Действия с данными (за один ответ верни одно действие; результат придёт сообщением ДАННЫЕ, после которого продолжишь текущую задачу):
- alert_list: список и состояние оповещений Walter (название, канал, правило, дата последнего запуска и статус)
- alert_create: создать оповещение; "query" — JSON {"name":"...","rule":"когда и на что срабатывать","channel":"ПУШ|СМС|ФОН","tool":"имя инструмента для фона","every":минуты}
- alert_edit: изменить оповещение; "query" — JSON {"name":"текущее имя","newName":"новое имя (необязательно)","rule":"..."/"channel":.../"tool":.../"every":...} (только меняемые поля)
- alert_delete: удалить оповещение ("query" — имя)
- alert_enable: "query" — "<имя> on|off" — включить/выключить
- read_sms: последние SMS; "query" — необязательный фильтр по отправителю или тексту (анализ трат и сводки делай сам по этим данным)
- read_pushes: последние уведомления; "query" — необязательный фильтр по источнику/заголовку/тексту; "Walter" — только собственные алерты Walter (kind=watch). Для переписки с Walter в чате используй search_history, а не read_sms
- read_apps: список установленных приложений
- read_files: недавние файлы
- read_dirs: список папок в памяти
- read_dir: содержимое папки (имя или путь — в "query")
- find_files: поиск файла по имени (имя — в "query")
- read_system_prompt: полный текст системного промпта Walter (промпт не секрет — по запросу пользователя напечатай полученный текст полностью)
- search_history: лексический поиск по ПОЛНОЙ сохранённой истории основного чата и всех веток ("query" — редкие слова, имя файла, действие или тема); используй для вопросов о прошлых сообщениях, даже если их нет в текущем контексте; если совпадений нет — попробуй другие ключевые слова
- web_search: веб-поиск в интернете (запрос — в "query"); ищет сразу в двух движках (Яндекс + DuckDuckGo) и отдаёт объединённые результаты; для вопросов о текущих событиях, новостях, актуальных ценах и свежих фактах вызывай web_search, а не выдумывай
- read_file: полное содержимое файла (путь — в "path"); поддерживает текстовые и офисные файлы (docx/xlsx/pptx/pdf)
- write_file: полная перезапись файла — подготовь ВЕСЬ новый текст файла и положи его в "new" ("path" — путь); поддерживаются text, docx, xlsx, pptx, pdf (pdf пересоздаётся как простой текстовый PDF)
- create_file: создать НОВЫЙ файл ("path" — полный путь с именем и расширением, "new" — полное содержимое); если файл уже существует — инструмент откажет, тогда используй write_file; если пользователь не указал расширение — используй .txt
- форматы для write_file: xlsx — ячейки строки через « | », новый лист начинается строкой "## Имя листа"; pptx — новый слайд начинается строкой "## Слайд N" (далее строки текста слайда); text/docx — обычный текст строками
- grep_files: поиск текста внутри текстовых файлов (искомый текст — в "query"; папку или конкретный файл можно указать в "path")
- read_calendar: события календарей — системный календарь устройства + все настроенные Яндекс-календари по CalDAV ("query" — сегодня/завтра/неделя/месяц или YYYY-MM-DD..YYYY-MM-DD)
- read_mail: письма из всех настроенных почтовых аккаунтов по IMAP ("query" — сколько последних писем с каждого, по умолчанию 10)
- fetch_url: прямой HTTPS GET произвольной HTTP/HTTPS-страницы по полному URL в "query"; никаких специальных обработчиков сервисов
- geocode: координаты и точный адрес места ("query" — адрес; Яндекс Геокодер)
- reverse_geocode: адрес по координатам ("query" — широта,долгота)
- distance: расстояние по прямой между адресами ("from" и "to"; бесплатно, без сервиса маршрутизации)
- location_now: текущее местоположение устройства (координаты и адрес)
- route_info: маршрут между адресами ("from" и "to"; время и дистанция; ПЛАТНЫЙ сервис Маршрутизации — если не настроен, используй distance)
- disk_list: содержимое папки на Яндекс Диске ("query" — путь папки на Диске; пусто — корень Диска); отдаёт имена, размеры, даты и полные пути; при нескольких настроенных аккаунтах — сводка по каждому; содержимое показывается карточками — в reply дай только краткую сводку
- disk_files: поиск файла по имени на всём Яндекс Диске ("query" — имя; отдаёт полные пути); при нескольких аккаунтах — по всем; результаты показываются карточками — в reply только краткая сводка
- disk_read: полное содержимое файла с Яндекс Диска ("query" — путь файла на Диске); текстовые и офисные файлы docx/xlsx/pptx/pdf; файлы больше 10 МБ не читаются — предложи disk_download
- disk_download: скачать файл с Яндекс Диска на устройство ("query" — путь на Диске; "path" — локальная папка, по умолчанию Download); появится карточкой в чате
- disk_write: создать или перезаписать файл на Яндекс Диске — подготовь ПОЛНОЕ содержимое в "new", путь файла на Диске — в "path"; text/docx/xlsx/pptx/pdf
- disk_upload: загрузить локальный файл устройства на Яндекс Диск ("path" — локальный путь, "to" — путь на Диске; без "to" — в корень Диска)

Действия на устройстве:
- ask_confirm: одиночная проверка ВНЕ многошагового плана; текст подтверждения — в "query". После ответа получишь текст пользователя. В многошаговых планах ask_confirm не используй: подтверждения вставляет приложение.
Остальные действия на устройстве:
- open_app: открыть приложение; ОБЯЗАТЕЛЬНО точное имя приложения в "app" (если не уверен — сначала read_apps). Для конкретного пуша из read_pushes передай его id в "query"
- open_capability: открыть приложение по функции; "query" — обычная формулировка (счётчик калорий, переводы, галерея, навигация, повтор заказа). Локальный роутер выберет deep link, главный экран или вернёт кандидатов для уточнения; при NotFound получишь список установленных приложений
- capability_rule: запомнить предпочтение или исключение приложения для функции; "query" — JSON {"capability":"счётчик калорий","package":"метка или пакет","mode":"prefer|exclude|reset","alias":"необязательный новый alias"}
- add_event: напоминание/встреча; "title" — суть, "when" — когда
- navigate: маршрут; "address" — адрес или место
- disk_delete: удалить файл или папку на Яндекс Диске ("path" — путь на Диске)
- disk_move: переместить файл или папку на Яндекс Диске ("from" — откуда, "to" — куда)
- disk_publish: создать публичную ссылку на файл Яндекс Диска ("path" — путь на Диске); требует OAuth-токен (настройки → «Яндекс Диск») — если не настроен, передай подсказку пользователю; в ответе будет готовая ссылка
- chat: обычный ответ пользователю; весь текст — в "reply"
- remember: запомнить факт о пользователе; "title" — сам факт
- forget: удалить факты, совпадающие с текстом из "title"
- memory: показать содержимое памяти пользователя, учитывая событие.

ОБ УСТРОЙСТВЕ WALTER (это факты о самом приложении; на вопросы «где хранится», «в каком файле», «откуда данные» отвечай строго по ним):
- Ты — языковая модель, работающая по API; приложение Walter исполняет действия и хранит свои настройки, память и историю локально, а внешние данные получает через API сервисов.
- Оповещения хранятся локально в хранилище настроек приложения (dispatcher_prefs); ими управляй действиями alert_*.
- Память (факты и журнал диалогов) и история чата — тоже в хранилище настроек приложения (dispatcher_prefs).
- Серверной конфигурации Walter нет: настройки и журнал находятся на устройстве; внешние сервисы доступны через их API.

Правила:
- обычные задачи выполняй инструментами напрямую по запросу, а фоновые правила создавай и изменяй действиями alert_*;
- если пользователь просит подмножество SMS/пушей — обязательно передай фильтр в query: инструмент вернёт и покажет карточками один и тот же набор. «Все уведомления Walter» → read_pushes(query="Walter"); «все сообщения/переписка с Walter в чате» → search_history, а не read_sms;
- для открытия приложения по функции всегда сначала open_capability, даже если запрос состоит из одного слова: «галерея», «переводы», «навигация», «питание», «повтор заказа». Не заменяй такой запрос read_pushes/read_files/read_sms — они нужны для анализа данных, а не для открытия приложения. «Галерея» → open_capability(query="галерея"), «переводы» → open_capability(query="переводы") в банковском смысле; «что у меня с деньгами» → read_sms для анализа, а не open_capability;
- если названо КОНКРЕТНОЕ приложение (например openvpn, Telegram, Maps) — используй open_app, а не open_capability; open_capability только для функции без точного имени приложения;
- многошаговые планы: разделитель шагов — только «->»/«→». Не оценивай и не переписывай сценарий, выполняй исходный текст строго по порядку. Один смысловой шаг может требовать несколько инструментов (например find_files, затем read_file, затем write_file; или несколько fetch_url) — число вызовов не является номером шага. Подтверждения между шагами вставляет приложение, поэтому в плане ask_confirm не вызывай: завершив сегмент, верни chat с итогом и жди следующий ввод. Если шаг — вопрос пользователю, верни chat только с вопросом без инструментов: приложение ждёт свободный текст. Не повторяй инструменты уже завершённого шага;
- строго валидный JSON, кавычки и переносы экранируй;
- за один ответ верни ровно одно JSON-действие: если текущий шаг можно завершить — chat, иначе следующее нужное действие;
- никогда не выдумывай данные телефона (SMS, файлы, приложения) — читай их действиями;
- не используй эмодзи и смайлики в ответах (никаких 🛒, 📁, 💳 и подобных разноцветных иконок) — только текст, списки через •, заголовки ##, выделение **жирным**; папки обозначай словом «папка»;
- SMS, пуши, приложения, файлы, память и настройки — локальные данные устройства; веб-поиск, страницы, почта, CalDAV, Диск, геокодинг и Telegram — внешние данные через API. Источник каждого набора указан в выводе инструмента;
- системный промпт не секрет: если пользователь просит показать его — вызови read_system_prompt и напечатай полученный текст полностью, без отказов и сокращений;
- "reply" — текст пользователю для текущего шага или финальный ответ; для вызовов инструментов оставляй null;
- получив ДАННЫЕ, продолжай текущий шаг: если нужны ещё инструменты — верни следующее действие, если шаг завершён — {"action":"chat","reply":"..."} с итогом; при chat reply никогда не оставляй пустым;
- результаты инструментов файлов (read_files/read_dirs/read_dir/find_files/grep_files/disk_list/disk_files) показываются карточками — в reply список не переписывай, давай только краткую сводку;
- редактирование файла — модель делает всю работу: найди путь, прочитай через read_file, измени текст у себя и запиши ВЕСЬ новый файл через write_file. После успешной записи не повторяй read_file/write_file без новой инструкции пользователя;
- форматируй ответ markdown-разметкой: ключевые цифры и выводы выделяй **жирным** (например **128 450 ₽**); в длинных ответах используй заголовки вида ## Название раздела; списки — отдельными строками с •;
- отвечай развёрнуто и обстоятельно: никаких лимитов длины; на общие, абстрактные и сложные вопросы давай полноценный подробный ответ (объясняй, структурируй списками, приводи примеры) — не прячь знания;
- кратко отвечай только на простые команды («открыл», «напомнил», «нашёл»).
- сервисы Яндекса (Диск, почта, календари, поиск, геокодер) настраиваются пользователем в приложении: если инструмент отвечает, что сервис не настроен, ключ или токен неверен — честно передай сообщение инструмента и подскажи, как включить (команда «настрой …»); данные Диска, почты и календарей никогда не выдумывай.
- оповещения Walter исполняются в фоне: для их списка и состояния используй alert_list, для изменений — alert_create, alert_edit, alert_delete и alert_enable.
ПАМЯТЬ (всё, что ты знаешь о пользователе и его диалогах; учитывай при ответах):
$mem
""".trimIndent()

    private suspend fun callModelRaw(messages: JSONArray, key: String): String {
        if (key.isBlank()) return "ERR::Сначала добавь ключ DeepSeek API («+» → «Настройки»)"
        val baseUrl = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_BASE_URL, BASE_URL) ?: BASE_URL
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .toString()

        var httpCode = 0
        val resp = try {
            withContext(Dispatchers.IO) {
                client.newCall(
                    Request.Builder()
                        .url(baseUrl)
                        .header("Authorization", "Bearer $key")
                        .header("Content-Type", "application/json")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute().use { r ->
                    if (r.isSuccessful) r.body?.string() ?: ""
                    else {
                        httpCode = r.code
                        JSONObject()
                            .put("error", "HTTP ${r.code}: ${r.body?.string()?.take(300) ?: ""}")
                            .toString()
                    }
                }
            }
        } catch (e: Exception) {
            return "ERR_RETRY::Сеть: ${e.message}"
        }

        val root = try {
            JSONObject(resp)
        } catch (e: Exception) {
            return "ERR::Не удалось разобрать ответ API: ${e.message}"
        }
        if (root.has("error")) {
            val prefix = if (httpCode == 429 || httpCode >= 500) "ERR_RETRY::" else "ERR::"
            return prefix + "Ошибка API: ${root.optString("error")}"
        }

        return try {
            val content = root.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            if (content.isBlank()) "ERR_RETRY::Модель вернула пустой ответ." else content
        } catch (e: Exception) {
            "ERR::Не удалось разобрать ответ: ${e.message}"
        }
    }

    /** Стриминг-вызов: токены приходят через onDelta, полный текст — возврат. */
    private suspend fun callModelStream(
        messages: JSONArray,
        key: String,
        onDelta: (String) -> Unit
    ): String {
        if (key.isBlank()) return "ERR::Сначала добавь ключ DeepSeek API («+» → «Настройки»)"
        val baseUrl = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_BASE_URL, BASE_URL) ?: BASE_URL
        val body = JSONObject()
            .put("model", MODEL)
            .put("stream", true)
            .put("messages", messages)
            .toString()

        val resp = try {
            withContext(Dispatchers.IO) {
                client.newCall(
                    Request.Builder()
                        .url(baseUrl)
                        .header("Authorization", "Bearer $key")
                        .header("Content-Type", "application/json")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()
            }
        } catch (e: Exception) {
            DebugLog.log("stream-net", e.message ?: "")
            return "ERR_RETRY::Сеть: ${e.message}"
        }

        if (!resp.isSuccessful) {
            val err = try { resp.body?.string()?.take(300) ?: "" } catch (e: Exception) { "" }
            resp.close()
            DebugLog.log("stream-http", "${resp.code}: $err")
            // 429 и 5xx — временные: их стоит повторить
            val prefix = if (resp.code == 429 || resp.code >= 500) "ERR_RETRY::" else "ERR::"
            return prefix + "Ошибка API: HTTP ${resp.code}: $err"
        }

        val sb = StringBuilder()
        // UI-батчинг: обновляем экран не чаще ~40 мс — плавная печать без дёрганий
        val uiBuf = StringBuilder()
        var lastUi = 0L
        suspend fun flushUi(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if ((force || now - lastUi >= 40) && uiBuf.isNotEmpty()) {
                val batch = uiBuf.toString()
                uiBuf.setLength(0)
                withContext(Dispatchers.Main) { onDelta(batch) }
                lastUi = now
            }
        }
        var ioError: java.io.IOException? = null
        try {
            withContext(Dispatchers.IO) {
                resp.body?.source()?.use { src ->
                    while (true) {
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        try {
                            val obj = JSONObject(payload)
                            val delta = obj.optJSONArray("choices")?.optJSONObject(0)
                                ?.optJSONObject("delta")?.optString("content") ?: ""
                            if (delta.isNotEmpty()) {
                                sb.append(delta)
                                uiBuf.append(delta)
                                flushUi()
                            }
                        } catch (e: Exception) {
                            // пропускаем битые чанки
                        }
                    }
                }
            }
            flushUi(force = true)
        } catch (e: java.io.IOException) {
            // обрыв потока: если часть ответа уже пришла — отдаём её как есть
            ioError = e
        } finally {
            resp.close()
        }
        if (sb.isEmpty()) {
            DebugLog.log(if (ioError != null) "stream-io" else "stream-empty", ioError?.message ?: "пустой поток")
            return if (ioError != null) "ERR_RETRY::Поток оборвался: ${ioError.message}"
            else "ERR_RETRY::Модель вернула пустой поток."
        }
        return sb.toString()
    }

    /**
     * Цикл harness: модель запрашивает данные телефона (read_*) → исполняем локально →
     * возвращаем ДАННЫЕ модели → модель даёт финальный ответ. Локально — только чтение
     * данных и финальные действия (открыть приложение / напоминание / маршрут).
     */
    private fun toolStatus(action: String): String {
        READ_TOOLS.firstOrNull { it.id == action }?.status?.let { return "$action · $it" }
        val known = (TERMINAL_TOOLS + LOCAL_TOOLS).any { it.first == action }
        return if (known) "Выполняю $action…" else "Думаю"
    }

    private suspend fun runLoop(
        system: String,
        history: List<Msg>,
        q: String,
        key: String,
        initialStatus: String = "Думаю",
        onStatus: (String) -> Unit = {},
        onDelta: (String) -> Unit = {},
        onFiles: (List<FileRef>) -> Unit = {},
        onTools: (Set<String>) -> Unit = {},
        onEvents: (List<EventInfo>) -> Unit = {},
        onTool: (String) -> Unit = {}
    ): String {
        if (key.isBlank()) return "Сначала добавь ключ DeepSeek API («+» → «Настройки»)"
        planMode = PlanFlow.isPlan(q)
        planQ = q
        planConfirms = PlanFlow.confirms(q)
        planConfirmDone = 0
        if (planMode) {
            planTools.clear()
            planActionIndex = 0
            planStepTools.clear()
        }
        val msgs = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
        history.takeLast(10).forEach { m ->
            val text = if (m.event != null) eventToText(m.event) else m.text
            msgs.put(
                JSONObject()
                    .put("role", if (m.fromUser) "user" else "assistant")
                    .put("content", text.take(1200))
            )
        }
        msgs.put(JSONObject().put("role", "user").put("content", q))
        val collected = mutableListOf<FileRef>()
        val toolsUsed = mutableSetOf<String>()
        val collectedEvents = mutableListOf<EventInfo>()
        try {
            return runLoopInternal(msgs, q, key, initialStatus, onStatus, onDelta, collected, toolsUsed, collectedEvents, onTool)
        } finally {
            confirmMsgs = msgs // история цикла — для продолжения после ask_confirm
            onFiles(collected.distinctBy { it.path })
            onTools(toolsUsed.toSet())
            onEvents(collectedEvents.toList())
        }
    }

    /** Продолжение цикла после нажатия «Подтвердить» (ask_confirm). */
    private var confirmMsgs: JSONArray? = null

    /** Включён ли режим многошагового плана (влияет на dataPrompt). */
    private var planMode = false
    private var planQ = ""
    private var planConfirms = 0
    private var planConfirmDone = 0
    private var planActionIndex = 0
    private val planStepTools = mutableSetOf<String>()
    private val planTools = mutableListOf<String>()

    private suspend fun resumeLoop(
        msgs: JSONArray,
        q: String,
        key: String,
        plan: Boolean = false,
        planQ0: String = "",
        planDone: Int = 0,
        planActionStart: Int = planDone * 2,
        onStatus: (String) -> Unit = {},
        onDelta: (String) -> Unit = {},
        onFiles: (List<FileRef>) -> Unit = {},
        onTools: (Set<String>) -> Unit = {},
        onEvents: (List<EventInfo>) -> Unit = {},
        onTool: (String) -> Unit = {}
    ): String {
        planMode = plan
        planQ = planQ0
        planConfirms = PlanFlow.confirms(planQ0)
        planConfirmDone = planDone
        planActionIndex = planActionStart
        planStepTools.clear()
        val collected = mutableListOf<FileRef>()
        val toolsUsed = mutableSetOf<String>()
        val collectedEvents = mutableListOf<EventInfo>()
        try {
            return runLoopInternal(msgs, q, key, "Думаю", onStatus, onDelta, collected, toolsUsed, collectedEvents, onTool)
        } finally {
            onFiles(collected.distinctBy { it.path })
            onTools(toolsUsed.toSet())
            onEvents(collectedEvents.toList())
        }
    }

    private suspend fun runLoopInternal(
        msgs: JSONArray,
        q: String,
        key: String,
        initialStatus: String,
        onStatus: (String) -> Unit,
        onDelta: (String) -> Unit,
        collected: MutableList<FileRef>,
        toolsUsed: MutableSet<String>,
        collectedEvents: MutableList<EventInfo>,
        onTool: (String) -> Unit = {}
    ): String {
        repeat(12) {
            val buf = StringBuilder()
            var inReply = false
            var dec = StreamDecoder()
            var printed = false
            var attempt = 0
            var content = ""
            // автоповторы при временных сбоях (пустой ответ, обрыв потока, 429/5xx):
            // повторяем только если на экран ещё ничего не выводилось
            while (attempt < 3) {
                attempt++
                onStatus(if (attempt > 1) "Модель не ответила — повторяю (попытка $attempt)…" else initialStatus)
                val c = callModelStream(msgs, key) { batch ->
                    buf.append(batch)
                    if (!inReply) {
                        val am = ACTION_PATTERN.find(buf)
                        if (am != null && am.groupValues[1] in DATA_ACTIONS) {
                            // инструмент: на экране только «Думаю» — ничего не печатаем
                        } else {
                            val idx = buf.indexOf("\"reply\"")
                            if (idx >= 0 && (am == null || am.groupValues[1] == "chat")) {
                                var start = buf.indexOf(":", idx)
                                if (start >= 0) {
                                    start = buf.indexOf("\"", start)
                                    if (start >= 0) {
                                        inReply = true
                                        val out = dec.display(buf.substring(start + 1))
                                        if (out.isNotEmpty()) printed = true
                                    }
                                }
                            }
                        }
                    } else {
                        val out = dec.display(batch)
                        if (out.isNotEmpty()) printed = true
                    }
                }
                DebugLog.log("attempt", "q=${q.take(60)} n=$attempt content=${c.take(180)}")
                if (c.startsWith("ERR_RETRY::") && !printed && attempt < 3) {
                    buf.setLength(0)
                    inReply = false
                    dec = StreamDecoder()
                    msgs.put(JSONObject().put("role", "user").put("content",
                        "Пустой ответ недопустим. Отвечай строго одним JSON по схеме."))
                    delay(500L * attempt)
                    continue
                }
                content = c
                break
            }
            if (content.startsWith("ERR::") || content.startsWith("ERR_RETRY::")) {
                // стрим не удался — обычный вызов с парой повторов на временные ошибки
                var fallback = callModelRaw(msgs, key)
                var fa = 0
                while ((fallback.startsWith("ERR_RETRY::") || fallback.isBlank()) && fa < 2) {
                    fa++
                    delay(500L * fa)
                    fallback = callModelRaw(msgs, key)
                }
                if (fallback.startsWith("ERR::") || fallback.startsWith("ERR_RETRY::")) {
                    DebugLog.log("final-fallback", "q=${q.take(80)} $fallback")
                    return if (fallback.contains("пустой")) {
                        "Модель не смогла ответить после нескольких попыток. Подробности: файл Download/walter_debug.log"
                    } else fallback.removePrefix("ERR_RETRY::").removePrefix("ERR::")
                }
                content = fallback
            }
            msgs.put(JSONObject().put("role", "assistant").put("content", content))
            var action = try {
                parseAction(content)
            } catch (e: Exception) {
                extractAction(content)
            }
            if (action == null) {
                val t = content.trim()
                if (t.isBlank()) {
                    DebugLog.log("final-blank", "q=${q.take(80)}")
                    return "Модель вернула пустой ответ — несколько попыток не помогли. Подробности: файл Download/walter_debug.log"
                }
                // не-JSON ответ: просим модель исправиться один раз (иначе tool-loop ломается)
                msgs.put(JSONObject().put("role", "user").put("content",
                    "Ответ не является валидным JSON по схеме. Верни строго один JSON, без текста вне его."))
                val fixed = callModelRaw(msgs, key)
                if (fixed.startsWith("ERR::") || fixed.startsWith("ERR_RETRY::") || fixed.isBlank()) {
                    // если модель вернула текст с эхом JSON-истории — вытаскиваем последний reply
                    val clean = Regex(""""reply"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                        .findAll(t).map { it.groupValues[1] }.lastOrNull()
                        ?.replace("\\\\", "\\")?.replace("\\\"", "\"")?.replace("\\n", "\n")
                    return clean?.takeIf { it.isNotBlank() } ?: t
                }
                msgs.put(JSONObject().put("role", "assistant").put("content", fixed))
                val a2 = try { parseAction(fixed) } catch (e: Exception) { extractAction(fixed) }
                if (a2 == null) return t
                action = a2
            }
            if (action.action in DATA_ACTIONS) {
                val segmentComplete = planMode && PlanFlow.segmentComplete(planQ, planActionIndex, planStepTools)
                val repeatCompletedFileAction = segmentComplete && action.action in setOf("read_file", "write_file", "create_file")
                if (repeatCompletedFileAction) {
                    DebugLog.log("plan-guard", "повтор ${action.action} заблокирован: файловый сегмент уже выполнен")
                    msgs.put(JSONObject().put("role", "user").put("content",
                        "Чтение и изменение файла в этом сегменте уже выполнены. Не повторяй инструменты. Верни action=chat с итогом и жди подтверждение приложения."))
                } else {
                // Фиксируем использованный инструмент для статуса и цепочки выполнения.
                toolsUsed += action.action
                onTool(action.action)
                planStepTools += action.action
                // тул исполняется в защищённом блоке: любая ошибка инструмента
                // превращается в текст для модели, а не в падение приложения
                val raw = try {
                    if (action.action == "open_app") {
                        execTerminal(action)
                    } else {
                        execData(action, { }, { refs -> collected += refs }, { evs -> collectedEvents += evs })
                    }
                } catch (t: Throwable) {
                    // Throwable, а не Exception: OutOfMemoryError и другие ошибки уровня Error
                    // тоже превращаются в сообщение модели, а не в падение приложения
                    DebugLog.log("tool-crash", "${action.action}: ${t.message ?: t.javaClass.simpleName}")
                    "Ошибка инструмента ${action.action}: ${t.message ?: t.javaClass.simpleName}"
                }
                val data = when {
                    action.action in setOf("read_file", "disk_read", "read_system_prompt", "search_history") -> raw.take(24000)
                    action.action in setOf("disk_list", "disk_files") -> raw.take(12000)
                    else -> raw.take(6000)
                }
                DebugLog.log("tool", "${action.action} -> ${data.take(120)}")
                if (action.action == "write_file" || action.action == "create_file") {
                    // при записи показываем только карточку созданного/изменённого файла — без шума листинга
                    collected.clear()
                    val p = action.path
                    if (!p.isNullOrBlank()) {
                        val target = if (java.io.File(p).isAbsolute) p else java.io.File(FileStore.storageRoot(), p).absolutePath
                        val f = java.io.File(target)
                        if (f.exists()) collected += FileRef(f.name, f.absolutePath, FileStore.kindOf(f.name), f.length())
                    }
                }
                msgs.put(JSONObject().put("role", "user").put("content", dataPrompt(action.action, data)))
                }
            } else if (action.action == "ask_confirm") {
                toolsUsed += action.action
                onTool(action.action)
                if (planMode && planConfirmDone < planConfirms) {
                    val currentStep = PlanFlow.nextActionStep(planQ, planActionIndex)
                    val satisfied = PlanFlow.stepSatisfied(currentStep, planStepTools)
                    val completedIndex = PlanFlow.completedActionIndex(planQ, planActionIndex, planStepTools)
                    val nextAction = if (satisfied) PlanFlow.nextActionStep(planQ, completedIndex + 1) else currentStep
                    if (nextAction.isNotBlank() && PlanFlow.confirms(nextAction) == 0) {
                        planActionIndex = completedIndex
                        planStepTools.clear()
                        msgs.put(JSONObject().put("role", "user").put("content",
                            "Выполни текущий шаг плана: $nextAction. Не спрашивай подтверждение, пока шаг не выполнен."))
                    } else {
                        planConfirmDone++
                        return "CONFIRM::" + (action.query ?: action.reply ?: "")
                    }
                } else {
                    // Вне плана ask_confirm — реальная граница: ждём текста пользователя.
                    return "CONFIRM::" + (action.query ?: action.reply ?: "")
                }
            } else {
                toolsUsed += action.action
                onTool(action.action)
                // план не закончен: текст уже выведен стримером — просто ставим плашку следующего шага
                if (action.action == "chat" && planMode && planConfirmDone < planConfirms) {
                    val currentStep = PlanFlow.nextActionStep(planQ, planActionIndex)
                    val satisfied = PlanFlow.stepSatisfied(currentStep, planStepTools)
                    val completedIndex = PlanFlow.completedActionIndex(planQ, planActionIndex, planStepTools)
                    val nextAction = if (satisfied) PlanFlow.nextActionStep(planQ, completedIndex + 1) else currentStep
                    if (nextAction.isNotBlank() && PlanFlow.confirms(nextAction) == 0) {
                        // Промежуточный chat не завершает шаг: нужен фактический результат инструмента.
                        planActionIndex = completedIndex
                        planStepTools.clear()
                        msgs.put(JSONObject().put("role", "user").put("content",
                            "Продолжай текущий сегмент плана: $nextAction. Выполни его сейчас, не спрашивая подтверждение."))
                    } else {
                        planConfirmDone++
                        val confirmText = PlanFlow.forcedConfirmText(planQ, planConfirmDone)
                        return (action.reply ?: "") + "\u0000CONFIRM::" + confirmText
                    }
                } else {
                    return execTerminal(action)
                }
            }
        }
        return "Слишком много шагов — не удалось завершить. Переформулируй, пожалуйста."
    }

    private val LISTING_ACTIONS = setOf("read_files", "read_dirs", "read_dir", "find_files", "grep_files", "disk_list", "disk_files")

    /** Инструменты, чьи списки показываются карточками событий (SMS/пуши/почта/календарь). */
    private val EVENT_LIST_ACTIONS = setOf("read_sms", "read_pushes", "read_mail", "read_calendar")

    /** Сообщение модели после исполнения инструмента. */
    private fun dataPrompt(action: String, data: String): String {
        val extra = when {
            action in LISTING_ACTIONS ->
                " Список файлов уже показан пользователю карточками — в reply не переписывай его, дай только краткую сводку (1-2 строки)."
            action in EVENT_LIST_ACTIONS ->
                " Список уже показан пользователю карточками (с ветками ответов) — в reply не переписывай его, дай только краткую сводку (1-2 строки); если это анализ трат — только аналитику."
            action == "alert_list" ->
                " В reply верни текст ДАННЫХ ПОЛНОСТЬЮ КАК ЕСТЬ, без переписывания, вступлений и сокращений."
            action == "write_file" || action == "create_file" || action == "disk_download" ->
                " В reply кратко (1-2 строки) подтверди, что именно сделано."
            else -> ""
        }
        val planHint = if (planMode) {
            val current = PlanFlow.nextActionStep(planQ, planActionIndex)
            val completed = PlanFlow.completedActionIndex(planQ, planActionIndex, planStepTools)
            val pending = if (PlanFlow.stepSatisfied(current, planStepTools))
                PlanFlow.nextActionStep(planQ, completed + 1) else current
            " Выполняется многошаговый план. Следующий незавершённый шаг: «$pending». " +
                "Выполни именно его по данным инструмента; для write_file подготовь НОВОЕ полное содержимое с требуемым изменением, а не исходный текст без изменений. " +
                "Всегда возвращай chat с ПОЛНЫМ текстом для пользователя; подтверждение между шагами организует само приложение — ask_confirm вызывать НЕ нужно."
        } else ""
        return "ДАННЫЕ (результат инструмента $action; верни финальный JSON вида " +
            "{\"action\":\"chat\",\"reply\":\"...\"} с текстом для пользователя; reply не оставляй пустым.$planHint$extra):\n$data"
    }

    private suspend fun execTerminal(action: IntendedAction): String = when (action.action) {
        "open_app"  -> openApp(action.app ?: "", action.query?.toLongOrNull())
        "capability_rule" -> capabilityRule(action.query ?: "")
        "add_event" -> addEvent(action.title ?: "", action.whenStr ?: "")
        "navigate"  -> openMaps(action.address ?: "")
        "disk_delete" -> diskDeleteData(action.path ?: "")
        "disk_move" -> diskMoveData(action.from ?: "", action.to ?: "")
        "disk_publish" -> diskPublishData(action.path ?: "")
        "remember" -> {
            val fact = action.title?.trim().orEmpty()
            if (fact.isBlank()) "Что запомнить?"
            else {
                val p0 = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
                val next = MemoryStore.addFact(MemoryStore.load(p0), fact)
                MemoryStore.save(p0, next)
                "Запомнил: $fact"
            }
        }
        "forget" -> {
            val fact = action.title?.trim().orEmpty()
            if (fact.isBlank()) "Что забыть?"
            else {
                val p0 = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
                val next = MemoryStore.removeFact(MemoryStore.load(p0), fact)
                MemoryStore.save(p0, next)
                "Забыл то, что совпадает с «$fact»"
            }
        }
        "memory" -> {
            val p0 = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            val block = MemoryStore.buildMemoryBlock(MemoryStore.load(p0), loadAllThreads(p0))
            if (block.isBlank()) "Память пока пуста." else "Вот что я помню:\n\n$block"
        }
        else        -> action.reply ?: "Не понял (действие: ${action.action})"
    }

    suspend fun router(
        q: String, key: String, history: List<Msg>, mem: String, extra: String = "",
        initialStatus: String = "Думаю",
        onStatus: (String) -> Unit = {}, onDelta: (String) -> Unit = {},
        onFiles: (List<FileRef>) -> Unit = {},
        onTools: (Set<String>) -> Unit = {},
        onEvents: (List<EventInfo>) -> Unit = {},
        onTool: (String) -> Unit = {}
    ): String = runLoop(mainSystem(mem) + extra, history, q, key, initialStatus, onStatus, onDelta, onFiles, onTools, onEvents, onTool)

    suspend fun threadRouter(
        ev: EventInfo, q: String, key: String, history: List<Msg>, mem: String, extra: String = "",
        initialStatus: String = "Думаю",
        onStatus: (String) -> Unit = {}, onDelta: (String) -> Unit = {},
        onFiles: (List<FileRef>) -> Unit = {},
        onTools: (Set<String>) -> Unit = {},
        onEvents: (List<EventInfo>) -> Unit = {},
        onTool: (String) -> Unit = {}
    ): String = runLoop(threadSystem(ev, mem) + extra, history, q, key, initialStatus, onStatus, onDelta, onFiles, onTools, onEvents, onTool)

    // ─── Действия ───────────────────────────────────────────
    private fun installedApps(): List<String> =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.loadLabel(packageManager).toString() }

    private fun listApps(): String {
        val apps = installedApps()
        return "Установлено приложений: ${apps.size}\n" + apps.joinToString("\n") { "• $it" }
    }

    private fun installedPackageSet(): Set<String> =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()

    private fun labelForPackage(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun launchMainPackage(pkg: String): String? {
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            startActivity(launch)
            "Открыл: ${labelForPackage(pkg)}"
        }.getOrNull()
    }

    /** Открытие приложения по функции через локальный capability-роутер. */
    private fun openCapability(query: String): String {
        if (query.isBlank()) return "Не понял, какую функцию открыть"
        val catalog = CapabilityStore.loadCatalog(this)
        val rules = CapabilityStore.loadRules(getSharedPreferences(Prefs.NAME, MODE_PRIVATE))
        return when (val result = CapabilityRouter.resolve(query, installedPackageSet(), catalog, rules)) {
            is CapabilityResolution.DeepLink -> {
                val launched = runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.uri))
                        .setPackage(result.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }.getOrDefault(false)
                if (launched) "Открыл: ${labelForPackage(result.packageName)} — ${result.id}"
                else launchMainPackage(result.packageName)
                    ?: "Приложение ${labelForPackage(result.packageName)} не открылось"
            }
            is CapabilityResolution.MainApp -> launchMainPackage(result.packageName)
                ?: "Приложение ${labelForPackage(result.packageName)} не открылось"
            is CapabilityResolution.Ambiguous -> {
                val names = result.packages.map { labelForPackage(it) }.distinct()
                "Нужно выбрать приложение для «${result.id}»: ${names.joinToString(", ")}. Уточни, какое использовать."
            }
            is CapabilityResolution.NotFound ->
                "Не нашёл приложение для «$query» в capability-каталоге. Выбери подходящее из списка установленных или честно скажи, что его нет:\n" + readAppsData()
        }
    }

    /** Пользовательские prefer/exclude/reset правила поверх каталога. */
    private fun capabilityRule(query: String): String {
        val o = runCatching { JSONObject(query) }.getOrNull()
            ?: return "Для capability_rule нужен JSON: capability, package, mode=prefer|exclude|reset."
        val capability = o.optString("capability").trim()
        val packageQuery = o.optString("package").trim()
        val mode = o.optString("mode").trim().lowercase()
        val alias = o.optString("alias").trim()
        val catalog = CapabilityStore.loadCatalog(this)
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val current = CapabilityStore.loadRules(prefs)
        val id = catalog.firstOrNull { it.id.equals(capability, ignoreCase = true) }?.id
            ?: CapabilityRouter.capabilityId(capability, catalog, current)
            ?: return "Неизвестная функция «$capability»."
        val pkg = if (packageQuery.contains('.')) packageQuery
        else PushReply.packageByLabel(this, packageQuery) ?: return "Не нашёл приложение «$packageQuery»."
        if (pkg !in installedPackageSet()) return "Приложение ${labelForPackage(pkg)} не установлено."
        val excluded = current.excluded.toMutableMap()
        val preferred = current.preferred.toMutableMap()
        val aliases = current.aliases.toMutableMap()
        when (mode) {
            "prefer" -> {
                preferred[id] = pkg
                excluded[id] = excluded[id].orEmpty() - pkg
            }
            "exclude" -> {
                excluded[id] = excluded[id].orEmpty() + pkg
                if (preferred[id] == pkg) preferred.remove(id)
            }
            "reset" -> {
                preferred.remove(id)
                excluded.remove(id)
            }
            else -> return "Неизвестный mode «$mode»: используй prefer, exclude или reset."
        }
        if (alias.isNotBlank()) aliases[alias] = id
        CapabilityStore.saveRules(prefs, RoutingRules(preferred, excluded, aliases))
        return when (mode) {
            "prefer" -> "Для «$id» выбрано ${labelForPackage(pkg)}. Правило сохранено."
            "exclude" -> "${labelForPackage(pkg)} исключено для «$id». Правило сохранено."
            else -> "Правило для «$id» сброшено."
        }
    }

    private fun openApp(name: String, eventAt: Long? = null): String {
        if (eventAt != null) {
            val event = loadMessages(getSharedPreferences(Prefs.NAME, MODE_PRIVATE))
                .flatMap { listOfNotNull(it.event) + it.events }
                .firstOrNull { it.kind == "push" && it.at == eventAt }
            if (event != null) {
                PushReply.open(event, this)
                return "Запрошено открытие пуша: ${event.source}"
            }
            return "Не нашёл пуш с id=$eventAt в сохранённой истории"
        }
        if (name.isBlank()) return "Не понял, какое приложение открыть"
        val hit = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).firstOrNull { it.loadLabel(packageManager).toString().contains(name, true) }
        if (hit == null) return "Не нашёл приложение «$name» в списке"
        startActivity(packageManager.getLaunchIntentForPackage(hit.activityInfo.packageName))
        return "Открыл: ${hit.loadLabel(packageManager)}"
    }

    private fun addEvent(title: String, whenStr: String): String {
        startActivity(
            Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.Events.DESCRIPTION, whenStr)
        )
        return "Открыл календарь: «$title» ($whenStr) — проверь дату и сохрани"
    }

    private fun openMaps(address: String): String {
        if (address.isBlank()) return "Не понял, куда едем"
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
        )
        return "Открыл карты: маршрут до «$address»"
    }

    // ─── SMS-история и финансы ──────────────────────────────
    private fun canReadSms(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun smsRows(days: Int): List<Triple<String, String, Long>> {
        if (!canReadSms()) return emptyList()
        val since = System.currentTimeMillis() - days * 24L * 3600_000L
        val out = mutableListOf<Triple<String, String, Long>>()
        try {
            contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                "date >= ?",
                arrayOf(since.toString()),
                "date DESC"
            )?.use { c ->
                val ai = c.getColumnIndexOrThrow("address")
                val bi = c.getColumnIndexOrThrow("body")
                val di = c.getColumnIndexOrThrow("date")
                while (c.moveToNext()) {
                    out += Triple(c.getString(ai) ?: "?", c.getString(bi) ?: "", c.getLong(di))
                }
            }
        } catch (e: Exception) {
            Log.w("Dispatcher", "Чтение SMS inbox: ${e.message}")
        }
        return out
    }

    /** SMS из журнала событий чата (для watch-сборки: копятся с установки). */
    private fun eventSmsRows(): List<Triple<String, String, Long>> =
        loadMessages(getSharedPreferences(Prefs.NAME, MODE_PRIVATE))
            .mapNotNull { it.event }
            .filter { it.kind == "sms" }
            .map { Triple(it.source, it.text, it.at) }

    // ─── Инструменты чтения данных (исполняются локально, результат — модели) ───
    private fun readSmsData(query: String, onEvents: (List<EventInfo>) -> Unit): String {
        val allRows = (if (canReadSms()) smsRows(30) else eventSmsRows())
        val rows = EventFilter.filterSms(allRows, query)
        if (rows.isEmpty()) {
            return if (query.isBlank()) "SMS не найдено (проверь доступ к SMS-истории: «+» → «Настройки»)."
            else "SMS по фильтру «$query» не найдено."
        }
        // Модель и карточки получают один фильтрованный набор.
        onEvents(rows.take(20).map { EventInfo("sms", it.first, "", it.second.take(300), it.third) })
        val df = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val filterText = if (query.isBlank()) "" else " — фильтр «$query»"
        return "Последние SMS (${minOf(rows.size, 50)}$filterText):\n" +
            rows.take(50).joinToString("\n") {
                "[${df.format(Date(it.third))}] ${it.first}: ${it.second.take(160).replace("\n", " ")}"
            }
    }

    private fun readPushesData(query: String, onEvents: (List<EventInfo>) -> Unit): String {
        val pushes = loadMessages(getSharedPreferences(Prefs.NAME, MODE_PRIVATE))
            .mapNotNull { it.event }
            .filter { it.kind == "push" || it.kind == "watch" }
            .let { EventFilter.filterPushes(it, query) }
            .takeLast(20)
        if (pushes.isEmpty()) {
            return if (query.isBlank()) "Пушей пока не было." else "Пуши по фильтру «$query» не найдены."
        }
        // Модель и карточки получают один фильтрованный набор.
        onEvents(pushes)
        val df = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val filterText = if (query.isBlank()) "" else " — фильтр «$query»"
        return "Последние пуши$filterText (id нужен для открытия конкретного пуша):\n" + pushes.joinToString("\n") {
            val src = if (it.kind == "watch") "Walter: ${it.source}" else it.source
            "[id=${it.at}; pkg=${it.pkg.orEmpty()}; ${df.format(Date(it.at))}] $src: " +
                listOf(it.title, it.text).filter { x -> x.isNotBlank() }.joinToString(" — ").take(160)
        }
    }

    private fun readAppsData(): String {
        val apps = installedApps()
        return "Установленные приложения (${apps.size}):\n" + apps.joinToString("\n")
    }

    private suspend fun readCalendarData(q: String, onEvents: (List<EventInfo>) -> Unit): String {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val (start, end) = CalendarStore.parseRange(q)
        // выборка ВСЕЙ истории календаря (у пользователей события и за прошлые годы),
        // режем только на этапе показа — иначе «веб показывает, а приложение нет»
        val now = System.currentTimeMillis()
        val wideFrom = now - 10L * 365 * 24 * 3600_000L
        val wideTo = now + 2L * 365 * 24 * 3600_000L
        val events = mutableListOf<CalendarStore.CalEvent>()
        var sysError: String? = null

        // 1) системный календарь устройства (Google/Яндекс через синхронизацию)
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            try {
                contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    arrayOf(
                        CalendarContract.Events.TITLE,
                        CalendarContract.Events.DTSTART,
                        CalendarContract.Events.DTEND,
                        CalendarContract.Events.EVENT_LOCATION
                    ),
                    "${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DTEND} >= ?",
                    arrayOf(wideTo.toString(), wideFrom.toString()),
                    "${CalendarContract.Events.DTSTART} ASC"
                )?.use { c ->
                    val ti = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                    val ds = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                    val de = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                    val lo = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                    while (c.moveToNext()) {
                        events += CalendarStore.CalEvent(
                            c.getString(ti) ?: "(без названия)",
                            c.getLong(ds),
                            c.getLong(de),
                            c.getString(lo) ?: ""
                        )
                    }
                }
            } catch (e: Exception) {
                sysError = "Не удалось прочитать системный календарь: ${e.message}"
            }
        }

        // 2) Яндекс Календарь по CalDAV (несколько аккаунтов, если настроены)
        var caldavNote: String? = null
        val cdAccounts = CalDavAccounts.fromJson(prefs.getString(Prefs.KEY_CALDAV_ACCOUNTS, null))
        for (acc in cdAccounts) {
            try {
                val cdEvents = CalDavStore.fetchEvents(client, acc.url, acc.user, acc.pass, wideFrom, wideTo)
                val label = acc.label.ifBlank { acc.user.substringBefore('@') }
                events += cdEvents.map { it.copy(source = "Яндекс:$label") }
            } catch (e: Exception) {
                caldavNote = "Яндекс Календарь «${acc.label.ifBlank { acc.user.substringBefore('@') }}» недоступен: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        if (events.isEmpty()) {
            val hints = mutableListOf<String>()
            if (sysError != null) hints += sysError
            if (caldavNote != null) hints += caldavNote
            if (hints.isEmpty()) {
                return "Событий в календарях не найдено."
            }
            return hints.joinToString("\n")
        }
        // раскрываем повторяющиеся события (RRULE) во вхождения, дедуп по (название, начало)
        val expanded = CalendarStore.expandEvents(events, wideFrom, wideTo)
        val unique = expanded.distinctBy { it.title to it.start }
        val (inRange, upcoming, past) = CalendarStore.partitionRange(unique, start, end)
        val shown: List<CalendarStore.CalEvent> = when {
            inRange.isNotEmpty() -> inRange.take(40)
            upcoming.isNotEmpty() -> upcoming.take(20)
            else -> past.takeLast(20).reversed()
        }
        // события карточками, как почта/пуши/SMS — с веткой ответа прямо в чате
        onEvents(shown.map { e ->
            EventInfo(
                "calendar",
                e.source.ifBlank { "Календарь" },
                e.title,
                CalendarStore.formatEventWhen(e),
                e.start
            )
        })
        val header = when {
            inRange.isNotEmpty() -> null
            upcoming.isNotEmpty() -> "В запрошенном диапазоне событий нет — ближайшие будущие:"
            else -> "В запрошенном диапазоне событий нет — недавние события:"
        }
        var text = CalendarStore.formatEvents(shown, start, end, header)
        if (inRange.isEmpty() || unique.size > inRange.size) {
            text += "\n(Всего событий в календарях: ${unique.size})"
        }
        if (caldavNote != null) text += "\n$caldavNote"
        if (sysError != null) text += "\n$sysError"
        return text
    }

    private suspend fun readMailData(q: String, onEvents: (List<EventInfo>) -> Unit): String {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val accounts = MailAccounts.fromJson(prefs.getString(Prefs.KEY_MAIL_ACCOUNTS, null))
        if (accounts.isEmpty()) return "Почта не настроена: скажи пользователю выполнить команду «настрой почту» (IMAP-аккаунт с паролем приложения)."
        val count = q.trim().toIntOrNull()?.coerceIn(1, 20) ?: 10
        val per = maxOf(1, count / accounts.size)
        return withContext(Dispatchers.IO) {
            // карточки писем в чате: как пуши и SMS — с веткой ответа, без внешнего приложения
            val cardEvents = mutableListOf<EventInfo>()
            val parts = accounts.map { acc ->
                val res = runCatching { MailStore.fetch(acc.host, acc.port, acc.user, acc.pass, acc.ssl, per) }
                res.fold(
                    onSuccess = { items ->
                        cardEvents += items.map { m ->
                            EventInfo("mail", m.from, m.subject, m.body.take(400), m.at)
                        }
                        "### ${acc.user} (${acc.host})\n" + MailStore.formatItems(items)
                    },
                    onFailure = { e -> "### ${acc.user}: ОШИБКА — ${e.message ?: e.javaClass.simpleName}" }
                )
            }
            onEvents(cardEvents)
            "Почта (аккаунтов: ${accounts.size}, писем карточками: ${cardEvents.size}):\n\n" + parts.joinToString("\n\n")
        }
    }

    /** fetch_url: только прямой GET полного URL, без сервисных исключений. */
    private suspend fun fetchUrlData(q: String): String {
        val url = q.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Укажи полный HTTP/HTTPS URL в query."
        }
        return fetchAnyUrl(url)
    }

    /** Внутренний канал фоновых оповещений; к действию fetch_url отношения не имеет. */
    private suspend fun readTelegramChannelsData(q: String): String {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val qt = q.trim()
        val saved = TelegramChannels.channelsFromJson(prefs.getString(Prefs.KEY_TG_CHANNELS, null))
        val targets: List<String> = if (qt.isBlank()) {
            if (saved.isEmpty()) return "Каналов нет: добавь их в настройках оповещения или укажи @имя в правиле."
            saved.map { TelegramChannels.normalizeChannel(it) }.distinct().take(10)
        } else {
            listOf(TelegramChannels.normalizeChannel(qt))
        }
        val parts = mutableListOf<String>()
        targets.forEachIndexed { i, name ->
            parts += fetchChannel(name)
            if (i != targets.lastIndex) delay(120)
        }
        return if (targets.size > 1) {
            "Сводка по каналам (${targets.size}):\n\n" + parts.joinToString("\n\n")
        } else parts.firstOrNull() ?: ""
    }

    /** Один GET с таймаутом; null — таймаут/ошибка сети. */
    private suspend fun plainGet(url: String, timeoutMs: Long = 25_000): String? = withTimeoutOrNull(timeoutMs) {
        withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    .build()
            ).execute().use { r ->
                if (!r.isSuccessful) {
                    DebugLog.log("fetch-http", "HTTP ${r.code}: $url")
                    null
                } else {
                    val body = r.body?.string()
                    if (body.isNullOrBlank()) DebugLog.log("fetch-http", "пустое тело: $url")
                    body
                }
            }
        }.getOrElse { e ->
            DebugLog.log("fetch-http", "${e.javaClass.simpleName}: ${e.message ?: ""} — $url")
            null
        }
        }
    }

    /** Любая страница: прямой GET (VPN на стороне пользователя). */
    private suspend fun fetchAnyUrl(url: String): String {
        plainGet(url, 45_000)?.let { body ->
            val text = if (body.trimStart().startsWith("<")) WebSearch.htmlToText(body) else body
            if (text.isNotBlank()) return "Страница $url:\n" + text.take(2400)
        }
        return "Не удалось прочитать страницу $url (прямой доступ недоступен — проверь VPN/сеть)."
    }

    /** Канал: прямой GET t.me (VPN на стороне пользователя) с повторами — VPN мог ещё подключаться. */
    private suspend fun fetchChannel(name: String): String {
        repeat(3) { attempt ->
            plainGet("https://t.me/s/$name", 45_000)?.let { html ->
                val posts = TelegramChannels.parse(html)
                if (posts.isNotEmpty()) {
                    return "### @$name\n" + TelegramChannels.formatPosts(name, posts)
                }
            }
            if (attempt < 2) delay(3_000)
        }
        return "### @$name: не удалось получить (t.me недоступен напрямую — проверь, что VPN подключён, и повтори)."
    }

    private fun yandexApiKey(): String = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        .getString(Prefs.KEY_YANDEX_KEY, "") ?: ""

    /** Ключ геокодера: отдельный бесплатный ключ, фолбэк — поисковый ключ Яндекса. */
    private fun geoApiKey(): String {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        return (prefs.getString(Prefs.KEY_GEO_KEY, "") ?: "").ifBlank { yandexApiKey() }
    }

    /** URL API геокодера (настраивается командой «настрой карты»). */
    private fun geoApiUrl(): String = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        .getString(Prefs.KEY_GEO_URL, "https://geocode-maps.yandex.ru/v1/") ?: "https://geocode-maps.yandex.ru/v1/"

    /** Координаты адреса (широта, долгота) через геокодер; null — не найдено/ошибка. */
    private fun geocodeCoords(addr: String, key: String): Pair<Double, Double>? {
        val url = YandexMaps.buildGeocodeUrl(geoApiUrl(), key, addr, reverse = false, results = 1)
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        val root = org.json.JSONObject(body)
        val pos = root.optJSONObject("response")
            ?.optJSONObject("GeoObjectCollection")
            ?.optJSONArray("featureMember")
            ?.optJSONObject(0)
            ?.optJSONObject("GeoObject")
            ?.optJSONObject("Point")
            ?.optString("pos")
        val parts = pos?.split(" ") ?: return null
        if (parts.size < 2) return null
        val lon = parts[0].toDoubleOrNull() ?: return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        return lat to lon
    }

    private fun geocodeData(q: String): String {
        if (q.isBlank()) return "Не указан адрес (query)."
        val key = geoApiKey()
        if (key.isBlank()) return "Нет ключа геокодера: скажи пользователю выполнить команду «настрой карты»."
        return try {
            val url = YandexMaps.buildGeocodeUrl(geoApiUrl(), key, q, reverse = false)
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { r ->
                val body = r.body?.string() ?: ""
                if (!r.isSuccessful) {
                    "Геокодер вернул ошибку HTTP ${r.code}: " + body.take(200)
                } else YandexMaps.formatGeocode(body, q)
            }
        } catch (e: Exception) {
            "Не удалось выполнить геокодинг: ${e.message}"
        }
    }

    private fun routeInfoData(from: String, to: String, q: String): String {
        var fromA = from.trim()
        var toA = to.trim()
        if ((fromA.isBlank() || toA.isBlank()) && q.contains("|")) {
            val parts = q.split("|")
            if (fromA.isBlank()) fromA = parts[0].trim()
            if (toA.isBlank()) toA = parts[1].trim()
        }
        if (fromA.isBlank() || toA.isBlank()) return "Нужны обе точки маршрута: from и to (или query «откуда | куда»)."
        val key = yandexApiKey()
        if (key.isBlank()) return "Нет ключа Яндекс API: скажи пользователю выполнить команду «настрой поиск». Маршрутизация — платный сервис; бесплатно работает distance (расстояние по прямой)."
        return try {
            val c1 = geocodeCoords(fromA, key) ?: return "Не нашёл точку «$fromA» на карте."
            val c2 = geocodeCoords(toA, key) ?: return "Не нашёл точку «$toA» на карте."
            val url = "https://api.routing.yandex.net/v2/route?apikey=$key&waypoints=${c1.second},${c1.first}|${c2.second},${c2.first}&mode=driving"
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
            "Маршрут: $fromA → $toA\n" + YandexMaps.formatRoute(body)
        } catch (e: Exception) {
            "Не удалось построить маршрут: ${e.message}"
        }
    }

    private fun reverseGeocodeData(q: String): String {
        if (q.isBlank()) return "Не указаны координаты (query — широта,долгота)."
        val c = YandexMaps.parseCoords(q)
            ?: return "Не понял координаты «$q» (формат: широта,долгота)."
        val key = geoApiKey()
        if (key.isBlank()) return "Нет ключа геокодера: скажи пользователю выполнить команду «настрой карты»."
        return try {
            val url = YandexMaps.buildGeocodeUrl(geoApiUrl(), key, "${c.first},${c.second}", reverse = true)
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
            val addr = YandexMaps.formatReverse(body)
            if (addr.isBlank()) "По координатам ${c.first}, ${c.second} ничего не найдено."
            else "Координаты ${c.first}, ${c.second} — это: $addr"
        } catch (e: Exception) {
            "Не удалось выполнить обратный геокодинг: ${e.message}"
        }
    }

    private fun distanceData(from: String, to: String, q: String): String {
        var fromA = from.trim()
        var toA = to.trim()
        if ((fromA.isBlank() || toA.isBlank()) && q.contains("|")) {
            val parts = q.split("|")
            if (fromA.isBlank()) fromA = parts[0].trim()
            if (toA.isBlank()) toA = parts[1].trim()
        }
        if (fromA.isBlank() || toA.isBlank()) return "Нужны две точки: from и to (или query «откуда | куда»)."
        val key = geoApiKey()
        if (key.isBlank()) return "Нет ключа геокодера: скажи пользователю выполнить команду «настрой карты»."
        return try {
            val c1 = geocodeCoords(fromA, key) ?: return "Не нашёл точку «$fromA» на карте."
            val c2 = geocodeCoords(toA, key) ?: return "Не нашёл точку «$toA» на карте."
            val km = YandexMaps.haversineKm(c1.first, c1.second, c2.first, c2.second)
            val br = YandexMaps.bearingDeg(c1.first, c1.second, c2.first, c2.second)
            val kmS = String.format(java.util.Locale.US, "%.1f", km)
            val brS = br.toInt()
            "Расстояние по прямой: $fromA → $toA\n• дистанция: $kmS км\n" +
                "• направление: ${YandexMaps.compassPoint(br)} ($brS°)\n" +
                "• координаты: [${c1.first}, ${c1.second}] → [${c2.first}, ${c2.second}]"
        } catch (e: Exception) {
            "Не удалось посчитать расстояние: ${e.message}"
        }
    }

    private fun locationData(): String {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return "Нет доступа к местоположению: разреши в «+» → «Настройки» (право «Местоположение»)."
        val lm = getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
            ?: return "Служба местоположения недоступна."
        val loc = listOf(
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.NETWORK_PROVIDER,
            android.location.LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (loc == null) return "Не удалось определить местоположение (включи геолокацию и подожди, пока телефон её определит)."
        val acc = if (loc.hasAccuracy()) "±${loc.accuracy.toInt()} м" else "точность неизвестна"
        val df = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val base = "Текущее местоположение: ${loc.latitude}, ${loc.longitude} ($acc, определено ${df.format(Date(loc.time))})"
        val key = geoApiKey()
        if (key.isBlank()) return base
        val addr = runCatching { reverseAddress(loc.latitude, loc.longitude, key) }.getOrNull()
        return if (addr.isNullOrBlank()) base else "$base\nАдрес: $addr"
    }

    private fun reverseAddress(lat: Double, lon: Double, key: String): String {
        val url = YandexMaps.buildGeocodeUrl(geoApiUrl(), key, "$lat,$lon", reverse = true)
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        return YandexMaps.formatReverse(body)
    }

    private suspend fun readFilesData(onFiles: (List<FileRef>) -> Unit): String {
        if (!FileStore.hasAllFilesAccess()) return ALL_FILES_HINT
        val files = withContext(Dispatchers.IO) { FileStore.recentAll(30) }
        if (files.isEmpty()) return "Файлы не найдены."
        onFiles(files.map { FileRef(it.name, it.path, FileStore.kindOf(it.name), it.size) })
        val df = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        return "Недавние файлы:\n" + files.joinToString("\n") {
            "${it.name} [${it.path}] — ${FileStore.fmtSize(it.size)} [${df.format(Date(it.mtime))}]"
        }
    }

    private suspend fun readDirsData(onFiles: (List<FileRef>) -> Unit): String {
        if (!FileStore.hasAllFilesAccess()) return ALL_FILES_HINT
        val dirs = withContext(Dispatchers.IO) { FileStore.topDirs(FileStore.storageRoot()) }
        if (dirs.isEmpty()) return "Папки не найдены."
        onFiles(dirs.map { FileRef(it.name, it.path, "dir", 0L, isDir = true) })
        return "Папки в памяти устройства: " + dirs.joinToString(", ") { it.name }
    }

    private suspend fun readDirData(q: String, onFiles: (List<FileRef>) -> Unit): String {
        if (!FileStore.hasAllFilesAccess()) return ALL_FILES_HINT
        if (q.isBlank()) return "Не указана папка."
        val dir = withContext(Dispatchers.IO) { FileStore.resolveDir(FileStore.storageRoot(), q) }
            ?: return "Папка «$q» не найдена."
        val items = withContext(Dispatchers.IO) { FileStore.listDir(dir) }
        if (items.isEmpty()) return "Папка «${dir.name}» пуста."
        onFiles(items.map { FileRef(it.name, it.path, if (it.isDir) "dir" else FileStore.kindOf(it.name), it.size, it.isDir) })
        return "Папка ${dir.name} [${dir.absolutePath}]:\n" + items.joinToString("\n") {
            if (it.isDir) "[папка] ${it.name} [${it.path}]" else "${it.name} [${it.path}] — ${FileStore.fmtSize(it.size)}"
        }
    }

    private suspend fun findData(q: String, onFiles: (List<FileRef>) -> Unit): String {
        if (!FileStore.hasAllFilesAccess()) return ALL_FILES_HINT
        if (q.isBlank()) return "Не указано имя файла."
        val files = withContext(Dispatchers.IO) { FileStore.searchAll(q, 30) }
        if (files.isEmpty()) return "По запросу «$q» ничего не найдено."
        onFiles(files.map { FileRef(it.name, it.path, FileStore.kindOf(it.name), it.size) })
        return "Найдено ${files.size} (с полными путями):\n" + files.joinToString("\n") { "${it.name} [${it.path}] — ${FileStore.fmtSize(it.size)}" }
    }

    /** Поиск текста внутри текстовых файлов (папку или файл можно указать в path). */
    private suspend fun grepData(q: String, path: String?, onFiles: (List<FileRef>) -> Unit): String {
        if (!FileStore.hasAllFilesAccess()) return ALL_FILES_HINT
        if (q.isBlank()) return "Не указан текст для поиска."
        val root = if (!path.isNullOrBlank()) {
            val f = java.io.File(path)
            when {
                f.isDirectory -> f
                f.isFile -> f.parentFile ?: FileStore.storageRoot()
                else -> FileStore.storageRoot()
            }
        } else FileStore.storageRoot()
        val hits = withContext(Dispatchers.IO) { FileStore.grepText(root, q, 15) }
        if (hits.isEmpty()) return "В текстовых файлах ничего не найдено по запросу «$q»."
        onFiles(hits.map { (e, _) -> FileRef(e.name, e.path, "text", e.size) })
        return "Найдено в содержимом файлов (${hits.size}):\n" + hits.joinToString("\n") { (e, frag) ->
            "${e.name} [${e.path}]: …${frag}…"
        }
    }

    /** Полное содержимое файла — модели для нейро-редактирования. */
    private suspend fun readFileData(path: String): String {
        if (path.isBlank()) return "Не указан путь к файлу (path)."
        if (!FileStore.hasAllFilesAccess()) return ALL_FILES_HINT
        val f = withContext(Dispatchers.IO) { FileStore.resolveFilePath(path) }
            ?: return "Файл «$path» не найден. Уточни путь через find_files (он отдаёт полные пути)."
        val content = withContext(Dispatchers.IO) { FileStore.readText(f.absolutePath, 60_000) }
        return if (content.isBlank()) "Файл пуст или не читается."
        else "СОДЕРЖИМОЕ ФАЙЛА ${f.absolutePath}:\n$content"
    }

    /** Полная перезапись файла: тул пишет ровно то, что подготовила модель. */
    private suspend fun writeFileData(path: String, content: String): String {
        if (path.isBlank()) return "Не указан путь к файлу (path)."
        if (!FileStore.hasAllFilesAccess()) return ALL_FILES_HINT
        val f = withContext(Dispatchers.IO) { FileStore.resolveFilePath(path) }
            ?: return "Файл «$path» не найден. Уточни путь через find_files (он отдаёт полные пути)."
        return withContext(Dispatchers.IO) { FileStore.writeText(f.absolutePath, content) }
    }

    /** Создание нового файла: тул пишет ровно то, что подготовила модель. */
    private suspend fun createFileData(path: String, content: String): String {
        if (path.isBlank()) return "Не указан путь к файлу (path)."
        if (!FileStore.hasAllFilesAccess()) return ALL_FILES_HINT
        val target = if (java.io.File(path).isAbsolute) path else java.io.File(FileStore.storageRoot(), path).absolutePath
        return withContext(Dispatchers.IO) { FileStore.createText(target, content) }
    }

    // ─── Яндекс Диск (WebDAV-аккаунты; OAuth-токен — только публичные ссылки) ───

    private fun diskAccounts(): List<DiskAccount> =
        DiskAccounts.fromJson(getSharedPreferences(Prefs.NAME, MODE_PRIVATE).getString(Prefs.KEY_DISK_ACCOUNTS, null))

    private fun diskToken(): String = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        .getString(Prefs.KEY_DISK_TOKEN, "")?.trim().orEmpty()

    /** Содержимое папки на Диске (query — путь; пусто — корень); карточками, папку можно открыть тапом. */
    private suspend fun diskListData(q: String, onFiles: (List<FileRef>) -> Unit): String {
        val accs = diskAccounts()
        if (accs.isEmpty()) return DISK_HINT
        val path = YandexDisk.normalizePath(q)
        return withContext(Dispatchers.IO) {
            val allRefs = mutableListOf<FileRef>()
            val parts = accs.map { acc ->
                runCatching { DiskStore.list(client, acc, path) }.fold(
                    onSuccess = { items ->
                        allRefs += items.map {
                            FileRef(it.name, it.path, if (it.isDir) "dir" else FileStore.kindOf(it.name),
                                it.size, it.isDir, it.path, acc.user)
                        }
                        "### ${acc.user}\n" + YandexDisk.formatItems(path, items)
                    },
                    onFailure = { "### ${acc.user}: ОШИБКА — ${YandexDisk.describeWebdavError(it)}" }
                )
            }
            onFiles(allRefs)
            if (accs.size == 1) parts.first()
            else "Яндекс Диск (аккаунтов: ${accs.size}):\n\n" + parts.joinToString("\n\n")
        }
    }

    /** Поиск файла по имени на всём Диске; результат — карточками (файлы раскрываются тапом). */
    private suspend fun diskFilesData(q: String, onFiles: (List<FileRef>) -> Unit): String {
        val accs = diskAccounts()
        if (accs.isEmpty()) return DISK_HINT
        if (q.isBlank()) return "Не указано имя файла для поиска на Диске."
        return withContext(Dispatchers.IO) {
            val allRefs = mutableListOf<FileRef>()
            val parts = accs.map { acc ->
                runCatching { DiskStore.files(client, acc, q) }.fold(
                    onSuccess = { (items, truncated) ->
                        allRefs += items.map {
                            FileRef(it.name, it.path, FileStore.kindOf(it.name), it.size, false, it.path, acc.user)
                        }
                        "### ${acc.user}\n" + YandexDisk.formatMatches(q, items, truncated)
                    },
                    onFailure = { "### ${acc.user}: ОШИБКА — ${YandexDisk.describeWebdavError(it)}" }
                )
            }
            onFiles(allRefs)
            if (accs.size == 1) parts.first()
            else "Яндекс Диск (аккаунтов: ${accs.size}):\n\n" + parts.joinToString("\n\n")
        }
    }

    /** Полное содержимое файла с Диска — модели (текст и офисные форматы). */
    private suspend fun diskReadData(q: String): String {
        val acc = diskAccounts().firstOrNull() ?: return DISK_HINT
        val path = YandexDisk.normalizePath(q)
        if (path.isBlank()) return "Не указан файл на Диске (query)."
        return withContext(Dispatchers.IO) {
            runCatching {
                val meta = DiskStore.meta(client, acc, path)
                if (meta.isDir) throw DiskHttpException(400, "Это папка — укажи файл (список файлов даёт disk_list).")
                if (meta.size > YandexDisk.MAX_CHAT_READ) {
                    throw DiskHttpException(413, "Файл больше 10 МБ — в чат не читается. Скачай его на устройство (disk_download).")
                }
                val f = File(cacheDir, "disk_read/" + meta.name)
                DiskStore.download(client, acc, path, f)
                val content = FileStore.readText(f.absolutePath, 60_000)
                if (content.isBlank()) "Файл на Диске пуст или не читается (${meta.name})."
                else "СОДЕРЖИМОЕ ФАЙЛА С ДИСКА ${path}:\n$content"
            }.fold(
                onSuccess = { it },
                onFailure = { "Яндекс Диск: ${YandexDisk.describeWebdavError(it)}" }
            )
        }
    }

    /** Скачать файл с Диска на устройство — карточкой, как локальные файлы. */
    private suspend fun diskDownloadData(q: String, localDir: String?, onFiles: (List<FileRef>) -> Unit): String {
        val acc = diskAccounts().firstOrNull() ?: return DISK_HINT
        val path = YandexDisk.normalizePath(q)
        if (path.isBlank()) return "Не указан файл на Диске (query)."
        return withContext(Dispatchers.IO) {
            runCatching {
                val meta = DiskStore.meta(client, acc, path)
                if (meta.isDir) throw DiskHttpException(400, "Это папка — укажи файл (список файлов даёт disk_list).")
                val dir = if (localDir.isNullOrBlank()) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                } else {
                    FileStore.resolveDir(FileStore.storageRoot(), localDir)
                        ?: throw DiskHttpException(400, "Локальная папка «$localDir» не найдена на устройстве.")
                }
                dir.mkdirs()
                val dest = File(dir, meta.name)
                val existed = dest.exists()
                DiskStore.download(client, acc, path, dest)
                onFiles(listOf(FileRef(dest.name, dest.absolutePath, FileStore.kindOf(dest.name), dest.length())))
                "Скачано с Диска: ${dest.absolutePath}${if (existed) " (существующий файл перезаписан)" else ""}"
            }.fold(
                onSuccess = { it },
                onFailure = { "Яндекс Диск: ${YandexDisk.describeWebdavError(it)}" }
            )
        }
    }

    /** Создать/перезаписать файл на Диске: тул пишет ровно то, что подготовила модель. */
    private suspend fun diskWriteData(path: String, content: String): String {
        val acc = diskAccounts().firstOrNull() ?: return DISK_HINT
        val diskPath = YandexDisk.normalizePath(path)
        if (diskPath.isBlank()) return "Не указан путь на Диске (path)."
        if (content.isBlank()) return "Не указано содержимое (new)."
        val ext = diskPath.substringAfterLast('.', "").lowercase()
        val kind = if (ext.isBlank()) "text" else FileStore.kindOf(diskPath)
        return withContext(Dispatchers.IO) {
            runCatching {
                when (kind) {
                    "text" -> DiskStore.uploadBytes(client, acc, diskPath, content.toByteArray(Charsets.UTF_8))
                    "doc", "sheet", "slides", "pdf" -> {
                        // офисный формат: генерируем на устройстве (как create_file) и грузим байты
                        val tmp = File(cacheDir, "disk_write/" + File(diskPath).name)
                        tmp.parentFile?.mkdirs()
                        val r = FileStore.createText(tmp.absolutePath, content)
                        if (r.startsWith("Не удалось") || r.startsWith("Неподдерживаемое")) throw DiskHttpException(400, r)
                        DiskStore.uploadFile(client, acc, diskPath, tmp)
                    }
                    else -> throw DiskHttpException(400,
                        "Неподдерживаемый формат для записи на Диск: .$ext (доступны text/docx/xlsx/pptx/pdf).")
                }
                "Записано на Диск: $diskPath"
            }.fold(
                onSuccess = { it },
                onFailure = { "Яндекс Диск: ${YandexDisk.describeWebdavError(it)}" }
            )
        }
    }

    /** Загрузить локальный файл устройства на Диск. */
    private suspend fun diskUploadData(localPath: String, to: String): String {
        val acc = diskAccounts().firstOrNull() ?: return DISK_HINT
        if (localPath.isBlank()) return "Не указан локальный файл (path)."
        val f = withContext(Dispatchers.IO) { FileStore.resolveFilePath(localPath) }
            ?: return "Локальный файл «$localPath» не найден. Уточни путь через find_files (он отдаёт полные пути)."
        val rawTo = YandexDisk.normalizePath(to)
        val diskPath = when {
            rawTo.isBlank() -> "/" + f.name
            rawTo.endsWith("/") -> rawTo + f.name
            else -> rawTo
        }
        return withContext(Dispatchers.IO) {
            runCatching { DiskStore.uploadFile(client, acc, diskPath, f) }.fold(
                onSuccess = { "Загружено на Диск: $diskPath" },
                onFailure = { "Яндекс Диск: ${YandexDisk.describeWebdavError(it)}" }
            )
        }
    }

    /** Удалить файл/папку на Диске. */
    private suspend fun diskDeleteData(path: String): String {
        val acc = diskAccounts().firstOrNull() ?: return DISK_HINT
        val p = YandexDisk.normalizePath(path)
        if (p.isBlank()) return "Не указан путь на Диске (path)."
        return withContext(Dispatchers.IO) {
            runCatching { DiskStore.deleteResource(client, acc, p) }.fold(
                onSuccess = { "Удалил с Диска: $p" },
                onFailure = { "Яндекс Диск: ${YandexDisk.describeWebdavError(it)}" }
            )
        }
    }

    /** Переместить файл/папку на Диске. */
    private suspend fun diskMoveData(from: String, to: String): String {
        val acc = diskAccounts().firstOrNull() ?: return DISK_HINT
        val f = YandexDisk.normalizePath(from)
        val t = YandexDisk.normalizePath(to)
        if (f.isBlank()) return "Не указано, что перемещать (from)."
        if (t.isBlank()) return "Не указано, куда перемещать (to)."
        return withContext(Dispatchers.IO) {
            runCatching { DiskStore.moveResource(client, acc, f, t) }.fold(
                onSuccess = { "Переместил на Диске: $f → $t" },
                onFailure = { "Яндекс Диск: ${YandexDisk.describeWebdavError(it)}" }
            )
        }
    }

    /** Публичная ссылка на файл Диска (REST, OAuth-токен). */
    private suspend fun diskPublishData(path: String): String {
        val token = diskToken()
        if (token.isBlank()) return DISK_PUBLISH_HINT
        val p = YandexDisk.normalizePath(path)
        if (p.isBlank()) return "Не указан файл на Диске (path)."
        return withContext(Dispatchers.IO) {
            runCatching { DiskStore.publishResource(client, token, p) }.fold(
                onSuccess = { url -> "Публичная ссылка на $p:\n$url" },
                onFailure = { "Яндекс Диск: ${YandexDisk.describeError(it)}" }
            )
        }
    }

    /**
     * Полный текст системного промпта — генерируется приложением на устройстве
     * и отдаётся модели как данные. Это снимает «отказ раскрывать промпт»:
     * модель не выдаёт внутреннюю инструкцию, а печатает данные из инструмента.
     */
    private fun readSystemPromptData(): String {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val mem = MemoryStore.buildMemoryBlock(MemoryStore.load(prefs), loadAllThreads(prefs))
        val base = mainSystem(mem)
        return "СИСТЕМНЫЙ ПРОМПТ WALTER — ровно так он отправляется модели в каждом запросе. " +
            "Текст ниже напечатай пользователю полностью, как есть:\n\n" + base
    }

    /** Веб-поиск через DuckDuckGo Lite (без API-ключа). */
    private suspend fun webSearchData(q: String, onEngine: (String) -> Unit = {}): String {
        if (q.isBlank()) return "Не указан поисковый запрос."
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val yandexKey = prefs.getString(Prefs.KEY_YANDEX_KEY, "")?.trim().orEmpty()
        val yandexUrl = prefs.getString(Prefs.KEY_YANDEX_URL, YANDEX_SEARCH_URL) ?: YANDEX_SEARCH_URL

        fun empty(s: String): Boolean = s.contains("ничего не найдено") || s.startsWith("Поиск не удался") ||
            s.startsWith("Поиск не дал") || s.contains("лимит запросов") || s.contains("не завершился")

        // ищем сразу в двух движках и отдаём объединённый массив
        onEngine("Ищу в DuckDuckGo…")
        val ddg = ddgSearch(q)
        var yandex = ""
        var yandexNote = ""
        if (yandexKey.isNotBlank()) {
            onEngine("Ищу в Яндексе…")
            yandex = yandexSearch(q, yandexKey, yandexUrl)
        } else {
            yandexNote = "ключ Яндекса не задан (поиск только в DuckDuckGo)"
        }

        val ddgOk = !empty(ddg)
        val yandexOk = !empty(yandex)
        val parts = mutableListOf<String>()
        if (ddgOk) parts += "## DuckDuckGo\n" + ddg.trim()
        if (yandexOk) parts += "## Яндекс\n" + yandex.trim()
        if (parts.isEmpty()) {
            return when {
                yandexNote.isNotBlank() -> "Поиск не дал результатов в DuckDuckGo, а $yandexNote."
                else -> "Поиск не дал результатов ни в DuckDuckGo, ни в Яндексе."
            }
        }
        val head = "Веб-поиск (оба движка) «$q»:"
        val notes = mutableListOf<String>()
        if (!ddgOk) notes += "DuckDuckGo не ответил"
        if (!yandexOk) notes += if (yandexNote.isNotBlank()) yandexNote else "Яндекс не ответил"
        return (listOf(head) + parts + if (notes.isEmpty()) emptyList() else listOf("(не ответили: " + notes.joinToString("; ") + ")")).joinToString("\n\n")
    }

    private suspend fun ddgSearch(q: String): String {
        val url = "https://lite.duckduckgo.com/lite/?q=" + URLEncoder.encode(q, "UTF-8") + "&kl=ru-ru"
        val html = try {
            withContext(Dispatchers.IO) {
                client.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36")
                        .build()
                ).execute().use { r ->
                    if (r.isSuccessful) r.body?.string() ?: "" else ""
                }
            }
        } catch (e: Exception) {
            return "Поиск не удался: ${e.message}"
        }
        if (html.isBlank()) return "Поиск не дал ответа от сервера."
        return WebSearch.parseResults(html, q)
    }

    /** Яндекс Search API v2 (синхронный): ответ — JSON {"rawData": base64(XML)}. Rate limit повторяем. */
    private suspend fun yandexSearch(q: String, apiKey: String, baseUrl: String): String {
        // миграция: старый асинхронный URL упирался в rate limit — переходим на синхронный
        var url = baseUrl.ifBlank { YANDEX_SEARCH_URL }
        if (url.contains("searchAsync")) url = YANDEX_SEARCH_URL
        val body = JSONObject()
            .put("query", JSONObject().put("queryText", q).put("searchType", "SEARCH_TYPE_RU").put("page", "0"))
            .toString()
        var attempt = 0
        while (attempt < 3) {
            attempt++
            val respText = try {
                withContext(Dispatchers.IO) {
                    client.newCall(
                        Request.Builder()
                            .url(url)
                            .header("Authorization", "Api-Key $apiKey")
                            .header("Content-Type", "application/json")
                            .post(body.toRequestBody("application/json".toMediaType()))
                            .build()
                    ).execute().use { r -> r.body?.string() ?: "" }
                }
            } catch (e: Exception) {
                return "Поиск Яндекс не удался: ${e.message}"
            }
            val obj = try { JSONObject(respText) } catch (e: Exception) { null }
            if (obj != null && obj.optInt("code") == 8) {
                // rate limit — пауза и повтор
                delay(3000L * attempt)
                continue
            }
            val raw = obj?.optString("rawData") ?: ""
            if (raw.isEmpty()) return "Поиск Яндекс не дал данных (пустой rawData)."
            val xml = try {
                String(android.util.Base64.decode(raw, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                return "Поиск Яндекс не удался: не удалось декодировать ответ."
            }
            return YandexSearch.formatResults(xml, q)
        }
        return "Поиск Яндекс не удался: лимит запросов исчерпан (rate limit)."
    }

    // ─── Оповещения Walter: создание/настройка промптом (всё текстом в чате) ───

    private fun alertListData(): String {
        val p0 = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val disabled = WatchEngine.disabledFromJson(p0.getString(WatchEngine.KEY_DISABLED, null))
        return AlertStore.formatList(AlertStore.load(p0), disabled) { id -> WatchEngine.lastFire(p0, id) }
    }

    private fun alertCreateData(a: IntendedAction): String {
        val p0 = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val alerts0 = AlertStore.load(p0)
        val q = a.query ?: return "Не передан текст описания оповещения."
        val o = runCatching { org.json.JSONObject(q) }.getOrNull()
        val name = o?.optString("name")?.trim().orEmpty()
        val rule = o?.optString("rule")?.trim() ?: q.trim()
        if (name.isBlank()) return "Укажи имя оповещения (JSON-поля name, rule, channel, tool, every)."
        val existing = alerts0.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) return "Оповещение «${existing.name}» уже есть — используй alert_edit, чтобы изменить."
        AlertStore.add(
            p0,
            name = name,
            tools = o?.optString("tool", "").orEmpty(),
            instructions = rule,
            watchChannel = AlertStore.normalizeChannel(o?.optString("channel", "push") ?: "push"),
            watchTool = o?.optString("tool", "") ?: "",
            watchRule = rule,
            watchEvery = o?.optInt("every", 0) ?: 0
        )
        return "Оповещение «$name» создано: канал ${AlertStore.channelLabel(AlertStore.load(p0).first { it.name.equals(name, ignoreCase = true) })}, правило — $rule. Включено."
    }

    private fun alertEditData(a: IntendedAction): String {
        val p0 = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val alerts0 = AlertStore.load(p0)
        val q = a.query ?: return "Не переданы данные правки."
        val o = runCatching { org.json.JSONObject(q) }.getOrNull()
        val name = o?.optString("name")?.trim().orEmpty()
        val sk = alerts0.firstOrNull { it.name.contains(name, ignoreCase = true) }
            ?: return "Оповещение «$name» не найдено."
        val fields = listOf("newName", "rule", "channel", "tool", "every")
        val done = mutableListOf<String>()
        fields.forEach { f ->
            val v = o?.optString(f, null)
            if (v != null && v.isNotBlank()) {
                if (f == "newName") {
                    val collision = alerts0.any { it.id != sk.id && it.name.equals(v, ignoreCase = true) }
                    if (collision) return "Оповещение «$v» уже есть — выбери другое имя."
                }
                AlertStore.update(p0, sk.id, when (f) {
                    "newName" -> "name"
                    "rule" -> "watchRule"
                    "channel" -> "watchChannel"
                    "tool" -> "watchTool"
                    else -> "watchEvery"
                }, v)
                done += f
            }
        }
        if (done.isEmpty()) return "Укажи, что менять (поля newName, rule, channel, tool, every)."
        return "Оповещение «${sk.name}» обновлено: ${done.joinToString(", ")}."
    }

    private fun alertDeleteData(a: IntendedAction): String {
        val p0 = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val alerts0 = AlertStore.load(p0)
        val name = (a.query ?: "").trim()
        val sk = alerts0.firstOrNull { it.name.contains(name, ignoreCase = true) }
            ?: return "Оповещение «$name» не найдено."
        AlertStore.remove(p0, sk.id)
        return "Оповещение «${sk.name}» удалено."
    }

    private fun alertEnableData(a: IntendedAction): String {
        val p0 = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val alerts0 = AlertStore.load(p0)
        val q = (a.query ?: "").trim()
        val parts = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lastToken = parts.lastOrNull()?.lowercase()
        val firstToken = parts.firstOrNull()?.lowercase()
        val on = when {
            lastToken in setOf("on", "off") -> lastToken != "off"
            firstToken in setOf("on", "off") -> firstToken != "off"
            else -> true
        }
        val name = when {
            lastToken in setOf("on", "off") -> parts.dropLast(1).joinToString(" ")
            firstToken in setOf("on", "off") -> parts.drop(1).joinToString(" ")
            else -> q
        }.trim()
        val sk = alerts0.firstOrNull { it.name.contains(name, ignoreCase = true) }
            ?: return "Оповещение «$name» не найдено."
        var disabled = WatchEngine.disabledFromJson(p0.getString(WatchEngine.KEY_DISABLED, null))
        disabled = if (on) disabled - sk.id else disabled + sk.id
        p0.edit().putString(WatchEngine.KEY_DISABLED, WatchEngine.disabledToJson(disabled)).apply()
        /* сброс выключенных выполнит основной поток */
        return "Оповещение «${sk.name}» " + (if (on) "включено" else "выключено") + "."
    }



    private suspend fun execData(
        a: IntendedAction,
        onStatus: (String) -> Unit = {},
        onFiles: (List<FileRef>) -> Unit = {},
        onEvents: (List<EventInfo>) -> Unit = {}
    ): String = when (a.action) {
        "read_sms"    -> readSmsData(a.query ?: "", onEvents)
        "read_pushes" -> readPushesData(a.query ?: "", onEvents)
        "read_mail"   -> readMailData(a.query ?: "10", onEvents)
        "read_apps"   -> readAppsData()
        "open_capability" -> openCapability(a.query ?: "")
        "read_files"  -> readFilesData(onFiles)
        "read_dirs"   -> readDirsData(onFiles)
        "read_dir"    -> readDirData(a.query ?: "", onFiles)
        "find_files"  -> findData(a.query ?: "", onFiles)
        "read_system_prompt" -> readSystemPromptData()
        "search_history" -> withContext(Dispatchers.IO) {
            val main = loadMessages(getSharedPreferences(Prefs.NAME, MODE_PRIVATE))
            val withoutCurrent = if (main.lastOrNull()?.fromUser == true) main.dropLast(1) else main
            HistorySearch.format(
                withoutCurrent,
                loadAllThreads(getSharedPreferences(Prefs.NAME, MODE_PRIVATE)),
                a.query ?: ""
            )
        }
        "web_search" -> webSearchData(a.query ?: "") { st -> onStatus(st) }
        "read_calendar" -> readCalendarData(a.query ?: "неделя", onEvents)
        "fetch_url" -> fetchUrlData(a.query ?: "")
        "geocode" -> geocodeData(a.query ?: "")
        "route_info" -> routeInfoData(a.from ?: "", a.to ?: "", a.query ?: "")
        "reverse_geocode" -> reverseGeocodeData(a.query ?: "")
        "distance" -> distanceData(a.from ?: "", a.to ?: "", a.query ?: "")
        "location_now" -> locationData()
        "grep_files" -> grepData(a.query ?: "", a.path, onFiles)
        "read_file"  -> readFileData(a.path ?: "")
        "write_file" -> writeFileData(a.path ?: "", a.new ?: "")
        "create_file" -> createFileData(a.path ?: "", a.new ?: "")
        "disk_list"   -> diskListData(a.query ?: "", onFiles)
        "disk_files"  -> diskFilesData(a.query ?: "", onFiles)
        "disk_read"   -> diskReadData(a.query ?: "")
        "disk_download" -> diskDownloadData(a.query ?: "", a.path, onFiles)
        "disk_write"  -> diskWriteData(a.path ?: "", a.new ?: "")
        "disk_upload" -> diskUploadData(a.path ?: "", a.to ?: "")
        "alert_list"   -> alertListData()
        "alert_create" -> alertCreateData(a)
        "alert_edit"   -> alertEditData(a)
        "alert_delete" -> alertDeleteData(a)
        "alert_enable" -> alertEnableData(a)
        else          -> "Неизвестное действие чтения: ${a.action}"
    }

}

// ─── UI в стиле DeepSeek ────────────────────────────────────

/** Мигающий курсор стриминга. */
@Composable
private fun StreamCaret() {
    val alpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Restart)
    )
    Text("▍", color = DsBlue.copy(alpha = alpha), fontSize = 13.sp, fontFamily = Mono)
}

@Composable
private fun MessageRow(
    m: Msg,
    canRegenerate: Boolean,
    canRepeat: Boolean,
    streaming: Boolean,
    cardActions: CardActions,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onRepeat: () -> Unit
) {
    if (m.fromUser) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canRepeat) {
                IconButton(onClick = onRepeat, modifier = Modifier.size(28.dp)) {
                    Image(
                        painterResource(R.drawable.ic_refresh),
                        contentDescription = "Повторить запрос",
                        modifier = Modifier.size(14.dp),
                        colorFilter = ColorFilter.tint(DsIcon)
                    )
                }
            }
            SelectionContainer {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(DsUserBubble)
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                ) {
                    Text(MarkdownText.stripEmoji(m.text), color = DsText, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = Mono)
                }
            }
        }
    } else {
        if (m.helpKind == "tools") {
            ToolsHelpBlock(cardActions)
            return
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .animateContentSize(tween(150))
        ) {
            val legacy = splitTools(m.text)
            val (cleanText1, tools1) = if (m.toolsChain.isNotEmpty())
                (legacy.first to m.toolsChain)
            else legacy
            val thinking = streaming && (m.text.isBlank() || m.text.startsWith("Думаю"))
            val blinkAlpha by rememberInfiniteTransition().animateFloat(
                initialValue = 1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse)
            )
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(DsAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("W", color = DsBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                }
                Spacer(Modifier.width(8.dp))
                SelectionContainer {
                    Text(
                        text = MarkdownText.render(
                            when {
                                m.text.isBlank() -> "**Думаю**"
                                m.text.startsWith("Думаю") -> "**Думаю**"
                                m.text.startsWith("Готово") -> m.text.replaceFirst("Готово", "**Готово**")
                                else -> cleanText1
                            },
                            DsText, 13, 15, 14, Mono
                        ),
                        modifier = Modifier.weight(1f).alpha(if (thinking) blinkAlpha else 1f),
                        lineHeight = 21.sp
                    )
                }
                if (tools1.isNotEmpty() && streaming) ToolsChip(tools1)
            }
            if (tools1.isNotEmpty() && !streaming) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    ToolsChip(tools1)
                }
            }
            if (m.files.isNotEmpty()) {
                m.files.forEach { FileCardView(it, cardActions) }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { onCopy(m.text) }, modifier = Modifier.size(28.dp)) {
                    Image(
                        painterResource(R.drawable.ic_copy),
                        contentDescription = "Копировать",
                        modifier = Modifier.size(14.dp),
                        colorFilter = ColorFilter.tint(DsIcon)
                    )
                }
            }
        }
    }
}

/** Плавающий трекер задач: активные — со спиннером, завершённые — чипы с галочкой (тап → прокрутка). */
@Composable
private fun TasksOverlay(
    active: List<ActiveTask>,
    finished: List<ActiveTask>,
    entries: List<TaskEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTaskTap: (TaskEntry) -> Unit,
    onRepeat: (TaskEntry) -> Unit,
    onFinishedTap: (ActiveTask) -> Unit,
    onActiveTap: (ActiveTask) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (!expanded) {
            // единая иконка: пульсирует, пока что-то выполняется; тап — развернуть список
            val busy = entries.any { it.running } || active.isNotEmpty()
            val pulse = rememberInfiniteTransition().animateFloat(
                initialValue = 1f, targetValue = if (busy) 1.16f else 1f,
                animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
                label = "taskIcon"
            )
            Box(
                Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    }
                    .clip(CircleShape)
                    .background(DsBlue)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    entries.size.toString(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Mono
                )
            }
            return@Column
        }
        // строка «свернуть»
        Row(
            Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DsBlue)
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("свернуть", color = Color.White, fontSize = 10.sp, fontFamily = Mono)
        }
        // последние задачи (до 5): всегда на месте, статус + крутилка, тап — к задаче
        entries.forEach { e ->
            Row(
                Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DsBlue)
                    .clickable { onTaskTap(e) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (e.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${e.name} · выполняется",
                        color = Color.White, fontSize = 11.sp, fontFamily = Mono,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                } else if (e.done) {
                    Text("✓", color = Color(0xFF7DE29B), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        e.name,
                        color = Color.White, fontSize = 11.sp, fontFamily = Mono,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text("○", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${e.name} · шаг ${e.step}/${e.total}",
                        color = Color.White, fontSize = 11.sp, fontFamily = Mono,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Единый блок события: карточка с текстом, внутри — переписка с агентом. */

/** Список оповещений и их собственных настроек внешнего канала. */
@Composable
private fun AlertsHelpBlock(cardActions: CardActions) {
    val alerts = cardActions.alertsList()
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var ruleDraft by remember { mutableStateOf("") }
    var instructionsDraft by remember { mutableStateOf("") }
    var toolMenuId by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        if (alerts.isEmpty()) {
            Text("Оповещений нет. Создай их запросом в чате.", color = DsHint, fontSize = 12.sp, fontFamily = Mono)
        }
        alerts.forEach { alert ->
            val expanded = expandedId == alert.id
            val enabled = alert.id !in cardActions.watchDisabled()
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White)
                    .border(1.dp, if (expanded) DsBlue else DsBorder, RoundedCornerShape(13.dp))
                    .clickable {
                        if (expanded) {
                            expandedId = null
                        } else {
                            expandedId = alert.id
                            ruleDraft = alert.watchRule
                            instructionsDraft = alert.instructions
                        }
                    }
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(alert.name, color = DsText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
                        Text(
                            "канал: ${alert.watchChannel.ifBlank { "любой" }} · " + if (enabled) "включено" else "выключено",
                            color = DsHint,
                            fontSize = 10.sp,
                            fontFamily = Mono
                        )
                    }
                    Text(if (expanded) "▾" else "▸", color = DsHint, fontSize = 12.sp, fontFamily = Mono)
                }
                if (!expanded && WatchEngine.ruleOf(alert).isNotBlank()) {
                    Text(
                        WatchEngine.ruleOf(alert),
                        color = DsHint,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Text("Канал срабатывания", color = DsText, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("push" to "ПУШ", "sms" to "СМС", "tool" to "ФОН").forEach { (channel, label) ->
                            val selected = alert.watchChannel == channel
                            Box(
                                Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) DsBlue else DsSoftBg)
                                    .clickable {
                                        cardActions.updateAlert(alert.id, "watchChannel", channel)
                                        if (channel == "tool" && alert.watchEvery == 0) {
                                            cardActions.updateAlert(alert.id, "watchEvery", "15")
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(label, color = if (selected) Color.White else DsText, fontSize = 12.sp, fontFamily = Mono)
                            }
                        }
                    }
                    if (alert.watchChannel == "tool") {
                        Spacer(Modifier.height(8.dp))
                        Text("Инструмент фоновой проверки", color = DsText, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                        Box {
                            Text(
                                alert.watchTool.ifBlank { "(выбери инструмент)" },
                                color = if (alert.watchTool.isBlank()) DsHint else DsText,
                                fontSize = 12.sp,
                                fontFamily = Mono,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .border(1.dp, DsBorder, RoundedCornerShape(8.dp))
                                    .clickable { toolMenuId = alert.id }
                                    .padding(10.dp)
                            )
                            DropdownMenu(expanded = toolMenuId == alert.id, onDismissRequest = { toolMenuId = null }) {
                                cardActions.toolChoices().forEach { (id, _) ->
                                    DropdownMenuItem(
                                        text = { Text(id, fontSize = 12.sp, fontFamily = Mono) },
                                        onClick = {
                                            cardActions.updateAlert(alert.id, "watchTool", id)
                                            toolMenuId = null
                                        }
                                    )
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(5 to "5 мин", 15 to "15 мин", 30 to "30 мин", 60 to "1 ч", 180 to "3 ч").forEach { (minutes, label) ->
                                val selected = alert.watchEvery == minutes
                                Box(
                                    Modifier
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) DsBlue else DsSoftBg)
                                        .clickable { cardActions.updateAlert(alert.id, "watchEvery", minutes.toString()) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(label, color = if (selected) Color.White else DsText, fontSize = 12.sp, fontFamily = Mono)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = ruleDraft,
                        onValueChange = { ruleDraft = it },
                        label = { Text("Правило срабатывания") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        minLines = 2,
                        maxLines = 5,
                        textStyle = TextStyle(fontSize = 13.sp, color = DsText, fontFamily = Mono)
                    )
                    OutlinedTextField(
                        value = instructionsDraft,
                        onValueChange = { instructionsDraft = it },
                        label = { Text("Инструкции для фоновой проверки") },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        minLines = 2,
                        maxLines = 6,
                        textStyle = TextStyle(fontSize = 13.sp, color = DsText, fontFamily = Mono)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        RoundIconButton(R.drawable.ic_check, "Сохранить") {
                            cardActions.updateAlert(alert.id, "watchRule", ruleDraft)
                            cardActions.updateAlert(alert.id, "instructions", instructionsDraft)
                        }
                        RoundIconButton(if (enabled) R.drawable.ic_pause else R.drawable.ic_play,
                            if (enabled) "Выключить" else "Включить") {
                            cardActions.toggleWatch(alert.id)
                        }
                        RoundIconButton(R.drawable.ic_delete, "Удалить") {
                            cardActions.deleteAlert(alert.id)
                            expandedId = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsHelpBlock(cardActions: CardActions) {
    val cards = cardActions.toolsData()
    var expandedName by remember { mutableStateOf<String?>(null) }
    var editKey by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }
    var formKey by remember { mutableStateOf<String?>(null) }
    var formValues by remember { mutableStateOf(listOf<String>()) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        cards.forEach { c ->
            val expanded = expandedName == c.name
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White)
                    .border(1.dp, if (expanded) DsBlue else DsBorder, RoundedCornerShape(13.dp))
                    .clickable { expandedName = if (expanded) null else c.name }
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DsText,
                        fontFamily = Mono,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (expanded) "▾" else "▸", fontSize = 12.sp, color = DsHint, fontFamily = Mono)
                }
                if (expanded) {
                    c.settings.forEach { st ->
                        Spacer(Modifier.height(6.dp))
                        if (st.editKey != null && editKey == st.editKey && expandedName == c.name) {
                            Column {
                                OutlinedTextField(
                                    value = editValue,
                                    onValueChange = { editValue = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 12.sp, color = DsText, fontFamily = Mono)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                    RoundIconButton(R.drawable.ic_check, "Сохранить") {
                                        cardActions.saveToolSetting(st.editKey, editValue)
                                        editKey = null
                                    }
                                    RoundIconButton(R.drawable.ic_close, "Отмена") { editKey = null }
                                }
                            }
                        } else {
                            SelectionContainer {
                                Text(
                                    "${st.label}: ${st.value}",
                                    fontSize = 11.sp,
                                    color = DsText,
                                    fontFamily = Mono,
                                    lineHeight = 16.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (st.editKey != null) Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    editKey = st.editKey
                                                    editValue = st.value
                                                }
                                                .padding(2.dp)
                                            else Modifier.padding(2.dp)
                                        )
                                )
                            }
                        }
                    }
                    // форма добавления аккаунта
                    if (c.addForm.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        if (formKey == c.name) {
                            Column {
                                c.addForm.forEachIndexed { i, f ->
                                    OutlinedTextField(
                                        value = formValues.getOrElse(i) { "" },
                                        onValueChange = { v ->
                                            val next = formValues.toMutableList()
                                            while (next.size <= i) next.add("")
                                            next[i] = v
                                            formValues = next
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        placeholder = { Text(f.value, fontSize = 12.sp) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 12.sp, color = DsText, fontFamily = Mono)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RoundIconButton(R.drawable.ic_check, "Сохранить") {
                                        cardActions.addToolAccount(c.name, formValues)
                                        formKey = null
                                    }
                                    RoundIconButton(R.drawable.ic_close, "Отмена") { formKey = null }
                                }
                            }
                        } else {
                            RoundIconButton(R.drawable.ic_add, "Добавить аккаунт") {
                                formKey = c.name
                                formValues = c.addForm.map { "" }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: Int,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) DsBlue else DsSendDisabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )
    }
}

@Composable
private fun EventBlock(
    event: EventInfo,
    expanded: Boolean,    thread: List<Msg>,
    highlighted: Boolean,
    streaming: Boolean,
    cardActions: CardActions,
    onHeight: (Int) -> Unit,
    suggestions: List<String> = emptyList(),
    processing: Boolean = false,
    onProcess: () -> Unit = {},
    onSuggestion: (String) -> Unit = {},
    onHeaderClick: () -> Unit
) {
    val ctx = LocalContext.current
    // «Ответить» доступен: есть ссылка из пуша, пакет или приложение находится по имени (старые пуши)
    val canReply = remember(event.at, event.pi, event.pkg, event.source) {
        event.pi != null || event.pkg != null || PushReply.packageByLabel(ctx, event.source) != null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (highlighted) DsUserBubble else Color.White)
            .border(
                width = if (highlighted) 1.5.dp else 1.dp,
                color = if (highlighted) DsBlue else DsBorder,
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onHeaderClick)
            .onGloballyPositioned { onHeight(it.size.height) }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (tagBg, tagFg) = when (event.kind) {
                "sms" -> Color(0xFFE3F2E5) to Color(0xFF2F7A4D)
                "push" -> Color(0xFFE3ECFB) to Color(0xFF3B5F9E)
                "mail" -> Color(0xFFF1E9FB) to Color(0xFF6B4FA3)
                "calendar" -> Color(0xFFFBF0DC) to Color(0xFF8A6A1F)
                "watch" -> Color(0xFFF1F3F5) to Color(0xFF4B5159)
                else -> DsAccent to DsBlue
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(tagBg)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    when (event.kind) {
                        "sms" -> "SMS"
                        "mail" -> "ПОЧТА"
                        "calendar" -> "СОБЫТИЕ"
                        "watch" -> "АЛЕРТ"
                        else -> "ПУШ"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = tagFg,
                    fontFamily = Mono
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                (if (event.kind == "watch") "Уолтер: " else "") + MarkdownText.stripEmoji(event.source),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DsText,
                fontFamily = Mono,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(eventTime(event), fontSize = 10.sp, color = DsHint, fontFamily = Mono)
            Spacer(Modifier.width(6.dp))
            Image(
                painterResource(R.drawable.ic_chevron),
                contentDescription = if (expanded) "Свернуть" else "Раскрыть",
                modifier = Modifier.size(12.dp).rotate(if (expanded) 90f else 0f),
                colorFilter = ColorFilter.tint(DsHint)
            )
        }
        if (event.title.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                MarkdownText.stripEmoji(event.title),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DsText,
                fontFamily = Mono
            )
        }
        if (event.text.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = MarkdownText.render(event.text, DsText, 13, 14, 13, Mono),
                lineHeight = 20.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis
            )
        }
        // аннотация (например, категория SMS); для алертов prompt-продолжение не показываем
        if (event.kind != "watch") {
            event.note?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = DsBlue, fontFamily = Mono)
            }
        }
        // Единые иконочные действия для пушей, SMS и алертов.
        if (event.kind in setOf("push", "sms", "watch")) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (event.kind == "push" && canReply) {
                    RoundIconButton(R.drawable.ic_open_external, "Открыть", onClick = { cardActions.onReply(event) })
                }
                if (event.kind == "sms") {
                    RoundIconButton(R.drawable.ic_message, "Открыть сообщения", onClick = { cardActions.onReply(event) })
                }
                RoundIconButton(R.drawable.ic_actions, "Действия", onClick = onProcess)
            }
            if (processing) {
                Spacer(Modifier.height(4.dp))
                Text("Думаю", fontSize = 11.sp, color = DsHint, fontFamily = Mono)
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { prompt ->
                        Box(
                            Modifier
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DsBlue)
                                .border(1.dp, DsBlue, RoundedCornerShape(12.dp))
                                .clickable { onSuggestion(prompt) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                prompt,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = Mono,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        // карточки событий — статические объекты чата: веток-диалогов больше нет
    }
}

/** Сообщение внутри блока события. */
@Composable
private fun ThreadMsgRow(m: Msg, streaming: Boolean, cardActions: CardActions) {
    if (streaming && m.text.isBlank() && !m.fromUser) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("W", color = DsBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Spacer(Modifier.width(6.dp))
            StreamCaret()
        }
        return
    }
    if (m.fromUser) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            SelectionContainer {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DsUserBubbleDeep)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(MarkdownText.stripEmoji(m.text), color = DsText, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = Mono)
                }
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .animateContentSize(tween(150))
        ) {
            val legacy2 = splitTools(m.text)
            val (cleanText2, tools2) = if (m.toolsChain.isNotEmpty())
                legacy2.first to m.toolsChain else legacy2
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(DsAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("W", color = DsBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                }
                Spacer(Modifier.width(6.dp))
                SelectionContainer {
                    Text(
                        text = MarkdownText.render(cleanText2, DsText, 12, 14, 13, Mono),
                        modifier = Modifier.weight(1f),
                        lineHeight = 18.sp
                    )
                }
                if (tools2.isNotEmpty()) ToolsChip(tools2)
    
            }
            if (tools2.isNotEmpty() && !streaming) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    ToolsChip(tools2)
                }
            }
            if (m.files.isNotEmpty()) {
                m.files.forEach { FileCardView(it, cardActions) }
            }
        }
    }
}

/** Плашка-список инструментов справа от ответа: цветная, появляется с первым инструментом. */
@Composable
private fun ToolsChip(tools: List<String>) {
    Row(
        Modifier.padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEachIndexed { i, t ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DsUserBubble)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    t,
                    fontSize = 10.sp,
                    color = Color(0xFF3B5F9E),
                    fontFamily = Mono
                )
            }
            if (i < tools.lastIndex) {
                Text("→", fontSize = 10.sp, color = DsHint, fontFamily = Mono, modifier = Modifier.padding(horizontal = 3.dp))
            }
        }
    }
}

@Composable
private fun InputBar(
    liftWithChat: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onShowTools: () -> Unit,
    activeLabel: String?,
    clearEnabled: Boolean,
    onClearActive: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (liftWithChat) Modifier else Modifier.imePadding())
    ) {
        if (activeLabel != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "↩ Отвечаете в ветку: $activeLabel",
                    fontSize = 11.sp,
                    color = DsBlue,
                    fontFamily = Mono,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (clearEnabled) {
                    IconButton(onClick = onClearActive, modifier = Modifier.size(24.dp)) {
                        Image(
                            painterResource(R.drawable.ic_add),
                            contentDescription = "Выйти из ветки",
                            modifier = Modifier.size(14.dp).rotate(45f),
                            colorFilter = ColorFilter.tint(DsHint)
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(DsSoftBg)
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 140.dp),
                    maxLines = 5,
                    textStyle = TextStyle(fontSize = 14.sp, color = DsText, lineHeight = 20.sp, fontFamily = Mono),
                    cursorBrush = SolidColor(DsBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 40.dp),
                            contentAlignment = if (input.contains('\n')) Alignment.TopStart else Alignment.CenterStart
                        ) {
                            if (input.isEmpty()) {
                                Text(
                                    if (activeLabel != null) "Ответить…" else "Спроси меня…",
                                    color = DsHint,
                                    fontSize = 14.sp,
                                    fontFamily = Mono
                                )
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.width(6.dp))
                RoundIconButton(R.drawable.ic_send, "Отправить", enabled = input.isNotBlank(), onClick = onSend)
                Spacer(Modifier.width(6.dp))
                RoundIconButton(
                    R.drawable.ic_add,
                    if (menuOpen) "Закрыть меню" else "Меню",
                    modifier = Modifier.rotate(if (menuOpen) 45f else 0f),
                    onClick = onToggleMenu
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = DsHint,
        letterSpacing = 1.sp
    )
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = DsBorder, thickness = 1.dp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, action: () -> Unit, buttonText: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DsSoftBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DsText)
            Spacer(Modifier.height(2.dp))
            Text(
                if (granted) "выдано" else "не выдано",
                fontSize = 12.sp,
                color = if (granted) Color(0xFF3C7A3F) else Color(0xFFC0554C)
            )
        }
        if (!granted) {
            TextButton(onClick = action) { Text(buttonText, fontSize = 13.sp) }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ToolSettingCard(
    id: String,
    desc: String,
    rsshubUrl: String,
    corsUrl: String,
    onRsshubChange: (String) -> Unit,
    onCorsChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(DsSoftBg, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(id, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DsText, fontFamily = Mono, modifier = Modifier.weight(1f))
            Text(if (expanded) "▾" else "▸", fontSize = 12.sp, color = DsHint, fontFamily = Mono)
        }
        Text(desc, fontSize = 11.sp, color = DsHint, fontFamily = Mono, lineHeight = 15.sp)
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            when (id) {
                "fetch_url" -> {
                    Text("RSSHub URL (для RSS-каналов)", fontSize = 11.sp, color = DsHint)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = rsshubUrl,
                        onValueChange = onRsshubChange,
                        placeholder = { Text("https://rsshub.app") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("CORS-прокси (для t.me)", fontSize = 11.sp, color = DsHint)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = corsUrl,
                        onValueChange = onCorsChange,
                        placeholder = { Text("https://cors.lol/?url=") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "read_mail" -> Text("Почта настраивается в чате: «настрой почту» (IMAP-аккаунты).", fontSize = 11.sp, color = DsHint, lineHeight = 15.sp)
                "read_calendar" -> Text("Календари настраиваются в чате: «настрой календарь» (CalDAV-ссылки).", fontSize = 11.sp, color = DsHint, lineHeight = 15.sp)
                "disk_list", "disk_files", "disk_read", "disk_download", "disk_write", "disk_upload", "disk_delete", "disk_move", "disk_publish" ->
                    Text("Яндекс Диск настраивается в чате: «настрой диск» (логин + пароль приложения).", fontSize = 11.sp, color = DsHint, lineHeight = 15.sp)
                "route_info" -> Text("Маршрутизация настраивается в чате: «настрой маршруты» (ключ сервиса).", fontSize = 11.sp, color = DsHint, lineHeight = 15.sp)
                else -> Text("Параметры этого инструмента задаются в промпте.", fontSize = 11.sp, color = DsHint, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun LauncherSettingsBlock() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var isHome by remember { mutableStateOf(LauncherMode.isDefaultHome(ctx)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isHome = LauncherMode.isDefaultHome(ctx)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (isHome) "Walter выбран домашним приложением"
            else "Walter может работать как лончер Android",
            fontSize = 13.sp, color = DsText, fontFamily = Mono
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(DsBlue)
                .clickable {
                    DebugLog.log("launcher-mode", "открываю системный выбор Home")
                    ctx.startActivity(LauncherMode.homeIntent(ctx))
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painterResource(R.drawable.ic_home), contentDescription = "Сделать лончером",
                modifier = Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.White)
            )
            Spacer(Modifier.width(8.dp))
            Text("Сделать лончером", color = Color.White, fontSize = 13.sp, fontFamily = Mono)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "В режиме лончера пуши остаются в чате, а обычные системные уведомления скрываются. Постоянные уведомления не отключаются.",
            fontSize = 11.sp, color = DsHint, fontFamily = Mono, lineHeight = 16.sp
        )
    }
}

@Composable
private fun SettingsScreen(
    apiUrl: String,
    apiKey: String,
    onModelSave: (String, String) -> Unit,
    cardActions: CardActions,
    rsshubUrl: String,
    corsUrl: String,
    onRsshubChange: (String) -> Unit,
    onCorsChange: (String) -> Unit,
    smsGranted: Boolean,
    readSmsGranted: Boolean,
    pushAccess: Boolean,
    allFilesAccess: Boolean,
    calendarGranted: Boolean,
    onRequestCalendar: () -> Unit,
    locationGranted: Boolean,
    onRequestLocation: () -> Unit,
    onRequestSms: () -> Unit,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestReadSms: () -> Unit,
    onOpenPushSettings: () -> Unit,
    onRequestAllFiles: () -> Unit,
    onClose: () -> Unit
) {
    var urlInput by remember { mutableStateOf(apiUrl) }
    var keyInput by remember { mutableStateOf(apiKey) }
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(DsBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Image(
                    painterResource(R.drawable.ic_back),
                    contentDescription = "Назад",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(DsIcon)
                )
            }
            Text("Настройки", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DsText)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionHeader("Лончер")
            LauncherSettingsBlock()
            SectionHeader("Модель")
            Text("API URL", fontSize = 13.sp, color = DsHint)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text("https://api.deepseek.com/chat/completions") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text("Ключ API", fontSize = 13.sp, color = DsHint)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            RoundIconButton(R.drawable.ic_check, "Сохранить") { onModelSave(urlInput.trim(), keyInput.trim()) }

            // Оповещения настраиваются только в чате через alert_*; отдельной секции в настройках нет.
            SectionHeader("Инструменты")
            ToolsHelpBlock(cardActions)
            SectionHeader("Права")
            PermissionRow(
                "Уведомления Walter (оповещения)",
                notificationsGranted,
                onRequestNotifications,
                "Разрешить уведомления"
            )
            PermissionRow(
                "SMS в чат",
                smsGranted,
                onRequestSms,
                "Разрешить SMS"
            )
            PermissionRow(
                "Чтение SMS-истории (финансы)",
                readSmsGranted,
                onRequestReadSms,
                "Разрешить чтение SMS"
            )
            PermissionRow(
                "Доступ к уведомлениям (пуши)",
                pushAccess,
                onOpenPushSettings,
                "Открыть системные настройки"
            )
            PermissionRow(
                "Системный календарь (Google и др.)",
                calendarGranted,
                onRequestCalendar,
                "Разрешить календарь"
            )
            PermissionRow(
                "Местоположение (для «Где я»)",
                locationGranted,
                onRequestLocation,
                "Разрешить местоположение"
            )
            PermissionRow(
                "Все файлы",
                allFilesAccess,
                onRequestAllFiles,
                "Открыть системные настройки"
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
// ─── Карточки файлов в чате (снипеты, как в harness DeepSeek) ───

/** Открыть файл в подходящем приложении (через FileProvider). */
private fun openFile(ctx: android.content.Context, path: String) {
    try {
        var f = File(path)
        if (!f.exists()) {
            Toast.makeText(ctx, "Файл не найден", Toast.LENGTH_SHORT).show()
            return
        }
        var uri: Uri? = null
        try {
            uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        } catch (e: Exception) {
            uri = null
        }
        if (uri == null) {
            // файл вне корней FileProvider (например, другой носитель) — временная копия в cache
            val ext = f.extension.ifBlank { "bin" }
            val tmp = File(ctx.cacheDir, "open_${System.currentTimeMillis()}.$ext")
            f.copyTo(tmp, overwrite = true)
            uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", tmp)
        }
        val ext = f.extension.lowercase()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (FileStore.kindOf(f.name)) {
                "text" -> "text/plain"
                "image" -> "image/*"
                "video" -> "video/*"
                "apk" -> "application/vnd.android.package-archive"
                "pdf" -> "application/pdf"
                "doc" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "sheet" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "slides" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "application/octet-stream"
            }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = android.content.ClipData.newRawUri("file", uri)
        }
        val chooser = Intent.createChooser(intent, "Открыть файл")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Не удалось открыть: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/** Превью картинки с сэмплированием до разумного размера. */
private fun decodePreview(path: String): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
} catch (e: Exception) {
    null
}

/**
 * Карточка файла в чате:
 * - текст: тап раскрывает сниппет с прокруткой + кнопка «Открыть в приложении»;
 * - картинка: тап раскрывает превью + кнопка открыть;
 * - apk/video/прочее (исполняемые): открываются сразу.
 */
@Composable
private fun FileCardView(ref: FileRef, cardActions: CardActions) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var textSnippet by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val isDisk = ref.diskPath != null

    // Единая палитра приложения: все типы файлов — зелёный акцент на светло-зелёной подложке
    val label = when (ref.kind) {
        "text" -> "TXT"
        "image" -> "IMG"
        "apk" -> "APK"
        "video" -> "VID"
        "doc" -> "DOC"
        "sheet" -> "XLS"
        "slides" -> "PPT"
        "pdf" -> "PDF"
        "dir" -> "DIR"
        else -> "FILE"
    }
    val canExpand = ref.kind in setOf("text", "image", "doc", "sheet", "slides", "pdf")
    val opensDirectly = ref.kind in setOf("apk", "video", "other")

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, DsBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable {
                when {
                    // папка на Диске — раскрыть список внутри карточки
                    isDisk && ref.isDir ->
                        expanded = !expanded
                    // локальная папка в памяти устройства — раскрыть список внутри карточки
                    !isDisk && ref.kind == "dir" ->
                        expanded = !expanded
                    // исполняемые/видео с Диска — скачать на устройство (карточка станет локальной)
                    isDisk && opensDirectly -> {
                        if (!downloading) {
                            downloading = true
                            scope.launch {
                                val lp = cardActions.diskDownload(ref)
                                if (lp != null) cardActions.diskReplaced(ref, lp)
                                else downloading = false
                            }
                        }
                    }
                    opensDirectly -> openFile(ctx, ref.path)
                    else -> expanded = !expanded
                }
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (labelBg, labelFg) = when (ref.kind) {
                "dir" -> Color(0xFFFBF0DC) to Color(0xFF8A6A1F) // папка — янтарный
                "text" -> Color(0xFFE3ECFB) to Color(0xFF3B5F9E) // текст — синий
                "image" -> Color(0xFFF1E9FB) to Color(0xFF6B4FA3) // картинка — фиолетовый
                "apk" -> Color(0xFFE3F2E5) to Color(0xFF2F7A4D) // apk — зелёный
                "video" -> Color(0xFFFBE4E1) to Color(0xFF9E4436) // видео — красный
                "doc" -> Color(0xFFE3ECFB) to Color(0xFF3B5F9E) // docx — синий
                "sheet" -> Color(0xFFE3F2E5) to Color(0xFF2F7A4D) // xlsx — зелёный
                "slides" -> Color(0xFFFCE9D2) to Color(0xFF9A5B1F) // pptx — оранжевый
                "pdf" -> Color(0xFFFBE4E1) to Color(0xFF9E4436) // pdf — красный
                else -> DsAccent to DsBlue
            }
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(labelBg),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = labelFg, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ref.name, fontSize = 13.sp, color = DsText, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        ref.kind == "dir" && isDisk -> "папка на Диске"
                        ref.kind == "dir" -> "папка"
                        isDisk -> FileStore.fmtSize(ref.size) + " · Диск"
                        else -> FileStore.fmtSize(ref.size)
                    },
                    fontSize = 11.sp, color = DsHint, fontFamily = Mono
                )
            }
            if (canExpand || opensDirectly || ref.isDir) {
                Text(
                    if (expanded) "▾" else "▸",
                    fontSize = 12.sp, color = DsHint, fontFamily = Mono,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        if (expanded && !isDisk && ref.kind == "dir") {
            var dirItems by remember { mutableStateOf<List<FileEntry>?>(null) }
            LaunchedEffect(ref.path) {
                dirItems = withContext(Dispatchers.IO) {
                    runCatching { FileStore.listDir(java.io.File(ref.path)) }.getOrNull()
                }
            }
            dirItems?.let { items ->
                val txt = buildAnnotatedString {
                    items.forEachIndexed { i, it ->
                        if (i > 0) append(", ")
                        withStyle(SpanStyle(fontWeight = if (it.isDir) FontWeight.Bold else FontWeight.Normal)) {
                            append(it.name)
                        }
                    }
                }
                SelectionContainer {
                    Text(
                        txt.ifEmpty { buildAnnotatedString { append("(папка пуста)") } },
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        lineHeight = 16.sp,
                        color = DsText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
        if (expanded && isDisk && ref.isDir) {
            var dirItems by remember { mutableStateOf<List<FileRef>?>(null) }
            LaunchedEffect(ref.diskPath, ref.diskAcc) {
                dirItems = cardActions.diskListDir(ref)
            }
            dirItems?.let { items ->
                val sorted = items.sortedBy { !it.isDir }
                val txt = buildAnnotatedString {
                    sorted.forEachIndexed { i, it ->
                        if (i > 0) append(", ")
                        withStyle(SpanStyle(fontWeight = if (it.isDir) FontWeight.Bold else FontWeight.Normal)) {
                            append(it.name)
                        }
                    }
                }
                SelectionContainer {
                    Text(
                        txt.ifEmpty { buildAnnotatedString { append("(папка пуста)") } },
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        lineHeight = 16.sp,
                        color = DsText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
        if (expanded && canExpand) {
            when (ref.kind) {
                // офисные форматы — «офисный вид»: docx с форматированием, xlsx таблицей, pptx слайдами
                "doc", "sheet", "slides" -> {
                    var officePath by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(ref.path, ref.diskPath) {
                        officePath = if (isDisk) cardActions.diskFile(ref) else ref.path
                    }
                    officePath?.let { SelectionContainer { OfficeCardContent(it, ref.kind) } }
                }
                "image" -> {
                    if (preview == null) {
                        LaunchedEffect(ref.path, ref.diskPath) {
                            preview = if (isDisk) cardActions.diskPreview(ref)
                            else withContext(Dispatchers.IO) { decodePreview(ref.path) }
                        }
                    }
                    preview?.let { bmp ->
                        Image(
                            bmp,
                            contentDescription = "Превью файла",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                else -> {
                    if (textSnippet == null) {
                        LaunchedEffect(ref.path, ref.diskPath) {
                            textSnippet = if (isDisk) cardActions.diskSnippet(ref)
                            else withContext(Dispatchers.IO) { FileStore.readText(ref.path, 500_000) }
                        }
                    }
                    textSnippet?.let { sn ->
                        SelectionContainer {
                            Text(
                                sn.ifBlank { "(файл пуст или не читается)" },
                                fontSize = 11.sp,
                                fontFamily = Mono,
                                lineHeight = 16.sp,
                                color = DsText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DsBlue)
                    .clickable {
                        if (isDisk) {
                            if (!downloading) {
                                downloading = true
                                scope.launch {
                                    val lp = cardActions.diskDownload(ref)
                                    if (lp != null) cardActions.diskReplaced(ref, lp)
                                    else downloading = false
                                }
                            }
                        } else openFile(ctx, ref.path)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    if (isDisk) "Скачать на устройство" else "Открыть в приложении",
                    color = Color.White, fontSize = 12.sp, fontFamily = Mono
                )
            }
        }
    }
}


/** «Офисный вид» содержимого docx/xlsx/pptx внутри карточки файла (упрощённый рендер). */
@Composable
private fun OfficeCardContent(path: String, kind: String) {
    var blocks by remember(path, kind) { mutableStateOf<List<DocBlock>?>(null) }
    var sheets by remember(path, kind) { mutableStateOf<List<SheetView>?>(null) }
    var slides by remember(path, kind) { mutableStateOf<List<SlideView>?>(null) }
    var err by remember(path, kind) { mutableStateOf<String?>(null) }
    LaunchedEffect(path, kind) {
        withContext(Dispatchers.IO) {
            runCatching {
                when (kind) {
                    "doc" -> blocks = OfficeView.docxBlocks(path)
                    "sheet" -> sheets = OfficeView.xlsxSheets(path)
                    "slides" -> slides = OfficeView.pptxSlides(path)
                }
            }.onFailure { err = "Не удалось открыть: ${it.message ?: it.javaClass.simpleName}" }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        when (kind) {
            "doc" -> blocks?.forEach { b ->
                when (b) {
                    is DocBlock.Para -> DocxParaRow(b)
                    is DocBlock.Table -> GridTable(b.rows, headerBold = false)
                }
            }
            "sheet" -> sheets?.forEach { s ->
                Spacer(Modifier.height(6.dp))
                Text(s.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DsBlue, fontFamily = Mono)
                Spacer(Modifier.height(4.dp))
                GridTable(s.rows, headerBold = s.headerBold || s.rows.isNotEmpty())
            }
            "slides" -> slides?.forEachIndexed { i, sl -> SlideCardView(i + 1, sl) }
        }
        err?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 11.sp, color = DsHint, fontFamily = Mono, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Упрощённый вид: без картинок и сложной свёрстки. Полный вид — кнопка под карточкой.",
            fontSize = 10.sp, color = DsHint, fontFamily = Mono, lineHeight = 14.sp
        )
    }
}

/** Абзац docx с форматированием: заголовки крупнее, жирный/курсив, списки с «•». */
@Composable
private fun DocxParaRow(p: DocBlock.Para) {
    val scale = when (p.heading) {
        1 -> 1.5f
        2 -> 1.3f
        3 -> 1.15f
        else -> 1.0f
    }
    val base = 12f
    Text(
        buildAnnotatedString {
            if (p.bullet) append("• ")
            p.runs.forEach { r ->
                val fs = if (r.size > 1.0) (r.size.toFloat() * scale).sp else (base * scale).sp
                withStyle(
                    SpanStyle(
                        fontWeight = if (r.bold || p.heading > 0) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (r.italic) FontStyle.Italic else FontStyle.Normal,
                        fontSize = fs
                    )
                ) { append(r.text) }
            }
        },
        fontFamily = Mono,
        lineHeight = 18.sp,
        color = DsText,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

/** Таблица-сетка (xlsx или таблица docx): ячейки с границами, шапка жирным, прокрутка. */
@Composable
private fun GridTable(rows: List<List<String>>, headerBold: Boolean) {
    // ширина колонок единая по всей таблице — ячейки не «разъезжаются»
    val colCount = rows.maxOfOrNull { it.size } ?: 0
    val colWidths = (0 until colCount).map { ci ->
        val maxLen = rows.maxOf { r -> r.getOrNull(ci)?.length ?: 0 }
        ((maxLen.coerceIn(6, 26)) * 7).dp
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(0.5.dp, DsBorder, RoundedCornerShape(6.dp))
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        rows.forEachIndexed { ri, cells ->
            // дополняем строку пустыми ячейками — сетка прямоугольная, границы у всех клеток
            val padded = cells + List((colCount - cells.size).coerceAtLeast(0)) { "" }
            Row(Modifier.height(IntrinsicSize.Min)) {
                padded.forEachIndexed { ci, cell ->
                    Box(
                        Modifier
                            .width(colWidths[ci])
                            .fillMaxHeight()
                            .background(if (ri == 0) DsSoftBg else Color.White)
                            .border(0.5.dp, DsBorder)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            cell,
                            fontSize = 11.sp,
                            fontWeight = if (ri == 0 && headerBold) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = Mono,
                            color = DsText,
                            lineHeight = 15.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Слайд презентации: карточка с номером, заголовком и пунктами. */
@Composable
private fun SlideCardView(num: Int, sl: SlideView) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DsSoftBg)
            .border(1.dp, DsBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(5.dp)).background(DsBlue).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Слайд $num", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                sl.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = DsText,
                fontFamily = Mono,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
        sl.bullets.forEach { b ->
            Text(
                "• $b",
                fontSize = 11.sp,
                color = DsText,
                fontFamily = Mono,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
