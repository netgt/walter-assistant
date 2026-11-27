package com.example.dispatcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Отдельный browser-канал: WebView с JavaScript, без изменения чистого fetch_url. */
object BrowserFetch {
    suspend fun html(ctx: Context, url: String, waitMs: Long = 2500, timeoutMs: Long = 30_000): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                val done = AtomicBoolean(false)
                val webView = WebView(ctx)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.javaScriptCanOpenWindowsAutomatically = false

                fun finish(value: String) {
                    if (!done.compareAndSet(false, true)) return
                    runCatching { webView.stopLoading(); webView.destroy() }
                    cont.resume(value)
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        handler.postDelayed({
                            if (done.get()) return@postDelayed
                            view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                finish(decodeJs(raw))
                            }
                        }, waitMs)
                    }
                }
                handler.postDelayed({ finish("") }, timeoutMs)
                cont.invokeOnCancellation {
                    runCatching { webView.stopLoading(); webView.destroy() }
                }
                webView.loadUrl(url)
            }
        }

    suspend fun text(ctx: Context, url: String): String {
        val rendered = html(ctx, url)
        if (rendered.isBlank()) return ""
        return WebSearch.htmlToText(rendered).take(2400)
    }

    private fun decodeJs(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
    }
}
