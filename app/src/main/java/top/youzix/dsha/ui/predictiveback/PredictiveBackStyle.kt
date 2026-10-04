/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.predictiveback

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which flavour of predictive-back animation plays on a second-level page.
 *
 * Each style is nothing but a set of numbers: the geometry they feed lives in one shared place
 * ([Modifier.backLayer]), so both engines animate identically and there is only ever one
 * implementation of the gesture to get right.
 */
enum class PredictiveBackStyle(val id: String, val label: String) {
    /** The page follows the finger, shrinking and drifting with it, like a system dismiss. */
    Aosp("aosp", "AOSP"),

    /** The page slides away 1:1 with the finger while the page underneath parallaxes into place. */
    Miuix("miuix", "Miuix"),

    /** No gesture feedback at all: the page simply closes. */
    None("none", "无动画"),
    ;

    val config: BackMotionConfig
        get() = when (this) {
            Aosp -> BackMotionConfig(
                pageFollowsFinger = true,
                pageVerticalDrift = true,
                pageScaleAtDismiss = 0.9f,
                pageCornerRadiusAtDismiss = null,
                coveredParallaxFraction = 0f,
                coveredDimAtRest = 0f,
                coveredScrimAtRest = 0.32f,
                commitSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                cancelSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
            )

            Miuix -> BackMotionConfig(
                pageFollowsFinger = true,
                pageVerticalDrift = false,
                pageScaleAtDismiss = 1f,
                pageCornerRadiusAtDismiss = 24.dp,
                coveredParallaxFraction = 0.25f,
                coveredDimAtRest = 0.1f,
                coveredScrimAtRest = 0.18f,
                commitSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                cancelSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
            )

            None -> BackMotionConfig(
                // Everything is identity, so the page neither follows the finger nor moves: only
                // the close itself is timed, and it is kept short so it reads as a plain swap.
                pageFollowsFinger = false,
                pageVerticalDrift = false,
                pageScaleAtDismiss = 1f,
                pageCornerRadiusAtDismiss = null,
                coveredParallaxFraction = 0f,
                coveredDimAtRest = 0f,
                coveredScrimAtRest = 0f,
                commitSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
                cancelSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
            )
        }

    companion object {
        // AOSP is the platform's own gesture, so it is what the app does unless told otherwise.
        /**
         * The style for a stored id, or for a fresh install.
         *
         * Defaults to [Miuix]: it is the animation this app was written against, and the platform's
         * own is a fallback for phones whose launcher does not do predictive back at all.
         */
        fun from(id: String?): PredictiveBackStyle = entries.firstOrNull { it.id == id } ?: Miuix
    }
}

/**
 * The geometry one predictive-back style is made of.
 *
 * Every value describes the same drag: how the two layers look while the second-level page is
 * fully open, and how they look once it is gone. The transforms interpolate between those two
 * ends as a pure function of the drag's progress, which is what keeps the finger tracking exact.
 *
 * @param pageFollowsFinger whether the dismissing page translates with the drag at all.
 * @param pageVerticalDrift whether it also tracks the finger's vertical movement.
 * @param pageScaleAtDismiss scale the dismissing page shrinks to as it leaves.
 * @param pageCornerRadiusAtDismiss corner radius it rounds to as it leaves, or `null` for square.
 * @param coveredParallaxFraction how far the revealed page slides toward the leading edge while it
 *   is covered, as a fraction of the screen width.
 * @param coveredDimAtRest how transparent the revealed page is while it is covered.
 * @param coveredScrimAtRest alpha of the dark scrim laid over the revealed page while covered.
 * @param commitSpec the animation that carries the drag to completion once the finger lifts.
 * @param cancelSpec the animation that springs the page back when the gesture is abandoned.
 */
data class BackMotionConfig(
    val pageFollowsFinger: Boolean,
    val pageVerticalDrift: Boolean,
    val pageScaleAtDismiss: Float,
    val pageCornerRadiusAtDismiss: Dp?,
    val coveredParallaxFraction: Float,
    val coveredDimAtRest: Float,
    val coveredScrimAtRest: Float,
    val commitSpec: AnimationSpec<Float>,
    val cancelSpec: AnimationSpec<Float>,
)
