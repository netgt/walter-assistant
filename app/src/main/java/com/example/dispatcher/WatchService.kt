package com.example.dispatcher

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Ловит ВСЕ пуши приложений (кроме своих) и передаёт их в чат. */
class WatchService : NotificationListenerService() {

    companion object {
        // живые уведомления шторки: ключ pkg|title|text → contentIntent.
        // PendingIntent с биндерами не маршалится (pi-pack падает), поэтому после
        // перезапуска приложения ссылку ищем здесь, пока уведомление висит.
        val activeIntents = java.util.concurrent.ConcurrentHashMap<String, PendingIntent>()

        fun findActive(pkg: String, title: String, text: String): PendingIntent? {
            if (pkg.isBlank()) return null
            val key = "$pkg|${title.take(60)}|${text.take(60)}"
            activeIntents[key]?.let { return it }
            val candidates = activeIntents.entries.filter { it.key.startsWith("$pkg|") }
            if (candidates.isEmpty()) return null
            // Тот же диалог/письмо выбираем только при однозначном совпадении заголовка.
            val sameTitle = candidates.filter {
                val parts = it.key.split('|', limit = 3)
                parts.getOrNull(1).orEmpty().equals(title.take(60), ignoreCase = true)
            }
            if (sameTitle.size == 1) return sameTitle.single().value
            // Если у приложения одно живое уведомление, его ссылка однозначна даже при изменённом тексте.
            if (candidates.size == 1) return candidates.single().value
            val sameText = candidates.filter {
                val savedText = it.key.split('|', limit = 3).getOrNull(2).orEmpty()
                text.isNotBlank() && (savedText.startsWith(text.take(40)) || text.startsWith(savedText.take(40)))
            }
            return if (sameText.size == 1) sameText.single().value else null
        }

        private fun keyOf(pkg: String, title: String, text: String) = "$pkg|${title.take(60)}|${text.take(60)}"
    }

    private val lastSeen = HashMap<String, Long>()

    /** После смахивания из recents просим систему заново связать NLS при следующем запуске. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(this, WatchService::class.java))
                DebugLog.log("watch-service", "onTaskRemoved: запрошен rebind NLS")
            }.onFailure {
                DebugLog.log("watch-service", "onTaskRemoved rebind: ${it.message ?: it.javaClass.simpleName}")
            }
        }
    }

    private fun alreadyProcessed(sbn: StatusBarNotification, text: String): Boolean {
        val id = sbn.key + "|" + sbn.postTime + "|" + text.hashCode()
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val known = id in NotificationIds.fromJson(prefs.getString(Prefs.KEY_PUSH_IDS, null))
        if (known) DebugLog.log("push-skip", "уже обработан: ${sbn.packageName}")
        return known
    }

    private fun markProcessed(sbn: StatusBarNotification, text: String) {
        val id = sbn.key + "|" + sbn.postTime + "|" + text.hashCode()
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val next = NotificationIds.add(prefs.getString(Prefs.KEY_PUSH_IDS, null), id)
        prefs.edit().putString(Prefs.KEY_PUSH_IDS, next).apply()
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val n = sbn.notification ?: return
        // не следим за собой
        if (sbn.packageName == packageName) return

        val flags = n.flags
        val isGroupSummary = flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0
        val ongoing = flags and android.app.Notification.FLAG_ONGOING_EVENT != 0
        val suppressSystem = LauncherMode.isDefaultHome(this)

        val extras = n.extras
        val title = PushFilter.pushTitle(
            extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: "",
            extras.getCharSequence(android.app.Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
        )
        val textLines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
            ?.map { it?.toString() ?: "" }?.toList() ?: emptyList()
        val messages = messagingLines(extras)
        val text = PushFilter.fallbackText(PushFilter.pushText(
            extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: "",
            extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: "",
            textLines,
            messages,
            extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
                ?: extras.getCharSequence(android.app.Notification.EXTRA_INFO_TEXT)?.toString()
                ?: extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        ), n.tickerText?.toString().orEmpty())
        if (title.isBlank() && text.isBlank()) {
            DebugLog.log("push-skip", "пустые title/text: ${sbn.packageName}, flags=$flags")
            return
        }
        if (alreadyProcessed(sbn, text)) return
        // мусор: счётчики «1 сообщение от Max», групповые сводки, постоянные уведомления.
        // Одиночная содержательная group summary без детей допускается (типичный случай почты).
        val useSummary = PushFilter.useGroupSummary(isGroupSummary, hasDetailedChild(sbn), title, text)
        if (!useSummary && PushFilter.shouldSkip(title, text, isGroupSummary, ongoing)) {
            Log.d("Dispatcher", "Push skipped (filter): $title | ${text.take(60)}")
            if (suppressSystem && !ongoing) {
                runCatching { cancelNotification(sbn.key) }
                DebugLog.log("launcher-mode", "скрыто отфильтрованное уведомление: ${sbn.packageName}")
            }
            return
        }

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        // дедупликация: одинаковый пуш чаще чем раз в 3 секунды — не спамим
        val now = System.currentTimeMillis()
        val key = "${sbn.packageName}|$title|$text"
        val prev = lastSeen[key]
        if (prev != null && now - prev < 3000) return
        lastSeen[key] = now
        if (lastSeen.size > 300) lastSeen.clear()

        Log.d("Dispatcher", "Push: $appLabel | $title | ${text.take(60)}")
        // Сначала действие «Ответить»: оно ведёт в конкретный чат/письмо, а не на главный экран.
        val replyAction = n.actions?.firstOrNull {
            it.title?.toString()?.contains("ответ", ignoreCase = true) == true
        }?.actionIntent
        val openIntent = replyAction ?: n.contentIntent
            ?: n.actions?.firstOrNull { it.actionIntent != null }?.actionIntent
        if (openIntent != null) {
            activeIntents[keyOf(sbn.packageName, title, text)] = openIntent
        }
        // кнопка «Открыть»: только contentIntent; кнопки уведомления — только если его нет.
        // (Кнопки вроде «Прочитано» — одноразовые и «съедают» тап без открытия диалога:
        // раньше они подмешивались к contentIntent, и открыть получалось с N-й попытки.)
        val intents = listOfNotNull(openIntent)
        val distinct = intents.distinct().take(4)
        val packed = distinct.mapNotNull { PushReply.pendingIntentToB64(it) }
        val piB64 = packed.joinToString("|").ifBlank { null }
        // живые ссылки в памяти процесса — откроют карточку, даже если упаковка падает
        PushReply.registerLive("push|$appLabel|$now", distinct)
        DebugLog.log(
            "push-struct",
            "app=$appLabel content=${if (n.contentIntent != null) "есть" else "нет"} actions=${n.actions?.size ?: 0} " +
                "titles=${n.actions?.map { it.title ?: "-" }?.joinToString(",") ?: "-"} " +
                "open=${if (replyAction != null) "действие «Ответить»" else "contentIntent"} " +
                "packed=${packed.size}/${distinct.size}"
        )
        EventHub.post(
            EventInfo("push", appLabel, title, text, now, pi = piB64, pkg = sbn.packageName),
            this
        )
        markProcessed(sbn, text)
        // умные алерты: журнал событий + мгновенная оценка следилок по пушам
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        WatchEngine.logEvent(prefs, WatchEvent("push", appLabel, text, now))
        val alerts = AlertStore.load(prefs)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                DebugLog.log("watch-eval", "пуш из «$appLabel»: текст ${text.take(60)}")
                val fired = WatchEngine.evaluateForEvent(prefs, client, alerts, WatchEvent("push", appLabel, text, now))
                DebugLog.log("watch-eval", "сработало оповещений: ${fired.size}")
                fired.forEach { (alert, text, prompt) -> WatchEngine.deliver(this@WatchService, alert, text, prompt) }
            }
        }
        if (suppressSystem && !ongoing) {
            runCatching { cancelNotification(sbn.key) }
            DebugLog.log("launcher-mode", "скрыто системное уведомление после сохранения в чат: $appLabel")
        }
    }

    /** Сообщения из MessagingStyle («android.messages»): последние 3, «кто: текст». */
    private fun messagingLines(extras: android.os.Bundle): List<String> {
        val arr = runCatching {
            @Suppress("DEPRECATION")
            extras.getParcelableArray("android.messages")
        }.getOrNull() ?: return emptyList()
        val lines = mutableListOf<String>()
        for (p in arr) {
            val m = p as? Notification.MessagingStyle.Message ?: continue
            val sender = m.sender?.toString()?.trim().orEmpty()
            val t = m.text?.toString()?.trim().orEmpty()
            if (t.isBlank()) continue
            lines += if (sender.isBlank() || t.startsWith(sender)) t else "$sender: $t"
        }
        return lines.takeLast(3)
    }

