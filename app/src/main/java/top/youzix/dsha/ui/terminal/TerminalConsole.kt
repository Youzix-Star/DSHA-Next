/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The terminal pane: one scrollable body of monospace text.
 *
 * Shared by both engines, and deliberately built on `foundation`'s [BasicText] rather than on
 * either engine's Text: this file sits in the shared layer, where importing one engine's widget
 * would be the first crack in the 互不共享 Composable rule. What each engine owns is the chrome
 * around this — the field the command is typed into and the card it sits on.
 *
 * It is a real scroll view rather than a `LazyColumn`: a command's output arrives as one string,
 * the text is selectable, and a terminal you cannot scroll back through is not a terminal.
 */
@Composable
fun TerminalConsole(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    background: Color = Color.Unspecified,
    fontSize: Int = 12,
) {
    val scroll = rememberScrollState()

    // Follow the tail as output arrives. Not animated on purpose: a command that prints a lot
    // should *be* at the end, not travel there.
    LaunchedEffect(text) {
        if (text.isNotEmpty()) scroll.scrollTo(scroll.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(scroll),
    ) {
        SelectionContainer {
            BasicText(
                text = text,
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    color = textColor,
                ),
            )
        }
    }
}
