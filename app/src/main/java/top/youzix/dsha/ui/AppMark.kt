/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The app's mark, drawn as the text it already is ([AppIconText]).
 *
 * [BasicText] rather than either engine's `Text`: both About pages show this, and neither engine
 * should have to depend on the other's components. Android's own font fallback draws the glyphs —
 * they come from three different writing systems — and the colour is the caller's, so it follows
 * whichever theme is on.
 */
@Composable
fun AppMark(
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 52.sp,
) {
    BasicText(
        text = AppIconText,
        style = TextStyle(color = color, fontSize = fontSize),
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}
