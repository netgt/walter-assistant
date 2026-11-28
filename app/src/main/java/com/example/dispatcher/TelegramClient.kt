package com.example.dispatcher

import android.content.Context
import io.github.tdlibandroid.ktx.TdClient
import io.github.tdlibandroid.ktx.TdException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Чаты, история и файлы Telegram через авторизованный TDLib. Без t.me/WebView. */
object TelegramClient {
    data class ChatSummary(
        val id: Long,
        val title: String,
        val kind: String,
        val unread: Int,
        val lastText: String,
        val lastAt: Long
    )

    data class Attachment(
        val fileId: Int,
        val name: String,
        val mime: String,
        val size: Long
    )

    data class MessageSummary(
        val id: Long,
        val chatId: Long,
        val sender: String,
        val at: Long,
        val text: String,
        val attachments: List<Attachment>,
        val pending: Boolean = false,
        val body: String = ""
    )

    /** Текст карточки в чате: только тело и «Вложение: имена», без fileId/размеров/скобок. */
    fun cardText(m: MessageSummary): String {
        if (m.attachments.isEmpty()) return m.body
        val names = m.attachments.joinToString(", ") { it.name }
        val label = if (m.attachments.size > 1) "Вложения" else "Вложение"
        return (if (m.body.isBlank()) "" else m.body + "\n") + "$label: $names"
    }

    data class History(val chat: TdApi.Chat, val messages: List<MessageSummary>)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chatsCache = ConcurrentHashMap<Long, TdApi.Chat>()
    private val usersCache = ConcurrentHashMap<Long, TdApi.User>()
    private val observing = AtomicBoolean(false)
    private var observeJob: Job? = null

    private suspend fun ready(ctx: Context): TdClient {
        if (!TelegramAuth.isConfigured()) {
            throw IllegalStateException("Telegram не настроен в этой сборке: отсутствуют apiId/apiHash")
        }
        val td = TelegramAuth.client(ctx)
            ?: throw IllegalStateException("TDLib не инициализирован")
        td.awaitInit()
        val auth = td.send(TdApi.GetAuthorizationState())
        if (auth !is TdApi.AuthorizationStateReady) {
            throw IllegalStateException("Telegram не авторизован: Настройки → Telegram → номер, код и пароль 2FA")
        }
        observe(td)
        return td
    }

    private fun observe(td: TdClient) {
        if (!observing.compareAndSet(false, true)) return
        observeJob = scope.launch {
            td.updates.collect { update ->
                when (update) {
                    is TdApi.UpdateUser -> usersCache[update.user.id] = update.user
                    is TdApi.UpdateChatTitle -> chatsCache[update.chatId]?.let {
                        chatsCache[update.chatId] = it.apply { title = update.title }
                    }
                    is TdApi.UpdateChatLastMessage -> chatsCache[update.chatId]?.let {
                        chatsCache[update.chatId] = it.apply { lastMessage = update.lastMessage }
                    }
                    else -> Unit
                }
            }
        }
    }

    @Synchronized
    internal fun reset() {
        observeJob?.cancel()
        observeJob = null
        observing.set(false)
        chatsCache.clear()
        usersCache.clear()
    }

    private suspend fun chatIds(td: TdClient, limit: Int): List<Long> {
        val cached = try {
            td.send(TdApi.GetChats(TdApi.ChatListMain(), limit)).chatIds?.toList().orEmpty()
        } catch (e: TdException) {
            if (e.code == 404) emptyList() else throw e
        }
        if (cached.size >= limit) return cached
        try {
            td.send(TdApi.LoadChats(TdApi.ChatListMain(), limit))
        } catch (e: TdException) {
            if (e.code != 404) throw e // 404 = достигнут конец списка
        }
        val refreshed = td.send(TdApi.GetChats(TdApi.ChatListMain(), limit)).chatIds?.toList().orEmpty()
        return if (refreshed.isNotEmpty()) refreshed else cached
    }

    private suspend fun allChats(td: TdClient, limit: Int = 200): List<TdApi.Chat> =
        chatIds(td, limit.coerceIn(1, 200)).mapNotNull { id ->
            runCatching { td.send(TdApi.GetChat(id)) }.getOrNull()?.also { chatsCache[id] = it }
        }

    suspend fun chats(ctx: Context, filter: String = "", limit: Int = 50): List<ChatSummary> {
        val td = ready(ctx)
        val needle = filter.trim().lowercase()
        return allChats(td, 200).asSequence()
            .filter { needle.isBlank() || it.title.lowercase().contains(needle) }
            .take(limit.coerceIn(1, 100))
            .map { chatSummary(it) }
            .toList()
    }

