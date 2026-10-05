/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The page skeleton and the overview rows follow InstallerX-Revived's Material 3 home page
 * (GPL-3.0-only), which may be combined with this project's AGPL-3.0-only code under GPLv3 §13.
 * The block order and the padding values match DSHA's home page so the two engines stay in step.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.dsha.ui.material3.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.dsha.BuildConfig
import top.youzix.dsha.termux.BridgeHome
import top.youzix.dsha.termux.BridgeRow
import top.youzix.dsha.termux.BridgeStat
import top.youzix.dsha.termux.StatusTone
import top.youzix.dsha.termux.TermuxBridge
import top.youzix.dsha.termux.TermuxController
import top.youzix.dsha.termux.TermuxSetup
import top.youzix.dsha.termux.canOpenWebNow
import top.youzix.dsha.termux.homeFrom
import top.youzix.dsha.ui.AppIcons
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
 * The Material 3 twin of the miuix page: same state, same rows, same buttons, built by
 * `termux/TermuxUi.kt`. Changing behaviour in one engine and not the other is the one thing the
 * 双引擎 rule forbids.
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { TermuxController.probe() }

    val home = homeFrom(
        snapshot = snapshot,
        onOpenWeb = onOpenWeb,
        onOpenTermux = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onRequestPermission = {
            if (TermuxBridge.canRequestPermission(context)) {
                permissionLauncher.launch(TermuxBridge.PERMISSION)
            } else {
                context.startActivity(TermuxBridge.permissionIntent(context))
            }
        },
    )

    val icon = when {
        snapshot.setup != TermuxSetup.READY -> AppIcons.Grant
        !snapshot.dshInstalled -> AppIcons.Update
        snapshot.running -> AppIcons.Play
        else -> AppIcons.Update
    }

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
            item { StatusCard(home, icon) }

            items(home.rows.size) { index ->
                val row = home.rows[index]
                CompactStatusCard(row, if (row.id == "bridge") AppIcons.Grant else AppIcons.Tune)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    home.stats.forEach { stat ->
                        StatisticCard(stat, modifier = Modifier.weight(1f))
                    }
                }
            }

            home.remedy?.let { command ->
                item {
                    SegmentedColumn(
                        title = "在 Termux 里执行",
                        contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                    ) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.inverseSurface,
                                shape = RoundedCornerShape(CornerRadius),
                            ) {
                                Text(
                                    text = command,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
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
                            description = if (snapshot.dshInstalled) {
                                snapshot.dshVersion.ifEmpty { "已安装" }
                            } else {
                                "未安装"
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
                    item {
                        BaseWidget(
                            title = "Web 地址",
                            description = if (snapshot.canOpenWebNow) snapshot.url.substringBefore("/?") else "未运行",
                            iconPlaceholder = false,
                            onClick = if (snapshot.canOpenWebNow) onOpenWeb else null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The main block: a panel coloured by state, a large faint icon as a watermark, and the secondary
 * buttons along the bottom.
 */
@Composable
private fun StatusCard(home: BridgeHome, icon: ImageVector) {
    val (container, content) = colorsFor(home.status.tone)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = RoundedCornerShape(CornerRadius),
        onClick = home.status.action ?: {},
        enabled = home.status.action != null,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(88.dp)
                    .alpha(0.16f),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = home.status.headline,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = content,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = home.status.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.9f),
                )
                Text(
                    text = home.status.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (home.buttons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        home.buttons.forEach { action ->
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
        }
    }
}

/** A one-line prerequisite row. */
@Composable
private fun CompactStatusCard(row: BridgeRow, icon: ImageVector) {
    val (container, content) = colorsFor(if (row.active) StatusTone.OK else StatusTone.WARN)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = RoundedCornerShape(CornerRadius),
        onClick = row.action ?: {},
        enabled = row.action != null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(text = row.title, style = MaterialTheme.typography.bodyMediumEmphasized, color = content)
                Text(
                    text = row.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f),
                )
            }
            Text(
                text = row.hint,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.75f),
            )
        }
    }
}

/** One cell of the statistics row. */
@Composable
private fun StatisticCard(stat: BridgeStat, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = stat.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stat.value,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The tone palette every block on this page shares. */
@Composable
private fun colorsFor(tone: StatusTone): Pair<Color, Color> = when (tone) {
    StatusTone.OK -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    StatusTone.WARN, StatusTone.BAD ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    StatusTone.BUSY, StatusTone.IDLE ->
        MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
}
