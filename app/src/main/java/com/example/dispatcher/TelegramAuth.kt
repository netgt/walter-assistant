package com.example.dispatcher

import android.content.Context
import io.github.tdlibandroid.ktx.TdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/** Первый TDLib-контур Walter: вход по телефону, коду и паролю 2FA. */
object TelegramAuth {
    data class UiState(val status: String = "UNKNOWN", val message: String = "")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: TdClient? = null
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun isConfigured(): Boolean =
        BuildConfig.TG_API_ID != 0 && BuildConfig.TG_API_HASH.isNotBlank()

    @Synchronized
    private fun ensureClient(ctx: Context): TdClient? {
        if (!isConfigured()) {
            _state.value = UiState("ERROR", "В этой сборке не заданы Telegram apiId/apiHash.")
            return null
        }
        client?.let { return it }
        return runCatching {
            val created = TdClient(
                filesDir = ctx.filesDir.absolutePath + "/tdlib",
                apiId = BuildConfig.TG_API_ID,
                apiHash = BuildConfig.TG_API_HASH
            )
            created.init()
            client = created
            scope.launch {
                created.updates.collect { update ->
                    if (update is TdApi.UpdateAuthorizationState) {
                        applyState(update.authorizationState)
                    }
                }
            }
            scope.launch {
                runCatching { created.awaitInit() }
                    .onFailure { _state.value = UiState("ERROR", it.message ?: "Ошибка инициализации TDLib") }
                runCatching { created.send(TdApi.GetAuthorizationState()) }
                    .onSuccess { applyState(it) }
                    .onFailure { _state.value = UiState("ERROR", it.message ?: "Ошибка TDLib") }
            }
            created
        }.onFailure {
            _state.value = UiState("ERROR", it.message ?: "TDLib недоступен")
        }.getOrNull()
    }

    private fun applyState(state: TdApi.AuthorizationState) {
        _state.value = when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> UiState("PHONE", "Введи номер телефона")
            is TdApi.AuthorizationStateWaitCode -> UiState("CODE", "Введи код из Telegram/SMS")
            is TdApi.AuthorizationStateWaitPassword -> UiState("PASSWORD", "Введи пароль 2FA")
            is TdApi.AuthorizationStateReady -> UiState("READY", "Telegram подключён")
            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing,
            is TdApi.AuthorizationStateClosed -> {
                client = null
                TelegramClient.reset()
                UiState("LOGGED_OUT", "Сессия Telegram закрыта")
            }
            is TdApi.AuthorizationStateWaitEmailAddress,
            is TdApi.AuthorizationStateWaitEmailCode -> UiState("ERROR", "Telegram просит email — пока поддержан только вход по телефону")
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> UiState("ERROR", "QR-вход пока не включён; используй телефон")
            else -> UiState("UNKNOWN", state.javaClass.simpleName)
        }
    }

    fun refresh(ctx: Context) {
        ensureClient(ctx)
    }

    /** Общий TDLib-клиент для чатов/файлов; инициализация и авторизация остаются здесь. */
    internal fun client(ctx: Context): TdClient? = ensureClient(ctx)

    fun startPhone(ctx: Context, phone: String) {
        val td = ensureClient(ctx) ?: return
        scope.launch {
            runCatching {
                td.awaitInit()
                td.send(TdApi.SetAuthenticationPhoneNumber(phone.trim(), null))
            }.onFailure { _state.value = UiState("ERROR", it.message ?: "Не удалось отправить номер") }
        }
    }

    fun submitCode(code: String) {
        val td = client ?: return
        scope.launch {
            runCatching { td.send(TdApi.CheckAuthenticationCode(code.trim())) }
                .onFailure { _state.value = UiState("ERROR", it.message ?: "Неверный код") }
        }
    }

    fun submitPassword(password: String) {
        val td = client ?: return
        scope.launch {
            runCatching { td.send(TdApi.CheckAuthenticationPassword(password)) }
                .onFailure { _state.value = UiState("ERROR", it.message ?: "Неверный пароль") }
        }
    }

    fun logout() {
        val td = client ?: return
        scope.launch {
            runCatching { td.send(TdApi.LogOut()) }
                .onFailure { _state.value = UiState("ERROR", it.message ?: "Ошибка выхода") }
        }
    }
}
