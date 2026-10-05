/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The page skeleton and the overview rows follow InstallerX-Revived's Material 3 home page
 * (GPL-3.0-only), which may be combined with this project's AGPL-3.0-only code under GPLv3 §13.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.dsha.ui.material3.home

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
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.youzix.dsha.BuildConfig
import top.youzix.dsha.termux.BridgeAction
import top.youzix.dsha.termux.StatusTone
import top.youzix.dsha.termux.TermuxController
import top.youzix.dsha.termux.TermuxSetup
import top.youzix.dsha.termux.canOpenWebNow
import top.youzix.dsha.termux.homeFrom
import top.youzix.dsha.ui.material3.CornerRadius
import top.youzix.dsha.ui.material3.material3AppBarColor
import top.youzix.dsha.ui.material3.material3BlurEffect
import top.youzix.dsha.ui.material3.rememberMaterial3BlurBackdrop
import top.youzix.dsha.ui.material3.widgets.BaseWidget
import top.youzix.dsha.ui.material3.widgets.SegmentedColumn
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * 首页 — the remote control.
 *
 * Same state, same buttons as the miuix engine: the list is built by `termux/TermuxUi.kt`, and
 * this page only decides how a button looks. Changing behaviour here and not there would make the
 * two engines disagree, which is the one thing the双引擎 rule forbids.
 */
@Composable
fun MaterialHomeScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
    onOpenWeb: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    val context = LocalContext.current
    val snapshot = TermuxController.snapshot

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

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = {
                    Text(
                        text = "DSHA-Next",
                        modifier = Modifier.padding(start = 12.dp),
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backdrop.material3AppBarColor(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = backdrop.material3AppBarColor(),
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier),
            contentPadding = PaddingValues(16.dp) + paddingValues + outerPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BrandCard() }

            item {
                SegmentedColumn(
                    title = "Termux",
                    contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                ) {
                    item {
                        StatusBlock(
                            tone = home.status.tone,
                            headline = home.status.headline,
                            detail = home.status.detail,
                        )
                    }
                    if (home.actions.isNotEmpty()) {
                        item {
                            ActionsBlock(home.actions)
                        }
                    }
                }
            }

            snapshot.lastError?.let { message ->
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            item {
                SegmentedColumn(
                    title = "概览",
                    contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                ) {
                    item {
                        BaseWidget(
                            title = "应用版本",
                            description = BuildConfig.VERSION_NAME,
                            iconPlaceholder = false,
                        )
                    }
                    item {
                        BaseWidget(
                            title = "系统版本",
                            description = "Android ${Build.VERSION.RELEASE}",
                            iconPlaceholder = false,
                        )
                    }
                    item {
                        BaseWidget(
                            title = "dsh",
                            description = when {
                                snapshot.setup != TermuxSetup.READY -> "未就绪"
                                snapshot.dshInstalled -> snapshot.dshVersion.ifEmpty { "已安装" }
                                else -> "未安装"
                            },
                            iconPlaceholder = false,
                        )
                    }
                    item {
                        BaseWidget(
                            title = "Termux",
                            description = snapshot.termuxVersion?.let { "已安装 $it" } ?: "未安装",
                            iconPlaceholder = false,
                        )
                    }
                    if (snapshot.canOpenWebNow) {
                        item {
                            BaseWidget(
                                title = "打开网页界面",
                                description = snapshot.url.substringBefore("/?"),
                                iconPlaceholder = false,
                                onClick = onOpenWeb,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The headline state, painted with its tone: success, a problem, or just "nothing running". */
@Composable
private fun StatusBlock(tone: StatusTone, headline: String, detail: String) {
    val container = when (tone) {
        StatusTone.OK -> MaterialTheme.colorScheme.primaryContainer
        StatusTone.WARN, StatusTone.BAD -> MaterialTheme.colorScheme.errorContainer
        StatusTone.BUSY, StatusTone.IDLE -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when (tone) {
        StatusTone.OK -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.WARN, StatusTone.BAD -> MaterialTheme.colorScheme.onErrorContainer
        StatusTone.BUSY, StatusTone.IDLE -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        color = container,
        shape = RoundedCornerShape(CornerRadius),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = content,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** One row per action: what it does on the left, the button on the right. */
@Composable
private fun ActionsBlock(actions: List<BridgeAction>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        actions.forEach { action ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = action.label, style = MaterialTheme.typography.bodyMediumEmphasized)
                    Text(
                        text = action.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (action.primary) {
                    Button(onClick = action.run, enabled = action.enabled) {
                        Text(action.label)
                    }
                } else {
                    FilledTonalButton(onClick = action.run, enabled = action.enabled) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

/**
 * What this build is, in the same shape the page used to give its status card: one filled surface
 * at the top of the list. Nothing behind it is switchable yet, so it is not tappable.
 */
@Composable
private fun BrandCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = "DSHA-Next",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "DSHA-Next Shell 的 Android 客户端",
                style = MaterialTheme.typography.bodySmallEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = if (TermuxController.snapshot.setup == TermuxSetup.READY) {
                    "安装、启动、停止都走 Termux"
                } else {
                    "先在这一页把 Termux 接上"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
