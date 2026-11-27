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

/** Универсальный WebView-канал: JS и догрузка по высоте страницы, без сайт-специфики. */
object BrowserFetch {
    private data class ScrollState(var attempts: Int = 0, var streak: Int = 0, var lastHeight: Int = 0)

    suspend fun html(ctx: Context, url: String, waitMs: Long = 2500, timeoutMs: Long = 30_000): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                val done = AtomicBoolean(false)
                val webView = WebView(ctx)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.javaScriptCanOpenWindowsAutomatically = false
                val scrollState = ScrollState()

                fun finish(value: String) {
                    if (!done.compareAndSet(false, true)) return
                    runCatching { webView.stopLoading(); webView.destroy() }
                    cont.resume(value)
                }

                fun dump(view: WebView) {
                    view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                        val decoded = decodeJs(raw)
                        DebugLog.log("browser", "dump length=${decoded.length}, raw=${raw?.length ?: 0}")
                        finish(decoded)
                    }
                }

                fun scrollStep(view: WebView, state: ScrollState) {
                    if (done.get()) return
                    view.evaluateJavascript("document.body.scrollHeight") { rawHeight ->
                        val height = rawHeight?.toIntOrNull() ?: 0
                        DebugLog.log("browser", "scrollHeight=$height attempts=${state.attempts} streak=${state.streak}")
                        if (state.attempts >= 2 || state.streak >= 3) {
                            dump(view)
                        } else {
                            if (height > state.lastHeight) {
                                state.lastHeight = height
                                state.streak = 0
                            } else {
                                state.streak++
                            }
                            state.attempts++
                            view.evaluateJavascript(
                                "window.scrollTo(0,0);document.documentElement.scrollTop=0;document.body.scrollTop=0;"
                            ) { }
                            handler.postDelayed({ scrollStep(view, state) }, 1800)
                        }
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        handler.postDelayed({
                            if (done.get()) return@postDelayed
                            scrollStep(view, scrollState)
                        }, waitMs)
                    }
                }
                handler.postDelayed({ dump(webView) }, timeoutMs)
                cont.invokeOnCancellation {
                    runCatching { webView.stopLoading(); webView.destroy() }
                }
                webView.loadUrl(url)
            }
        }

    suspend fun text(ctx: Context, url: String): String {
        val rendered = html(ctx, url)
        if (rendered.isBlank()) return ""
        return WebSearch.htmlToText(rendered).take(100000)
    }

    private fun decodeJs(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
    }
}
