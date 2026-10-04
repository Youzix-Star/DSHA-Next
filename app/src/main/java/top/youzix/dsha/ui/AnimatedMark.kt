/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import top.youzix.dsha.R

/**
 * The moving version of the app mark, for the About page.
 *
 * The animation belongs to [AnimatedImageDrawable], not to Compose: it advances itself and tells
 * its callback to redraw, so all this has to do is hand it a canvas and count the nudges. Reading
 * that counter inside the draw lambda is what turns each nudge into a repaint — reading it in the
 * composition instead would recompose the whole page fifty times a second.
 *
 * The decode is deliberately kept off the first composition: it happens in [LaunchedEffect], one
 * frame later, and the result is cached for the process so switching tabs does not decode again.
 */
@Composable
fun AnimatedMark(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = rememberMarkDrawable(context)

    if (drawable == null) {
        // One still frame instead of an empty hole while the decoder runs.
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = null,
            modifier = modifier,
        )
        return
    }

    var frameTick by remember { mutableIntStateOf(0) }
    DisposableEffect(drawable) {
        drawable.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                frameTick += 1
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) = Unit

            override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
        }
        (drawable as? AnimatedImageDrawable)?.start()
        onDispose {
            (drawable as? AnimatedImageDrawable)?.stop()
            drawable.callback = null
        }
    }

    Canvas(modifier = modifier) {
        frameTick.let {
            drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
            drawIntoCanvas { canvas -> drawable.draw(canvas.nativeCanvas) }
        }
    }
}

/** Decoding a 39-frame animation is not something to do once per tab visit. */
private object MarkCache {
    var drawable: Drawable? = null
    var failed = false
}

@Composable
private fun rememberMarkDrawable(context: Context): Drawable? {
    var drawable by remember { mutableStateOf(MarkCache.drawable) }

    LaunchedEffect(Unit) {
        if (MarkCache.drawable != null || MarkCache.failed) return@LaunchedEffect
        val decoded = runCatching {
            ImageDecoder.decodeDrawable(
                ImageDecoder.createSource(context.resources, R.drawable.app_icon_animated),
            )
        }.getOrNull()
        if (decoded == null) MarkCache.failed = true else MarkCache.drawable = decoded
        drawable = decoded
    }

    return drawable
}