    private fun notificationText(extras: android.os.Bundle): String {
        val textLines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
            ?.map { it?.toString() ?: "" }?.toList() ?: emptyList()
        return PushFilter.pushText(
            extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: "",
            extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: "",
            textLines,
            messagingLines(extras),
            extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
                ?: extras.getCharSequence(android.app.Notification.EXTRA_INFO_TEXT)?.toString()
                ?: extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        )
    }

    private fun hasDetailedChild(sbn: StatusBarNotification): Boolean = activeNotifications.orEmpty().any { other ->
        other.key != sbn.key && other.packageName == sbn.packageName &&
            other.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0
    }

    /** Смахивание/отмена уведомления — ссылка больше не нужна. */
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        val n = sbn.notification ?: return
        val extras = n.extras
        val title = PushFilter.pushTitle(
            extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: "",
            extras.getCharSequence(android.app.Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
        )
        val text = notificationText(extras)
        activeIntents.remove(keyOf(sbn.packageName, title, text))
    }

    /** После (пере)подключения слушателя — синхронизируем живые уведомления шторки. */
    override fun onListenerConnected() {
        super.onListenerConnected()
        activeIntents.clear()
        val current = activeNotifications.orEmpty().toList()
        DebugLog.log("watch-service", "onListenerConnected: активных уведомлений=${current.size}")
        for (sbn in current) {
            val n = sbn.notification ?: continue
            val extras = n.extras
            val title = PushFilter.pushTitle(
                extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: "",
                extras.getCharSequence(android.app.Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
            )
            val text = notificationText(extras)
            val openIntent = n.actions?.firstOrNull {
                it.title?.toString()?.contains("ответ", ignoreCase = true) == true
            }?.actionIntent ?: n.contentIntent
                ?: n.actions?.firstOrNull { it.actionIntent != null }?.actionIntent
            if (openIntent != null) {
                activeIntents[keyOf(sbn.packageName, title, text)] = openIntent
            }
        }
        // После остановки слушателя новые уведомления могли появиться без нас:
        // публикуем их один раз, persistent key+postTime исключает повторы при следующих reconnect.
        current.forEach { sbn -> onNotificationPosted(sbn) }
    }
}
