/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.miuix.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import top.youzix.dsha.termux.TermuxController
import top.youzix.dsha.termux.TermuxPhase
import top.youzix.dsha.termux.terminalShortcuts
import top.youzix.dsha.termux.terminalWelcome
import top.youzix.dsha.ui.terminal.TerminalConsole
import top.youzix.dsha.ui.miuix.dshaTextFieldColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 终端 — one command at a time, through Termux.
 *
 * This is not a terminal emulator and does not pretend to be one: there is no PTY, each line is a
 * single `app-shell` round trip, and what comes back is what Termux captured. The welcome text
 * says so, because the alternative is a user discovering that `vim` cannot work by trying it.
 */
@Composable
fun TerminalScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    val snapshot = TermuxController.snapshot
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // The welcome text is written once, so re-entering the tab does not wipe what was run.
    LaunchedEffect(Unit) {
        if (!initialized) {
            output = terminalWelcome(snapshot)
            initialized = true
        }
    }

    // A finished command replaces the pane with its result; nothing else writes to it.
    LaunchedEffect(snapshot.lastOutput) {
        if (snapshot.lastOutput.isNotEmpty()) {
            output = snapshot.lastOutput
        }
    }

    val busy = snapshot.phase == TermuxPhase.BUSY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(contentPadding),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(terminalShortcuts(snapshot).size) { index ->
                val command = terminalShortcuts(snapshot)[index]
                Button(
                    onClick = { input = command },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.secondaryVariant,
                        contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                    ),
                ) {
                    Text(command, style = MiuixTheme.textStyles.footnote1)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                colors = dshaTextFieldColors(),
                label = "命令",
                singleLine = true,
                enabled = !busy,
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
                Text(if (busy) "执行中" else "执行", style = MiuixTheme.textStyles.button)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { TermuxController.readLog() },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.secondaryVariant,
                    contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                ),
            ) {
                Text("web 日志", style = MiuixTheme.textStyles.button)
            }
            Button(
                onClick = { clipboard.setText(AnnotatedString(output)) },
                enabled = output.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.secondaryVariant,
                    contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                ),
            ) {
                Text("复制", style = MiuixTheme.textStyles.button)
            }
            Button(
                onClick = { output = "" },
                enabled = output.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.secondaryVariant,
                    contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                ),
            ) {
                Text("清空", style = MiuixTheme.textStyles.button)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // The pane: one card filling what is left of the page. The scrollable's content is the
        // console itself, so an overscroll stretches the pane rather than only the text inside it.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val paneHeight = maxHeight
            Card(modifier = Modifier.fillMaxWidth().height(paneHeight)) {
                TerminalConsole(
                    text = output,
                    modifier = Modifier.overScrollVertical(),
                    textColor = MiuixTheme.colorScheme.onSurface,
                    background = MiuixTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}
