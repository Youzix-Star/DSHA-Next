/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.youzix.dsha.ui.web.BrowserPane
import top.youzix.dsha.ui.web.BrowserState
import top.youzix.dsha.ui.web.createBrowserWebView
import top.youzix.dsha.ui.UiEngine
import top.youzix.dsha.ui.UiEnginePrefs
import top.youzix.dsha.ui.material3.MaterialApp
import top.youzix.dsha.ui.miuix.MiuixApp

/**
 * Picks the UI engine and hands the whole window to it.
 *
 * Two complete implementations of the same four tabs live side by side — [MiuixApp] and
 * [MaterialApp] — and the choice is persisted, so switching on the About page takes effect
 * immediately.
 */
class MainActivity : ComponentActivity() {

    private var pane: FrameLayout? = null
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppState.engine = UiEnginePrefs.load(this)
        AppState.useBlur = UiEnginePrefs.loadUseBlur(this)
        AppState.predictiveBackStyle = UiEnginePrefs.loadPredictiveBackStyle(this)
        AppState.debugMode = UiEnginePrefs.loadDebugMode(this)
        setContent {
            when (AppState.engine) {
                UiEngine.Miuix -> MiuixApp()
                UiEngine.Material3 -> MaterialApp()
            }
        }
        installBrowserPane()
    }

    /**
     * Puts the browser in the window, above the Compose content, drawn exactly where the 网页 tab
     * asks for it.
     *
     * The tab is a card; this fills it. Hosting the WebView in Compose instead was tried in every
     * arrangement that can be reproduced outside the app — bare, inside a `graphicsLayer`, inside a
     * pager, inside all of them at once — and every one of those renders pages correctly when it is
     * a view in a window. So the tab describes the rectangle, and the view stays in the window.
     */
    private fun installBrowserPane() {
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        val frame = FrameLayout(this).apply { visibility = View.GONE }
        content.addView(frame, FrameLayout.LayoutParams(0, 0))
        pane = frame

        lifecycleScope.launch {
            snapshotFlow { Triple(BrowserPane.active, BrowserPane.bounds, BrowserState.pageUrl) }
                .collect { (active, rect, url) ->
                    val width = rect.width.toInt()
                    val height = rect.height.toInt()
                    // Nothing to show: stay hidden so the card's own empty state is visible.
                    if (!active || url.isEmpty() || width <= 0 || height <= 0) {
                        frame.visibility = View.GONE
                        return@collect
                    }
                    val view = web ?: createBrowserWebView(this@MainActivity).also { created ->
                        web = created
                        frame.addView(
                            created,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        // Attached here, and only now: the view has a window and a real size,
                        // which is the state a page has to be committed in.
                        BrowserState.attach(created)
                    }
                    view.visibility = View.VISIBLE
                    frame.layoutParams = frame.layoutParams.also {
                        it.width = width
                        it.height = height
                    }
                    frame.x = rect.left
                    frame.y = rect.top
                    frame.visibility = View.VISIBLE
                }
        }
    }

    override fun onDestroy() {
        if (web != null) BrowserState.detach()
        super.onDestroy()
    }
}
