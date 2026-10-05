/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.miuix.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import top.yukonga.miuix.kmp.utils.overScrollVertical

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
        // The card is the scrollable's content, not its container: that is what makes the whole
        // panel the thing that stretches on overscroll, instead of only the two lines inside it.
        // The scrollable still reports its unused deltas, so the top bar keeps collapsing.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Taken out of the scope on purpose: inside the Column below there are two implicit
            // receivers, and `maxHeight` only exists on one of them.
            val paneHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(paneHeight)
                    .overScrollVertical(),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(paneHeight),
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
    }
}
