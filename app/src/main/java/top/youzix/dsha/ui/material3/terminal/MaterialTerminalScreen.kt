/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.dsha.ui.material3.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.dsha.termux.TermuxController
import top.youzix.dsha.termux.TermuxPhase
import top.youzix.dsha.termux.terminalShortcuts
import top.youzix.dsha.termux.terminalWelcome
import top.youzix.dsha.ui.material3.CornerRadius
import top.youzix.dsha.ui.material3.material3AppBarColor
import top.youzix.dsha.ui.material3.material3BlurEffect
import top.youzix.dsha.ui.material3.rememberMaterial3BlurBackdrop
import top.youzix.dsha.ui.terminal.TerminalConsole
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * 终端 — one command at a time, through Termux.
 *
 * The Material 3 twin of the miuix page: same state, same shortcuts, same console. Not a terminal
 * emulator — no PTY, one `app-shell` round trip per line — and the welcome text says so.
 */
@Composable
fun MaterialTerminalScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
) {
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snapshot = TermuxController.snapshot
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        if (!initialized) {
            output = terminalWelcome(snapshot)
            initialized = true
        }
    }

    LaunchedEffect(snapshot.lastOutput) {
        if (snapshot.lastOutput.isNotEmpty()) output = snapshot.lastOutput
    }

    val busy = snapshot.phase == TermuxPhase.BUSY
    val shortcuts = terminalShortcuts(snapshot)

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
                .padding(PaddingValues(16.dp) + paddingValues + outerPadding),
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shortcuts.size) { index ->
                    val (command) = shortcuts[index]
                    FilledTonalButton(onClick = { input = command }) {
                        Text(
                            text = command,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("命令") },
                    singleLine = true,
                    enabled = !busy,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (input.isNotBlank() && !busy) {
                                TermuxController.run(input)
                                input = ""
                            }
                        },
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        TermuxController.run(input)
                        input = ""
                    },
                    enabled = !busy && input.isNotBlank(),
                ) {
                    Text(if (busy) "执行中" else "执行")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { TermuxController.readLog() }, enabled = !busy) {
                    Text("web 日志")
                }
                FilledTonalButton(
                    onClick = { clipboard.setText(AnnotatedString(output)) },
                    enabled = output.isNotEmpty(),
                ) {
                    Text("复制")
                }
                FilledTonalButton(onClick = { output = "" }, enabled = output.isNotEmpty()) {
                    Text("清空")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // The pane: one surface filling what is left of the page, on the inverse scheme so a
            // terminal reads as a surface of its own rather than another card of the page.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(CornerRadius),
                ) {
                    TerminalConsole(
                        text = output,
                        textColor = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}
