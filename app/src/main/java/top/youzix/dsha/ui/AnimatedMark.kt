/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The app's mark on the About page, alive: it breathes slowly and blinks now and then.
 *
 * The mark *is* a string ([AppIconText]), so this animates text rather than an image file. That
 * keeps it in the theme's ink instead of a colour baked into a bitmap, lets Android's own font
 * fallback draw the glyphs, and keeps a megabyte of animation out of the APK. It is drawn with
 * [BasicText] because both UI engines show it and neither's Text should be a dependency of the
 * other's.
 */
@Composable
fun AnimatedMark(
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 52.sp,
) {
    var blinking by remember { mutableStateOf(false) }
    val breath = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(BLINK_EVERY_MS)
            blinking = true
            delay(BLINK_LENGTH_MS)
            blinking = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            breath.animateTo(BREATH_SCALE, tween(BREATH_MS, easing = EaseInOut))
            breath.animateTo(1f, tween(BREATH_MS, easing = EaseInOut))
        }
    }

    BasicText(
        text = if (blinking) MarkBlinking else AppIconText,
        style = TextStyle(color = color, fontSize = fontSize),
        maxLines = 1,
        softWrap = false,
        modifier = modifier.graphicsLayer {
            scaleX = breath.value
            scaleY = breath.value
        },
    )
}

/** The same face with its eyes shut. */
private val MarkBlinking = AppIconText.replace('\u00b0', '-')

private const val BLINK_EVERY_MS = 2600L
private const val BLINK_LENGTH_MS = 150L
private const val BREATH_MS = 1400
private const val BREATH_SCALE = 1.05f
