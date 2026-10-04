/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.web

import android.graphics.Bitmap
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import top.youzix.dsha.ui.UiEnginePrefs
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
                // Baseline shared by the open-source WebView browsers that were compared before
                // this line existed: pre-rasterise for the offscreen layer we draw the WebView
                // into (Lightning sets this), allow the mixed content such a page may still
                // carry (Lightning's default), and say UTF-8 out loud for pages that forget to.
                settings.offscreenPreRaster = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.defaultTextEncodingName = "UTF-8"
                if (UiEnginePrefs.loadSoftwareRendering(context)) {
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        BrowserState.onPageStarted(url)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        BrowserState.onPageFinished(view, url)
                        // A positive record, not just failures: how many stylesheets the page
                        // ended up with and whether their rules are readable. "0 张" and "3 张"
                        // look identical on screen when the page is bare, and only one of them
                        // is a loading problem.
                        view.evaluateJavascript(SHEET_REPORT_JS) { report ->
                            WebLog.note(view.context, "样式表清点 ${url.orEmpty()} $report")
                        }
                        view.evaluateJavascript(TEXT_PROBE_JS) { report ->
                            WebLog.note(view.context, "文字探针 ${url.orEmpty()} $report")
                        }
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

/**
 * Counts the stylesheets a finished page actually has.
 *
 * A sheet only appears in `document.styleSheets` once it has been fetched and parsed, so the
 * count answers "did the CSS arrive" without needing to guess from how the page looks. Rules are
 * unreadable for cross-origin sheets (they throw), which is reported as -2 rather than a failure.
 */
internal const val SHEET_REPORT_JS = """(function(){
try{
  var sheets=document.styleSheets, parts=[], i, n;
  for(i=0;i<sheets.length;i++){
    n=-1;
    try{ n = sheets[i].cssRules ? sheets[i].cssRules.length : -1 }catch(e){ n=-2 }
    parts.push((sheets[i].href||'<inline>')+'#'+n);
  }
  var b=getComputedStyle(document.body);
  return sheets.length+' 张 | '+parts.join(' ; ')+' | body: '+b.fontFamily+' / '+b.backgroundColor;
}catch(e){ return 'ERR '+e.message }
})()"""

/**
 * Asks the page what became of its text.
 *
 * "No text on screen" has three very different causes, and they are distinguishable from inside
 * the page: the text may not be in the DOM, it may be laid out with a zero-sized box (a bad
 * `vmin`, a font that never resolved), or it may be laid out correctly and simply painted in a
 * colour nobody can see. This reports which.
 */
internal const val TEXT_PROBE_JS = """(function(){
try{
  function vminPx(){
    var d=document.createElement('div');
    d.style.cssText='position:absolute;left:-9999px;width:1vmin;height:1vmin';
    document.body.appendChild(d);
    var w=d.getBoundingClientRect().width;
    d.parentNode.removeChild(d);
    return w;
  }
  var out=[], pick=null;
  var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null);
  while(walker.nextNode()){
    var n=walker.currentNode;
    if(n.nodeValue && n.nodeValue.trim().length>1){ pick=n.parentElement; break }
  }
  out.push('vmin='+vminPx().toFixed(2));
  out.push('domText='+((document.body.innerText||'').trim().length));
  out.push('bodyColor='+getComputedStyle(document.body).color);
  if(pick){
    var r=pick.getBoundingClientRect(), cs=getComputedStyle(pick);
    out.push('firstText=<'+pick.tagName.toLowerCase()+'> '+Math.round(r.width)+'x'+Math.round(r.height)
      +' size='+cs.fontSize+' color='+cs.color+' fam='+String(cs.fontFamily).split(',')[0]
      +' vis='+cs.visibility+' op='+cs.opacity);
  } else {
    out.push('firstText=none');
  }
  return out.join(' | ');
}catch(e){ return 'ERR '+e.message }
})()"""

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