    private fun chatSummary(chat: TdApi.Chat): ChatSummary = ChatSummary(
        id = chat.id,
        title = chat.title,
        kind = chatKind(chat),
        unread = chat.unreadCount,
        lastText = chat.lastMessage?.let { contentText(it.content).take(180) }.orEmpty(),
        lastAt = (chat.lastMessage?.date ?: 0).toLong() * 1000
    )

    internal fun chatKind(chat: TdApi.Chat): String = when (val type = chat.type) {
        is TdApi.ChatTypePrivate, is TdApi.ChatTypeSecret -> "личный"
        is TdApi.ChatTypeBasicGroup -> "группа"
        is TdApi.ChatTypeSupergroup -> if (type.isChannel) "канал" else "группа"
        else -> "чат"
    }

    private suspend fun resolveChat(td: TdClient, reference: String): TdApi.Chat {
        val ref = reference.trim()
        require(ref.isNotBlank()) { "Укажи chat: id, @username или точное название" }
        if (ref.lowercase() in setOf("me", "saved", "saved messages", "избранное")) {
            val me = td.send(TdApi.GetMe())
            return td.send(TdApi.CreatePrivateChat(me.id, false))
        }
        ref.toLongOrNull()?.let { id ->
            return runCatching { td.send(TdApi.GetChat(id)) }.getOrNull()
                ?: throw IllegalArgumentException("Чат с id $id не найден")
        }
        val installed = allChats(td, 200)
        val exact = installed.filter { it.title.equals(ref.removePrefix("@"), true) }
        if (exact.size == 1) return exact.first()
        if (exact.size > 1) throw IllegalArgumentException("Несколько чатов с таким названием; укажи числовой id")
        val partial = installed.filter { it.title.contains(ref.removePrefix("@"), true) }
        if (partial.size == 1) return partial.first()
        if (partial.size > 1) {
            val variants = partial.take(8).joinToString("; ") { "${it.title} (${it.id})" }
            throw IllegalArgumentException("Название неоднозначно: $variants")
        }
        val username = ref.removePrefix("@").trim('/').substringAfterLast('/')
        if (username.matches(Regex("[A-Za-z0-9_]{4,}"))) {
            return runCatching { td.send(TdApi.SearchPublicChat(username)) }.getOrNull()
                ?: throw IllegalArgumentException("Публичный чат @$username не найден или недоступен")
        }
        throw IllegalArgumentException("Чат «$ref» не найден; сначала вызови tg_chats")
    }

    suspend fun history(ctx: Context, reference: String, count: Int = 20): History {
        val td = ready(ctx)
        val chat = resolveChat(td, reference)
        val target = count.coerceIn(1, 100)
        val collected = LinkedHashMap<Long, TdApi.Message>()
        var cursor = 0L
        var noProgress = 0
        while (collected.size < target && noProgress < 2) {
            val raw = td.send(TdApi.GetChatHistory(chat.id, cursor, 0, target - collected.size, false))
            val page = raw.messages.orEmpty()
            if (page.isEmpty()) break
            val before = collected.size
            page.forEach { collected[it.id] = it }
            val oldest = page.lastOrNull()?.id ?: break
            noProgress = if (collected.size == before || oldest == cursor) noProgress + 1 else 0
            cursor = oldest
        }
        val messages = collected.values.sortedBy { it.date }.map { messageSummary(td, chat, it) }
        return History(chat, messages)
    }

    private suspend fun messageSummary(td: TdClient, chat: TdApi.Chat, message: TdApi.Message): MessageSummary {
        val attachments = attachments(message.content)
        return MessageSummary(
            id = message.id,
            chatId = chat.id,
            sender = senderName(td, message),
            at = message.date.toLong() * 1000,
            text = contentText(message.content),
            attachments = attachments,
            pending = message.sendingState is TdApi.MessageSendingStatePending,
            body = bodyText(message.content)
        )
    }

    private suspend fun senderName(td: TdClient, message: TdApi.Message): String {
        if (message.isOutgoing) return "Вы"
        return when (val sender = message.senderId) {
            is TdApi.MessageSenderUser -> {
                val user = usersCache[sender.userId]
                    ?: runCatching { td.send(TdApi.GetUser(sender.userId)) }.getOrNull()
                        ?.also { usersCache[sender.userId] = it }
                if (user != null) "${user.firstName} ${user.lastName}".trim().ifBlank { "user ${sender.userId}" }
                else "user ${sender.userId}"
            }
            is TdApi.MessageSenderChat -> runCatching { td.send(TdApi.GetChat(sender.chatId)) }
                .getOrNull()?.title ?: "chat ${sender.chatId}"
            else -> "Telegram"
        }
    }

