/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * 终端页：Termux 的外观，1:1。
 *
 * 画的是 termux-app 首次启动时那一段 —— 欢迎语来自 Termux 自己的 $PREFIX/etc/motd.sh，
 * 提示符来自 $PREFIX/etc/bash.bashrc 的 PS1，配色来自 TerminalColorScheme.java 的默认色表。
 * 每条出处都写在 ui/terminal/TermuxStyle.kt 里。
 *
 * 这一页**不执行任何命令**：没有 PTY、没有 shell、没有一键按钮。它是一张画得很像的画，
 * 两套引擎画的是同一块内容（ui/terminal/TermuxReplica.kt），差别只在外面套什么。
 */

package top.youzix.dsha.ui.miuix.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.dsha.ui.terminal.TermuxColors
import top.youzix.dsha.ui.terminal.TermuxScreenText
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/**
 * 终端.
 *
 * 与 Termux 一致：纯黑底、纯白字、等宽字体；欢迎语里加粗的标签与带下划线的链接；
 * 提示符是绿色的 `~` 加白色的 `$`。横竖都能滚，所以长行不折行 —— 会折行的终端看着就不像终端。
 */
@Composable
fun TerminalScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermuxColors.Background)
            .padding(contentPadding),
    ) {
        SelectionContainer {
            BasicText(
                text = TermuxScreenText,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(vertical)
                    .horizontalScroll(horizontal)
                    .padding(12.dp),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = TermuxFontSize,
                    lineHeight = TermuxLineHeight,
                    color = TermuxColors.Foreground,
                ),
            )
        }
    }
}

/** Termux 的终端字号与行距；行距给足，` - ` 那种缩进行才看得出层次。 */
private val TermuxFontSize = 13.sp
private val TermuxLineHeight = 19.sp
