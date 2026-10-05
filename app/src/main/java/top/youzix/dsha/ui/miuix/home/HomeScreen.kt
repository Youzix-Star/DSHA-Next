/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.miuix.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.youzix.dsha.BuildConfig
import top.youzix.dsha.ui.AppIcons
import top.youzix.dsha.termux.BridgeAction
import top.youzix.dsha.termux.StatusTone
import top.youzix.dsha.termux.TermuxController
import top.youzix.dsha.termux.TermuxSetup
import top.youzix.dsha.termux.canOpenWebNow
import top.youzix.dsha.termux.homeFrom
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 首页 — the remote control.
 *
 * Everything on this page is a read of [TermuxController]'s snapshot plus a call back into it; the
 * decisions about which buttons exist live in `termux/TermuxUi.kt` so the Material 3 engine draws
 * exactly the same set.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onOpenWeb: () -> Unit = {},
) {
    val context = LocalContext.current
    val snapshot = TermuxController.snapshot

    // One probe when the page first appears, which is also how the app notices a dsh that was
    // started or stopped outside it.
    LaunchedEffect(Unit) { TermuxController.probe() }

    val home = homeFrom(
        snapshot = snapshot,
        onOpenWeb = onOpenWeb,
        onOpenTermux = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "brand") { BrandCard() }

        item(key = "control") {
            Column {
                SmallTitle(text = "Termux")
                ControlCard(home.status.tone, home.status.headline, home.status.detail, home.actions)
            }
        }

        snapshot.lastError?.let { message ->
            item(key = "error") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.errorContainer,
                        contentColor = MiuixTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        item(key = "overview") {
            Column {
                SmallTitle(text = "概览")
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "应用版本",
                        summary = BuildConfig.VERSION_NAME,
                        startAction = {
                            Icon(AppIcons.About, null, Modifier.size(22.dp))
                        },
                    )
                    BasicComponent(
                        title = "系统版本",
                        summary = "Android ${Build.VERSION.RELEASE}",
                        startAction = {
                            Icon(AppIcons.Phones, null, Modifier.size(22.dp))
                        },
                    )
                    BasicComponent(
                        title = "dsh",
                        summary = when {
                            snapshot.setup != TermuxSetup.READY -> "未就绪"
                            snapshot.dshInstalled -> snapshot.dshVersion.ifEmpty { "已安装" }
                            else -> "未安装"
                        },
                        startAction = {
                            Icon(AppIcons.Terminal, null, Modifier.size(22.dp))
                        },
                    )
                    BasicComponent(
                        title = "Termux",
                        summary = snapshot.termuxVersion?.let { "已安装 $it" } ?: "未安装",
                        startAction = {
                            Icon(AppIcons.Terminal, null, Modifier.size(22.dp))
                        },
                    )
                    BasicComponent(
                        title = "web 端口",
                        summary = snapshot.port.toString(),
                        startAction = {
                            Icon(AppIcons.Web, null, Modifier.size(22.dp))
                        },
                    )
                    if (snapshot.setup == TermuxSetup.READY) {
                        BasicComponent(
                            title = "网页界面",
                            summary = if (snapshot.canOpenWebNow) snapshot.url.substringBefore("/?") else "未运行",
                            onClick = if (snapshot.canOpenWebNow) onOpenWeb else null,
                            startAction = {
                                Icon(AppIcons.Web, null, Modifier.size(22.dp))
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The state card: a coloured block that says where things stand, then the buttons that move it.
 *
 * The heading block takes the tone's colour so the answer to "is it running" is readable from
 * across the room; the buttons stay neutral so the accent button is the only loud thing.
 */
@Composable
private fun ControlCard(
    tone: StatusTone,
    headline: String,
    detail: String,
    actions: List<BridgeAction>,
) {
    val (container, content) = when (tone) {
        StatusTone.OK -> MiuixTheme.colorScheme.primaryContainer to MiuixTheme.colorScheme.onPrimaryContainer
        StatusTone.BAD, StatusTone.WARN ->
            MiuixTheme.colorScheme.errorContainer to MiuixTheme.colorScheme.onErrorContainer
        StatusTone.BUSY, StatusTone.IDLE ->
            MiuixTheme.colorScheme.surfaceContainerHigh to MiuixTheme.colorScheme.onSurfaceContainerHigh
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = container, contentColor = content),
        ) {
            Text(text = headline, color = content, style = MiuixTheme.textStyles.title4)
            Text(
                text = detail,
                color = content,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(top = 6.dp).alpha(0.85f),
            )
        }
        if (actions.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                actions.forEach { action -> ActionRow(action) }
            }
        }
    }
}

/** One action: its name and what it does on the left, the button on the right. */
@Composable
private fun ActionRow(action: BridgeAction) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = action.label, style = MiuixTheme.textStyles.main)
            Text(
                text = action.detail,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Button(
            onClick = action.run,
            enabled = action.enabled,
            colors = if (action.primary) {
                ButtonDefaults.buttonColors()
            } else {
                ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.secondaryVariant,
                    contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                )
            },
        ) {
            Text(action.label, style = MiuixTheme.textStyles.button)
        }
    }
}

/** The one coloured surface at the top: what this app is, in three lines. */
@Composable
private fun BrandCard() {
    val containerColor = MiuixTheme.colorScheme.primaryContainer
    val contentColor = MiuixTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        colors = CardDefaults.defaultColors(color = containerColor, contentColor = contentColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "DSHA-Next", color = contentColor, style = MiuixTheme.textStyles.title4)
            Text(
                text = "DSHA-Next Shell 的 Android 客户端",
                color = contentColor,
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                text = if (TermuxController.snapshot.setup == TermuxSetup.READY) {
                    "安装、启动、停止都走 Termux"
                } else {
                    "先在这一页把 Termux 接上"
                },
                color = contentColor,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(top = 8.dp).alpha(0.75f),
            )
        }
    }
}
