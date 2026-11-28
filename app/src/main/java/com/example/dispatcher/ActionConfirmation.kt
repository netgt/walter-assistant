package com.example.dispatcher

/** Одноразовое подтверждение внешнего действия, привязанное к action и содержимому. */
object ActionConfirmation {
    private data class Pending(val action: String, val payload: String, val expiresAt: Long)

    private var pending: Pending? = null
    private var granted: Pending? = null
    private var prompt: String = ""

    @Synchronized
    fun prepare(action: String, payload: String, now: Long = System.currentTimeMillis()) {
        pending = Pending(action, payload, now + 300_000)
        granted = null
        prompt = ""
        DebugLog.log("confirm", "prepare $action")
    }

    @Synchronized
    fun notePrompt(text: String) {
        prompt = text
    }

    @Synchronized
    fun confirmPending(promptText: String, now: Long = System.currentTimeMillis()) {
        val p = pending ?: return
        if (p.expiresAt < now) {
            DebugLog.log("confirm", "rejected prompt/expiry for ${p.action}")
            pending = null
            return
        }
        granted = p.copy(expiresAt = now + 120_000)
        pending = null
        prompt = ""
        DebugLog.log("confirm", "granted ${p.action}")
    }

    @Synchronized
    fun consume(action: String, payload: String, now: Long = System.currentTimeMillis()): Boolean {
        val p = granted
        if (p == null) {
            DebugLog.log("confirm", "consume $action: no grant")
            return false
        }
        granted = null // любой повтор/другой payload требует нового подтверждения
        val ok = p.action == action && p.payload == payload && p.expiresAt >= now
        DebugLog.log("confirm", "consume $action: ok=$ok sameAction=${p.action == action} samePayload=${p.payload == payload}")
        return ok
    }

    @Synchronized
    fun reset() {
        pending = null
        granted = null
        prompt = ""
    }
}
