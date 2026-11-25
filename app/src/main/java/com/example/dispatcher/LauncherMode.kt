package com.example.dispatcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Проверка фактического режима лончера и открытие системного выбора Home. */
object LauncherMode {
    fun isDefaultHome(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val held = runCatching {
                ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
            }.getOrDefault(false)
            if (held) return true
        }
        val current = runCatching {
            Settings.Secure.getString(ctx.contentResolver, "default_home")
        }.getOrNull()
        return ownsHomeComponent(ctx.packageName, current)
    }

    fun ownsHomeComponent(packageName: String, component: String?): Boolean =
        component?.startsWith("$packageName/") == true

    /** Стандартный системный экран выбора домашнего приложения. */
    fun homeIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
