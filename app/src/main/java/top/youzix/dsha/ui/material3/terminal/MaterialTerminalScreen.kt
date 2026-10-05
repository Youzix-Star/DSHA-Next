/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Material 3 引擎下的终端页：外面是这一套引擎自己的大标题栏，里面那块画面与 miuix 完全一致 ——
 * 内容由 ui/terminal/TermuxReplica.kt 提供，两套引擎画的是同一份 Termux 画面。
 *
 * 同样不执行任何命令：没有 PTY、没有 shell、没有一键按钮。
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.dsha.ui.material3.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.dsha.ui.material3.material3AppBarColor
import top.youzix.dsha.ui.material3.material3BlurEffect
import top.youzix.dsha.ui.material3.rememberMaterial3BlurBackdrop
import top.youzix.dsha.ui.terminal.TermuxColors
import top.youzix.dsha.ui.terminal.TermuxScreenText
import top.yukonga.miuix.kmp.blur.layerBackdrop

/** 终端字号与行距，与 miuix 那份保持一致。 */
private val TermuxFontSize = 13.sp
private val TermuxLineHeight = 19.sp

/**
 * 终端.
 *
 * 面板是纯黑的（Termux 的默认背景色），不是主题里的 surface —— 终端就该是终端的样子，
 * 跟随主题变色反而不像。顶栏仍然属于这个引擎，模糊与配色照旧。
 */
@Composable
fun MaterialTerminalScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
) {
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()

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
                .padding(PaddingValues(12.dp) + paddingValues + outerPadding)
                .background(TermuxColors.Background),
        ) {
            SelectionContainer {
                BasicText(
                    text = TermuxScreenText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(vertical)
                        .horizontalScroll(horizontal),
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
}
