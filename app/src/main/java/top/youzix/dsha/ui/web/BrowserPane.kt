/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow

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

    /**
     * Publishes a card's rectangle — but only while the card is whole.
     *
     * While the pager slides this page, Compose clips it and the rectangle reported in window
     * coordinates shrinks with the clip; committing that would squeeze the browser down to a
     * sliver mid-swipe. A clipped rectangle therefore reads as "not on screen", which is also the
     * right moment to keep the browser hidden.
     */
    fun publish(coordinates: LayoutCoordinates) {
        val rect = coordinates.boundsInWindow()
        val whole = rect.width >= coordinates.size.width - 1f &&
            rect.height >= coordinates.size.height - 1f
        active = whole
        if (whole) bounds = rect
    }
}
