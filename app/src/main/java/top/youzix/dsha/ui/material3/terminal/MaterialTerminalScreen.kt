/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Material 3 引擎下的终端页：外面是这一套引擎自己的大标题栏，里面那块画面与行为跟 miuix
 * 完全一致 —— 外观样式在 ui/terminal 下共享，执行走同一个 TermuxController.submit。
 *
 * 一行命令一次 app-shell 往返，没有 PTY；vim/top 这类交互式程序跑不了。
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.dsha.ui.material3.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.dsha.termux.TermuxController
import top.youzix.dsha.ui.material3.material3AppBarColor
import top.youzix.dsha.ui.material3.material3BlurEffect
import top.youzix.dsha.ui.material3.rememberMaterial3BlurBackdrop
import top.youzix.dsha.ui.terminal.TermuxColors
import top.youzix.dsha.ui.terminal.terminalLine
import top.yukonga.miuix.kmp.blur.layerBackdrop

private val TerminalFont = 13.sp
private val TerminalLineHeight = 19.sp

/**
 * 终端.
 *
 * 面板是纯黑的（Termux 的默认背景色），不跟随主题 —— 终端就该是终端的样子。
 * 顶栏仍然属于这个引擎，模糊与配色照旧。
 */
@Composable
fun MaterialTerminalScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
) {
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val controller = TermuxController
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(controller.screen.size) {
        if (controller.screen.isNotEmpty()) listState.scrollToItem(controller.screen.lastIndex)
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("终端", modifier = Modifier.padding(start = 12.dp)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backdrop.material3AppBarColor(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = backdrop.material3AppBarColor(),
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .padding(PaddingValues(0.dp) + paddingValues + outerPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TermuxColors.Background)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                itemsIndexed(controller.screen) { _, line ->
                    Text(
                        text = terminalLine(line),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = TerminalFont,
                            lineHeight = TerminalLineHeight,
                            color = TermuxColors.Foreground,
                        ),
                        modifier = Modifier.fillMaxWidth(),
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("命令") },
                    singleLine = true,
                    enabled = !controller.busy,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                )
                Button(
                    onClick = {
                        controller.submit(input)
                        input = ""
                    },
                    enabled = !controller.busy && input.isNotBlank(),
                ) {
                    Text("执行")
                }
            }
        }
    }
}
