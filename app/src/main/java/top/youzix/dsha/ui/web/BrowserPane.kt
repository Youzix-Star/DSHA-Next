/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Where the 网页 tab wants the browser drawn.
 *
 * The tab publishes a rectangle in window coordinates and whether it is on screen; [MainActivity]
 * puts a real WebView, parented to the window rather than to Compose, exactly there. That split is
 * deliberate: a WebView inside this app's Compose tree never rendered a page's text, while the same
 * WebView parented to the window always has — including in every layering arrangement that could be
 * reproduced outside it.
 */
object BrowserPane {

    /** True while the 网页 tab is the one on screen. */
    var active by mutableStateOf(false)

    /** The card's inner rectangle, in window coordinates. */
    var bounds by mutableStateOf(Rect.Zero)
}
