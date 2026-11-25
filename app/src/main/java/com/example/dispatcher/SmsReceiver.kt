package com.example.dispatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Ловит входящие SMS, аннотирует финансовые и передаёт в чат. */
class SmsReceiver : BroadcastReceiver() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.KEY_WATCH_SMS, true)) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            null
        }
        if (messages.isNullOrEmpty()) return

        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val sender = messages.first().displayOriginatingAddress ?: "неизвестный номер"
        val now = System.currentTimeMillis()

        Log.d("Dispatcher", "SMS: $sender | ${body.take(60)}")
        // SMS в чат НЕ публикуются (иначе дублируются со сводками read_sms):
        // только журнал и оценка следилок; пуши — единственные живые карточки чата
        // умные алерты: журнал + мгновенная оценка следилок по SMS
        WatchEngine.logEvent(prefs, WatchEvent("sms", sender, body, now))
        val alerts = AlertStore.load(prefs)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    DebugLog.log("watch-eval", "SMS от «$sender»: ${body.take(60)}")
                    val fired = WatchEngine.evaluateForEvent(prefs, client, alerts, WatchEvent("sms", sender, body, now))
                    DebugLog.log("watch-eval", "сработало оповещений: ${fired.size}")
                    fired.forEach { (alert, text, prompt) -> WatchEngine.deliver(context, alert, text, prompt) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
