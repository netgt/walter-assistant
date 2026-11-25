package com.example.dispatcher

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcel
import android.util.Base64

/**
 * Кнопка «Ответить» на карточке пуша: запускает contentIntent уведомления
 * (открывает мессенджер с диалогом) или, как фолбэк, главный экран приложения.
 */
object PushReply {

    // живые ссылки пушей (процесс жив): если упаковка в base64 у конкретного приложения
    // падает (бывает у MAX), карточка открывается из памяти напрямую
    private val liveIntents = HashMap<String, List<PendingIntent>>()

    fun registerLive(key: String, intents: List<PendingIntent>) {
        if (intents.isEmpty()) return
        liveIntents[key] = intents
        if (liveIntents.size > 100) liveIntents.clear()
    }

    private fun takeLive(key: String): List<PendingIntent>? = liveIntents[key]

    /** Упаковка PendingIntent в base64 для хранения в переписке. */
    fun pendingIntentToB64(pi: PendingIntent?): String? {
        if (pi == null) return null
        return try {
            val p = Parcel.obtain()
            pi.writeToParcel(p, 0)
            val b64 = Base64.encodeToString(p.marshall(), Base64.NO_WRAP)
            p.recycle()
            b64
        } catch (e: Exception) {
            DebugLog.log("pi-pack", "упаковка не удалась: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Восстановление PendingIntent из base64. */
    fun b64ToPendingIntent(b64: String?): PendingIntent? {
        if (b64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val p = Parcel.obtain()
            p.unmarshall(bytes, 0, bytes.size)
            p.setDataPosition(0)
            val pi = PendingIntent.CREATOR.createFromParcel(p)
            p.recycle()
            pi
        } catch (e: Exception) {
            null
        }
    }

    /** Восстановление списка PendingIntent из base64 (несколько — через «|»). */
    fun b64ToPendingIntents(b64: String?): List<PendingIntent> {
        if (b64.isNullOrBlank()) return emptyList()
        return b64.split('|').mapNotNull { b64ToPendingIntent(it) }
    }

    /** Пакет приложения по подписи (имени) — для старых пушей, у которых нет пакета.
     *  Сначала точное совпадение, затем нечёткое по первому слову. */
    fun packageByLabel(ctx: Context, label: String): String? {
        if (label.isBlank()) return null
        return runCatching {
            val pm = ctx.packageManager
            val matches = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            )
            matches.firstOrNull { it.loadLabel(pm).toString().equals(label, ignoreCase = true) }
                ?.activityInfo?.packageName
                ?: run {
                    // нечётко: первое слово подписи встречается в имени приложения или наоборот
                    val first = label.trim().split(Regex("""\s+""")).firstOrNull().orEmpty().take(12)
                    matches.firstOrNull {
                        val appLabel = it.loadLabel(pm).toString()
                        (first.length >= 3 && appLabel.contains(first, ignoreCase = true)) ||
                            (appLabel.length >= 3 && label.contains(appLabel.take(12), ignoreCase = true))
                    }?.activityInfo?.packageName
                }
        }.getOrNull()
    }

    /** Открыть мессенджер с диалогом; фолбэк — главный экран приложения. */
    fun open(event: EventInfo, ctx: Context) {
        var ok = false
        // сначала живые ссылки из памяти (для пушей, чья упаковка в base64 падает),
        // затем ссылки, сохранённые в карточке
        val liveKey = "push|${event.source}|${event.at}"
        // только первый интент (contentIntent): одноразовые кнопки «Прочитано» и т.п.
        // в старых карточках «съедали» тап, ничего не открывая
        val fromMemory = takeLive(liveKey) ?: b64ToPendingIntents(event.pi)
        val active = WatchService.findActive(event.pkg ?: "", event.title, event.text)
        DebugLog.log("push-open", "key=$liveKey fromMemory=${fromMemory.size} active=${active != null} pkg=${event.pkg}")
        // Свежий активный интент пробуем раньше сохранённого: старый PI мог быть отменён системой.
        val intents = (listOfNotNull(active) + fromMemory).distinct().take(2)
        intents.forEachIndexed { i, pi ->
            // Android 14+: явно разрешаем запуск из foreground Walter, иначе send() может быть BAL_BLOCK.
            if (Build.VERSION.SDK_INT >= 34) {
                ok = runCatching {
                    val options = ActivityOptions.makeBasic().apply {
                        pendingIntentBackgroundActivityStartMode =
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }
                    pi.send(ctx, 0, Intent(), null, null, null, options.toBundle())
                    true
                }.getOrElse { e ->
                    DebugLog.log("push-open", "intent#$i send+options: ${e.message ?: e.javaClass.simpleName}")
                    false
                }
            }
            if (!ok) ok = runCatching { pi.send(); true }.getOrElse { e ->
                DebugLog.log("push-open", "intent#$i send(): ${e.message ?: e.javaClass.simpleName}"); false
            }
            if (!ok) {
                ok = runCatching {
                    ctx.startIntentSender(pi.intentSender, null, 0, 0, 0)
                    true
                }.getOrElse { e ->
                    DebugLog.log("push-open", "intent#$i startIntentSender: ${e.message ?: e.javaClass.simpleName}"); false
                }
            }
            if (!ok) {
                ok = runCatching { pi.send(ctx, 0, Intent()); true }.getOrElse { e ->
                    DebugLog.log("push-open", "intent#$i send(ctx,0,intent): ${e.message ?: e.javaClass.simpleName}"); false
                }
            }
            if (ok) {
                DebugLog.log("push-open", "intent#$i открыл: ${event.source}")
                val act = ctx as? MainActivity
                val pkg = event.pkg ?: packageByLabel(ctx, event.source)
                if (act != null && !pkg.isNullOrBlank() && pkg != act.packageName) {
                    // send() не гарантирует видимый экран (BAL_BLOCK). Запускаем главный экран
                    // только если Walter всё ещё foreground; открытый конкретный чат не перекрываем.
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (act.walterForeground) {
                            val launch = runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
                            if (launch != null) {
                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                                val started = runCatching { ctx.startActivity(launch) }.isSuccess
                                DebugLog.log("push-open", "foreground fallback $pkg started=$started")
                            }
                        }
                    }, 1200)
                }
                return
            }
        }
        DebugLog.log(
            "push-open",
            "source=${event.source} pi=${if (event.pi != null) "есть" else "нет"} opened=$ok (после смахивания пуша ссылка отменяется системой — открываем приложение)"
        )
        if (ok) return
        val pkg = event.pkg ?: packageByLabel(ctx, event.source)
        DebugLog.log("push-open", "фолбэк: pkg=${pkg ?: "не найден"} (event.pkg=${event.pkg})")
        if (!pkg.isNullOrBlank()) {
            val launch = runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val started = runCatching { ctx.startActivity(launch) }.isSuccess
                DebugLog.log("push-open", "фолбэк: главный экран $pkg started=$started")
            } else {
                DebugLog.log("push-open", "фолбэк: нет launch-intent для $pkg")
            }
        }
    }
}
