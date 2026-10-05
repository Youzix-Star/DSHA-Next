/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * 终端页：Termux 的外观 + 能用的命令控制台。
 *
 * 外观来自 termux-app 自己的东西（欢迎语取自 $PREFIX/etc/motd.sh、提示符取自
 * $PREFIX/etc/bash.bashrc 的 PS1、配色取自 TerminalColorScheme.java），出处逐条写在
 * ui/terminal/TermuxStyle.kt。
 *
 * 能用的部分是：敲一条命令，发给 Termux 执行，输出接在提示符后面。一行命令一次 app-shell
 * 往返 —— 所以 vim/top 这类交互式程序跑不了，那需要 termux-app 的 native 终端模拟器。
 */

package top.youzix.dsha.ui.miuix.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.dsha.termux.TermuxController
import top.youzix.dsha.ui.terminal.TermuxColors
import top.youzix.dsha.ui.terminal.terminalLine
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Termux 的终端字号与行距。行距给足，` - ` 那种缩进行才看得出层次。 */
private val TerminalFont = 13.sp
private val TerminalLineHeight = 19.sp

/**
 * 终端.
 *
 * 上：整屏输出（欢迎语 → 提示符 → 命令 → 输出 → 提示符 …），自动滚到底。
 * 下：一行输入。回车或「执行」发出去；忙的时候输入框锁住。
 */
@Composable
fun TerminalScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    val controller = TermuxController
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 每接一行就跟到底：终端永远停在提示符那儿。
    LaunchedEffect(controller.screen.size) {
        if (controller.screen.isNotEmpty()) listState.scrollToItem(controller.screen.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermuxColors.Background)
            .padding(contentPadding),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            itemsIndexed(controller.screen) { index, line ->
                Text(
                    text = terminalLine(line),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = TerminalFont,
                        lineHeight = TerminalLineHeight,
                        color = TermuxColors.Foreground,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (index == controller.screen.lastIndex) 2.dp else 0.dp),
                )
            }
        }

        if (controller.busy) {
            Text(
                text = "${controller.busyLabel} …",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TermuxColors.DimYellow,
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }

        InputRow(
            value = input,
            onValueChange = { input = it },
            enabled = !controller.busy,
            onSubmit = {
                controller.submit(input)
                input = ""
            },
        )
    }
}

/**
 * 输入行。
 *
 * 用的是主题里的输入框（它是 miuix 的组件，外观跟随主题），因为纯黑底上加一条边框比
 * 「在提示符后面插光标」要清楚得多 —— 后者在手机上没法点、也没法改。
 * 终端本身仍然是黑的，只有这一条是控件的颜色。
 */
@Composable
private fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = "命令",
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onSubmit, enabled = enabled && value.isNotBlank()) {
            Text("执行", style = MiuixTheme.textStyles.button)
        }
    }
}
