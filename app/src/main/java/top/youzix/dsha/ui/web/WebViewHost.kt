/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.web

import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import top.youzix.dsha.util.WebLog

/**
 * The 网页 tab's browser, shared by both UI engines.
 *
 * It is a process-wide object rather than per-screen state on purpose: the pager disposes a tab
 * the moment it scrolls out of view, and a browser that forgets where it was every time the user
 * looks at another tab is not a browser. The WebView itself still belongs to the screen — it is
 * attached on composition and destroyed when the tab goes away — so nothing here outlives the page
 * that drew it.
 */
/**
 * The self-test page that ships in the APK's assets.
 *
 * It runs in the WebView itself rather than describing it from the outside: inline CSS, a
 * same-directory stylesheet, a remote https one and a plaintext http one, with a verdict for
 * each. `file:///android_asset/` is exempt from the file-access restrictions that apply to other
 * `file://` URLs, so this page always opens.
 */
const val WEB_TEST_URL = "file:///android_asset/webview-test.html"

object BrowserState {
    /** What the address bar holds; the user's draft until it is submitted. */
    var address by mutableStateOf("")
        private set

    /** What is actually loaded. Empty means the start state is showing instead of the WebView. */
    var pageUrl by mutableStateOf("")
        private set

    var loading by mutableStateOf(false)
        private set

    var canGoBack by mutableStateOf(false)
        private set

    var canGoForward by mutableStateOf(false)
        private set

    private var view: WebView? = null

    fun onAddressChange(text: String) {
        address = text
    }

    /** Go where the address bar points. */
    fun submit() {
        open(address)
    }

    /** Go to [input], which may be a bare host such as `127.0.0.1:3080`. */
    fun open(input: String) {
        val target = normalize(input)
        if (target.isEmpty()) {
            goHome()
            return
        }
        address = target
        val attached = view
        if (attached == null) {
            // No WebView yet: publishing the url is what makes the screen compose one, and it
            // loads this url as it attaches.
            pageUrl = target
        } else {
            attached.loadUrl(target)
        }
    }

    fun reload() {
        view?.reload()
    }

    fun goBack() {
        val attached = view ?: return
        if (attached.canGoBack()) attached.goBack()
    }

    fun goForward() {
        val attached = view ?: return
        if (attached.canGoForward()) attached.goForward()
    }

    /** Back to the start state; composing the host away is what disposes the WebView. */
    fun goHome() {
        address = ""
        pageUrl = ""
        canGoBack = false
        canGoForward = false
    }

    internal fun attach(webView: WebView) {
        view = webView
        val target = pageUrl
        if (target.isNotEmpty()) webView.loadUrl(target)
    }

    internal fun detach() {
        val webView = view ?: return
        view = null
        webView.destroy()
    }

    internal fun onPageStarted(url: String?) {
        if (url.isNullOrEmpty()) return
        pageUrl = url
        address = url
        loading = true
    }

    internal fun onPageFinished(webView: WebView, url: String?) {
        if (!url.isNullOrEmpty()) {
            pageUrl = url
            address = url
        }
        canGoBack = webView.canGoBack()
        canGoForward = webView.canGoForward()
        loading = false
    }

    internal fun onProgress(progress: Int) {
        loading = progress < 100
    }
}

/** The WebView surface. Compose it only while [BrowserState.pageUrl] is not empty. */
@Composable
fun WebViewHost(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Browser-shaped viewport handling: honour a page's own <meta viewport>, and fall
                // back to the wide viewport for the pages that never declare one. Without this a
                // desktop-shaped page is squeezed into the phone's width and reads as "the layout
                // is gone" rather than "this page is not mobile".
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                // A browser without pinch zoom is not a browser. displayZoomControls=false keeps
                // the on-screen +/- buttons out of the way; the gesture stays.
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        BrowserState.onPageStarted(url)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        BrowserState.onPageFinished(view, url)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        // Subresources matter as much as the document here: a stylesheet that
                        // never arrived is exactly the bug this log exists to explain.
                        WebLog.note(view.context, describeFailure(request, error))
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        WebLog.note(
                            view.context,
                            "HTTP ${errorResponse.statusCode} ${cssMark(request)} ${request.url}",
                        )
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        BrowserState.onProgress(newProgress)
                    }

                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        // This is where a blocked stylesheet says why — mixed content, CSP, a
                        // refused connection. Warnings and errors only; the rest is noise.
                        if (message.messageLevel() >= ConsoleMessage.MessageLevel.WARNING) {
                            WebLog.note(
                                context,
                                "console/${message.messageLevel()} " +
                                    "${message.sourceId()}:${message.lineNumber()} ${message.message()}",
                            )
                        }
                        return false
                    }
                }

                BrowserState.attach(this)
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose { BrowserState.detach() }
    }
}

/** One failed request, with the two things needed to tell a block from a fluke. */
private fun describeFailure(request: WebResourceRequest, error: WebResourceError): String {
    val where = if (request.isForMainFrame) "主文档" else "子资源"
    return "${where}加载失败${cssMark(request)}：${error.errorCode} ${error.description} ${request.url}"
}

/** Marks the requests whose failure is most likely to be the one being chased. */
private fun cssMark(request: WebResourceRequest): String {
    val url = request.url?.toString()?.lowercase() ?: return ""
    return if (url.contains(".css") || url.contains("/css")) " [css]" else ""
}

/**
 * Turn what the user typed into something loadable.
 *
 * A bare host gets `https://`, except on loopback where an https attempt is always wrong — this
 * app exists to talk to a server running on the phone itself. Written as a scan rather than a
 * regex: Android's regex engine is ICU, not the JVM's.
 */
internal fun normalize(input: String): String {
    val text = input.trim()
    if (text.isEmpty()) return ""
    if (hasScheme(text)) return text
    val head = text.substringBefore('/').substringBefore('?').lowercase()
    val host = head.substringBefore(':')
    val loopback = host == "localhost" || host == "127.0.0.1" || text.startsWith("[")
    return if (loopback) "http://$text" else "https://$text"
}

private fun hasScheme(text: String): Boolean {
    val colon = text.indexOf(':')
    if (colon <= 0) return false
    for (index in 0 until colon) {
        val char = text[index]
        val allowed = char in 'a'..'z' || char in 'A'..'Z' ||
            (index > 0 && (char in '0'..'9' || char == '+' || char == '-' || char == '.'))
        if (!allowed) return false
    }
    return true
}
