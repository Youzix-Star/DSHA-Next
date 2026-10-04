/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.web

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import top.youzix.dsha.util.WebLog

/**
 * The same page in a window that owes nothing to the app's shell.
 *
 * No Compose, no pager, no glass backdrop, no theme — one [WebView] in a plain LinearLayout. It
 * exists because the browser inside the app can end up in places a browser normally never is
 * (inside a Compose `AndroidView`, under a `layerBackdrop`), and when a page misbehaves there, the
 * only way to tell "this is our hosting" from "this is the app" is to remove the hosting.
 */
class PlainWebActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var address: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: WEB_TEST_URL
        val pad = (12 * resources.displayMetrics.density).toInt()

        // An address bar, because a comparison window you cannot navigate is not a comparison:
        // the whole point is opening the same URL here and in the app's browser.
        address = EditText(this).apply {
            setText(url)
            setSingleLine()
            setTextColor(0xFFE0E0E0.toInt())
            setHintTextColor(0xFF808080.toInt())
            hint = "输入网址"
            textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    go(address.text.toString())
                    true
                } else {
                    false
                }
            }
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF1F1F1F.toInt())
            addView(address, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                Button(this@PlainWebActivity).apply {
                    text = "前往"
                    setOnClickListener { go(address.text.toString()) }
                },
            )
            addView(
                Button(this@PlainWebActivity).apply {
                    text = "关闭"
                    setOnClickListener { finish() }
                },
            )
        }

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false

            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    val where = if (request.isForMainFrame) "主文档" else "子资源"
                    WebLog.note(
                        this@PlainWebActivity,
                        "纯净窗口 $where 加载失败：${error.errorCode} ${error.description} ${request.url}",
                    )
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    WebLog.note(
                        this@PlainWebActivity,
                        "纯净窗口 HTTP ${errorResponse.statusCode} ${request.url}",
                    )
                }

                override fun onPageStarted(view: WebView, startedUrl: String?, favicon: android.graphics.Bitmap?) {
                    if (!startedUrl.isNullOrEmpty()) address.setText(startedUrl)
                }

                override fun onPageFinished(view: WebView, finishedUrl: String?) {
                    view.evaluateJavascript(SHEET_REPORT_JS) { report ->
                        WebLog.note(
                            this@PlainWebActivity,
                            "纯净窗口样式表清点 ${finishedUrl.orEmpty()} $report",
                        )
                    }
                    view.evaluateJavascript(TEXT_PROBE_JS) { report ->
                        WebLog.note(
                            this@PlainWebActivity,
                            "纯净窗口文字探针 ${finishedUrl.orEmpty()} $report",
                        )
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() >= ConsoleMessage.MessageLevel.WARNING) {
                        WebLog.note(
                            this@PlainWebActivity,
                            "纯净窗口 console/${message.messageLevel()} " +
                                "${message.sourceId()}:${message.lineNumber()} ${message.message()}",
                        )
                    }
                    return false
                }
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(bar)
                addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )
        web.loadUrl(url)
    }

    /** Same normalisation as the in-app browser, so both windows are asked for the same thing. */
    private fun go(input: String) {
        val target = normalize(input).ifEmpty { return }
        address.setText(target)
        web.loadUrl(target)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    companion object {
        const val EXTRA_URL = "top.youzix.dsha.PLAIN_URL"

        /** Opens [url] in the bare window, for comparing against the in-app browser. */
        fun intent(context: Context, url: String): Intent =
            Intent(context, PlainWebActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