    internal fun bodyText(content: TdApi.MessageContent?): String {
        if (content == null) return ""
        return when (content) {
            is TdApi.MessageText -> content.text?.text.orEmpty()
            is TdApi.MessageDocument -> content.caption?.text.orEmpty()
            is TdApi.MessagePhoto -> content.caption?.text.orEmpty()
            is TdApi.MessageVideo -> content.caption?.text.orEmpty()
            is TdApi.MessageAudio -> content.caption?.text.orEmpty()
            is TdApi.MessageVoiceNote -> content.caption?.text.orEmpty()
            is TdApi.MessageAnimation -> content.caption?.text.orEmpty()
            else -> ""
        }
    }

    /** Сырой текст для модели: тело + вложения с fileId/размером (нужны для tg_download). */
    internal fun contentText(content: TdApi.MessageContent?): String {
        if (content == null) return ""
        val body = bodyText(content)
        val files = attachments(content)
        if (files.isEmpty()) return body
        val filesText = files.joinToString("; ") {
            "${it.name} (fileId=${it.fileId}, ${it.size} B)"
        }
        return (if (body.isBlank()) "" else "$body\n") + "[Вложение: $filesText]"
    }

    internal fun attachments(content: TdApi.MessageContent?): List<Attachment> {
        if (content == null) return emptyList()
        fun file(file: TdApi.File?, name: String, mime: String): List<Attachment> {
            if (file == null) return emptyList()
            return listOf(Attachment(file.id, name, mime, maxOf(file.size, file.expectedSize)))
        }
        return when (content) {
            is TdApi.MessageDocument -> file(content.document?.document,
                content.document?.fileName ?: "document", content.document?.mimeType ?: "application/octet-stream")
            is TdApi.MessagePhoto -> {
                val size = content.photo?.sizes.orEmpty().maxByOrNull { maxOf(it.photo.size, it.photo.expectedSize) }
                file(size?.photo, "photo-${size?.photo?.id ?: 0}.jpg", "image/jpeg")
            }
            is TdApi.MessageVideo -> file(content.video?.video,
                content.video?.fileName ?: "video.mp4", content.video?.mimeType ?: "video/mp4")
            is TdApi.MessageAudio -> file(content.audio?.audio,
                content.audio?.fileName ?: "audio", content.audio?.mimeType ?: "application/octet-stream")
            is TdApi.MessageVoiceNote -> file(content.voiceNote?.voice,
                "voice-${content.voiceNote?.voice?.id ?: 0}.ogg", content.voiceNote?.mimeType ?: "audio/ogg")
            is TdApi.MessageAnimation -> file(content.animation?.animation,
                content.animation?.fileName ?: "animation.mp4", content.animation?.mimeType ?: "video/mp4")
            else -> emptyList()
        }
    }

