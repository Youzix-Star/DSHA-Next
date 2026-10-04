/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.miuix.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 终端.
 *
 * A placeholder with the shape of a terminal and no terminal behind it: nothing here runs a
 * command, and nothing is wired to a shell yet.
 */
@Composable
fun TerminalScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(contentPadding),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column {
                Text(
                    text = "$ ▌",
                    fontFamily = FontFamily.Monospace,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "终端尚未接入",
                    fontFamily = FontFamily.Monospace,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
