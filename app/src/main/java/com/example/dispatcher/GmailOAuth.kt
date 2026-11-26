package com.example.dispatcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Gmail OAuth: AppAuth для входа, refresh напрямую через oauth2.googleapis.com. */
object GmailOAuth {
    const val RC_AUTH = 4400
    private const val REDIRECT = "com.example.dispatcher:/oauth2redirect"
    private const val SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    private val http = OkHttpClient()

    fun clientId(prefs: SharedPreferences): String =
        prefs.getString(Prefs.KEY_GMAIL_CLIENT_ID, "")?.trim().orEmpty()

    fun isConnected(prefs: SharedPreferences): Boolean {
        val raw = prefs.getString(Prefs.KEY_GMAIL_OAUTH, null) ?: return false
        return runCatching { JSONObject(raw).optString("refreshToken").isNotBlank() }.getOrDefault(false)
    }

    fun email(prefs: SharedPreferences): String =
        runCatching {
            JSONObject(prefs.getString(Prefs.KEY_GMAIL_OAUTH, null) ?: "{}").optString("email")
        }.getOrDefault("")

    fun authorizationIntent(ctx: Context): Intent {
        val clientId = clientId(ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE))
        val config = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token")
        )
        val request = AuthorizationRequest.Builder(config, clientId, ResponseTypeValues.CODE, Uri.parse(REDIRECT))
            .setScope(SCOPE)
            .setPrompt("consent")
            .setAdditionalParameters(mapOf("access_type" to "offline"))
            .build()
        return AuthorizationService(ctx).getAuthorizationRequestIntent(request)
    }

    fun handleResult(ctx: Context, data: Intent?) {
        val response = data?.let { AuthorizationResponse.fromIntent(it) }
        if (response == null) {
            DebugLog.log("gmail-oauth", "вход отменён или ошибка ответа")
            return
        }
        val service = AuthorizationService(ctx)
        service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, error ->
            if (tokenResponse != null) {
                saveTokens(ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE), tokenResponse)
                DebugLog.log("gmail-oauth", "токены получены")
            } else {
                DebugLog.log("gmail-oauth", "обмен кода: ${error?.error ?: "unknown"}")
            }
            service.dispose()
        }
    }

    private fun saveTokens(prefs: SharedPreferences, response: TokenResponse) {
        val old = runCatching {
            JSONObject(prefs.getString(Prefs.KEY_GMAIL_OAUTH, null) ?: "{}")
        }.getOrDefault(JSONObject())
        old.put("accessToken", response.accessToken ?: "")
        old.put("refreshToken", response.refreshToken ?: old.optString("refreshToken"))
        old.put("expiresAt", response.accessTokenExpirationTime ?: 0L)
        prefs.edit().putString(Prefs.KEY_GMAIL_OAUTH, old.toString()).apply()
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit().remove(Prefs.KEY_GMAIL_OAUTH).apply()
    }

    /** Должен вызываться в фоне. Обновляет access token при необходимости. */
    fun validAccessToken(prefs: SharedPreferences): String? {
        val raw = prefs.getString(Prefs.KEY_GMAIL_OAUTH, null) ?: return null
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val token = obj.optString("accessToken")
        if (token.isNotBlank() && obj.optLong("expiresAt", 0L) > System.currentTimeMillis() + 60_000) {
            return token
        }
        val refreshToken = obj.optString("refreshToken")
        val clientId = clientId(prefs)
        if (refreshToken.isBlank() || clientId.isBlank()) return null
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()
        val request = Request.Builder().url("https://oauth2.googleapis.com/token").post(body).build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                if (!response.isSuccessful) return@use null
                val access = json.optString("access_token").takeIf { it.isNotBlank() } ?: return@use null
                obj.put("accessToken", access)
                obj.put("expiresAt", System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
                obj.put("refreshToken", json.optString("refresh_token").ifBlank { refreshToken })
                prefs.edit().putString(Prefs.KEY_GMAIL_OAUTH, obj.toString()).apply()
                access
            }
        }.getOrNull()
    }
}