    private suspend fun sendContent(
        td: TdClient,
        chat: TdApi.Chat,
        content: TdApi.InputMessageContent
    ): TdApi.Message = coroutineScope {
        var localId = 0L
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(60_000) {
                td.updates.filterIsInstance<TdApi.UpdateMessageSendSucceeded>()
                    .first { localId != 0L && it.oldMessageId == localId }.message
            }
        }
        val sent = td.send(TdApi.SendMessage(chat.id, null, null, null, null, content))
        localId = sent.id
        if (sent.sendingState !is TdApi.MessageSendingStatePending) {
            waiter.cancel()
            sent
        } else {
            waiter.await() ?: sent
        }
    }

    suspend fun sendText(ctx: Context, reference: String, text: String): MessageSummary {
        require(text.isNotBlank()) { "Текст сообщения пуст" }
        val td = ready(ctx)
        val chat = resolveChat(td, reference)
        var sent: TdApi.Message? = null
        text.chunked(4000).forEach { chunk ->
            val content = TdApi.InputMessageText(TdApi.FormattedText(chunk, null), null, true)
            sent = sendContent(td, chat, content)
        }
        return messageSummary(td, chat, sent ?: throw IllegalStateException("Сообщение не отправлено"))
    }

    suspend fun sendFile(ctx: Context, reference: String, path: String, caption: String = ""): MessageSummary {
        val local = File(path)
        require(local.isFile) { "Файл не найден: $path" }
        val td = ready(ctx)
        val chat = resolveChat(td, reference)
        val document = TdApi.InputDocument(TdApi.InputFileLocal(local.absolutePath), null, false)
        val content = TdApi.InputMessageDocument(document, TdApi.FormattedText(caption.take(1000), null))
        val sent = sendContent(td, chat, content)
        return messageSummary(td, chat, sent)
    }

    suspend fun download(
        ctx: Context,
        reference: String,
        messageId: Long = 0,
        fileId: Int = 0,
        requestedName: String = "",
        destinationDir: String
    ): File {
        val td = ready(ctx)
        val chat = resolveChat(td, reference)
        require(!chat.hasProtectedContent) { "Владелец чата запретил сохранение содержимого" }
        var id = fileId
        var name = requestedName
        if (messageId != 0L) {
            val message = td.send(TdApi.GetMessage(chat.id, messageId))
            require(message.canBeSaved) { "Владелец сообщения запретил его сохранение" }
            val attachment = attachments(message.content).firstOrNull {
                (fileId == 0 || it.fileId == fileId) &&
                    (requestedName.isBlank() || it.name.equals(requestedName, true))
            } ?: throw IllegalArgumentException("В сообщении $messageId нет подходящего вложения")
            id = attachment.fileId
            if (name.isBlank()) name = attachment.name
        }
        if (id == 0 && requestedName.isNotBlank()) {
            val raw = td.send(TdApi.GetChatHistory(chat.id, 0, 0, 100, false))
            val attachment = raw.messages.orEmpty().asSequence()
                .filter { it.canBeSaved }
                .flatMap { attachments(it.content).asSequence() }
                .firstOrNull { it.name.equals(requestedName, true) }
                ?: throw IllegalArgumentException("Вложение «$requestedName» не найдено в последних 100 сообщениях")
            id = attachment.fileId
            name = attachment.name
        }
        require(id != 0) { "Укажи fileId или messageId с вложением" }
        if (messageId == 0L) {
            // fileId локален для текущей TDLib-сессии: подтверждаем принадлежность чату.
            val raw = td.send(TdApi.GetChatHistory(chat.id, 0, 0, 100, false))
            val attachment = raw.messages.orEmpty().asSequence()
                .filter { it.canBeSaved }
                .flatMap { attachments(it.content).asSequence() }
                .firstOrNull { it.fileId == id }
                ?: throw IllegalArgumentException("fileId $id не найден в последних 100 сообщениях чата; вызови tg_history заново")
            if (name.isBlank()) name = attachment.name
        }
        val downloaded = withTimeout(180_000) {
            td.send(TdApi.DownloadFile(id, 16, 0, 0, true))
        }
        val sourcePath = downloaded.local?.path.orEmpty()
        require(sourcePath.isNotBlank() && downloaded.local?.isDownloadingCompleted == true) {
            "TDLib не завершил скачивание файла $id"
        }
        val source = File(sourcePath)
        require(source.isFile) { "Скачанный файл не найден: $sourcePath" }
        val allowedRoot = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "walter-telegram"
        ).canonicalFile
        val targetDir = File(destinationDir).canonicalFile
        require(targetDir.path == allowedRoot.path || targetDir.path.startsWith(allowedRoot.path + File.separator)) {
            "Скачивание разрешено только в Download/walter-telegram"
        }
        if (!targetDir.exists()) require(targetDir.mkdirs()) { "Не удалось создать папку загрузки" }
        val targetName = safeFileName(name.ifBlank { source.name }, "telegram-$id")
        val target = uniqueTarget(targetDir, targetName)
        val temp = File(targetDir, target.name + ".part")
        try {
            source.inputStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
            require(temp.renameTo(target)) { "Не удалось сохранить скачанный файл" }
        } finally {
            if (temp.exists()) temp.delete()
        }
        return target
    }

    internal fun uniqueTarget(dir: File, name: String): File {
        val clean = safeFileName(name)
        var candidate = File(dir, clean)
        if (!candidate.exists()) return candidate
        val base = candidate.nameWithoutExtension
        val ext = candidate.extension.let { if (it.isBlank()) "" else ".$it" }
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, "$base ($index)$ext")
            index++
        }
        return candidate
    }

    internal fun safeFileName(raw: String, fallback: String = "telegram-file"): String {
        val cleaned = File(raw).name.replace(Regex("[\\x00-\\x1f/\\\\]"), "_").trim()
        return cleaned.ifBlank { fallback }.take(180)
    }

    fun formatChats(items: List<ChatSummary>): String {
        if (items.isEmpty()) return "Чаты не найдены."
        return "Telegram-чаты (${items.size}):\n" + items.joinToString("\n") { chat ->
            val last = if (chat.lastText.isBlank()) "" else " | ${chat.lastText}"
            "id=${chat.id} | ${chat.kind} | ${chat.title} | непрочитано=${chat.unread}$last"
        }
    }

    fun formatHistory(history: History): String {
        val header = "Чат: ${history.chat.title} | id=${history.chat.id} | ${chatKind(history.chat)} | сообщений=${history.messages.size}"
        if (history.messages.isEmpty()) return "$header\nСообщений нет."
        val df = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        return header + "\n" + history.messages.joinToString("\n\n") { message ->
            "[${df.format(Date(message.at))}] ${message.sender} | id=${message.id}\n${message.text}"
        }
    }
}
