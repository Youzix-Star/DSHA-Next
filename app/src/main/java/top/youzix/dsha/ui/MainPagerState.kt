// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 NekoPlus contributors
//
// Ported from InstallerX-Revived (https://github.com/wxxsfxyzm/InstallerX-Revived),
// ui/navigation/PagerState.kt, which in turn derives from weishu/KernelSU.
// Original: GPL-3.0-only, Copyright (C) 2026 InstallerX Revived contributors.

package top.youzix.dsha.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Wraps a [PagerState] to drive **level-1** tab switching.
 *
 * The point of this wrapper is the split between two pieces of state:
 *
 * - [selectedPage] is the tab the user *asked for*, updated the instant the bottom bar is
 *   tapped. The bar's highlight therefore moves immediately instead of waiting for the
 *   pager to glide to a halt.
 * - [PagerState.currentPage] is where the content actually is right now.
 *
 * [isNavigating] keeps the two from fighting while an animated jump is in flight, so the
 * pager's own progress reports do not yank the highlight back to the outgoing tab.
 *
 * Level-1 navigation deliberately carries **no predictive back visual**: see
 * [top.youzix.dsha.ui.miuix.MiuixApp] and [top.youzix.dsha.ui.material3.MaterialApp], which only
 * route the back gesture here through `onBackCompleted`. Predictive back belongs to
 * level-2 pages.
 */
class MainPagerState(val pagerState: PagerState, private val coroutineScope: CoroutineScope) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    /**
     * Glide to [targetIndex] with the reference project's tab-switch animation: an
     * [EaseInOut] tween whose duration scales with how many pages have to be crossed
     * (`100ms` per page, never less than two pages' worth). Scrolling is driven by hand
     * through [PagerState.scroll] at [MutatePriority.UserInput] so the fling never turns
     * into the pager's default snap-and-settle behaviour.
     */
    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navJob?.cancel()

        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.scroll(MutatePriority.UserInput) {
                    val distance = abs(targetIndex - pagerState.currentPage).coerceAtLeast(2)
                    val duration = 100 * distance + 100
                    val layoutInfo = pagerState.layoutInfo
                    val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                    val currentDistanceInPages =
                        targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
                    val scrollPixels = currentDistanceInPages * pageSize

                    var previousValue = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = scrollPixels,
                        animationSpec = tween(easing = EaseInOut, durationMillis = duration),
                    ) { currentValue, _ ->
                        previousValue += scrollBy(currentValue - previousValue)
                    }
                }

                if (pagerState.currentPage != targetIndex) {
                    pagerState.scrollToPage(targetIndex)
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    // A user swipe that interrupted the animation wins over the target we
                    // were asked to reach.
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    /** Adopt the pager's position once the user has settled somewhere by swiping. */
    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainPagerState = remember(pagerState, coroutineScope) {
    MainPagerState(pagerState, coroutineScope)
}
