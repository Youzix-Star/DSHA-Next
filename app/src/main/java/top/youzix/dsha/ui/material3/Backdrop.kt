// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 NekoPlus contributors
//
// Ported from InstallerX-Revived (https://github.com/wxxsfxyzm/InstallerX-Revived),
// ui/theme/Backdrop.kt. Original: GPL-3.0-only,
// Copyright (C) 2026 InstallerX Revived contributors.
//
// The reference project blurs the Material 3 top bar with miuix's own backdrop engine
// rather than a separate one, so the glass looks identical across both of this app's UI
// engines. Everything below comes from `miuix-blur`, which needs minSdk 33.

package top.youzix.dsha.ui.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

/**
 * Remember a [LayerBackdrop] painted with `surfaceContainer` so the blurred strip never
 * alpha-blends against whatever happens to be behind the window.
 *
 * @param enableBlur Whether the effect is switched on.
 * @return a backdrop, or `null` when blur is off or the device cannot render effects.
 */
@Composable
fun rememberMaterial3BlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * The top bar has to go transparent when a backdrop is behind it, otherwise the blur would be
 * hidden by an opaque bar; without a backdrop it keeps the normal surface colour.
 */
@Composable
fun LayerBackdrop?.material3AppBarColor(): Color =
    this?.let { Color.Transparent } ?: MaterialTheme.colorScheme.surfaceContainer

/**
 * Gaussian-blur [this] against [backdrop], producing the glass strip under the top bar.
 *
 * @param backdrop the visual source; the modifier is a no-op when this is `null`.
 * @param enabled lets a single call site opt out while blur is on globally.
 * @param blurRadius the Gaussian radius.
 * @param shape the clip applied to the blurred area.
 */
@Composable
fun Modifier.material3BlurEffect(
    backdrop: LayerBackdrop?,
    enabled: Boolean = true,
    blurRadius: Float = 25f,
    shape: Shape = RectangleShape,
): Modifier {
    if (!enabled || backdrop == null) return this

    val blendColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)

    return this.then(
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(color = blendColor),
                ),
            ),
        ),
    )
}
