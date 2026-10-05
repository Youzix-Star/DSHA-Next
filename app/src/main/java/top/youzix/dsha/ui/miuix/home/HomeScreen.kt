/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The page follows DSHA's home page (AGPL-3.0, same author): a coloured main status block, two
 * compact prerequisite rows, a three-cell statistics row, then the overview list. Its card padding
 * values are copied deliberately — miuix's `Card` has `insideMargin = 0` by default, so a bare
 * `Text` in a card touches the edge unless the card asks for padding itself.
 */

package top.youzix.dsha.ui.miuix.home

import android.os.Build
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.youzix.dsha.BuildConfig
import top.youzix.dsha.termux.BridgeHome
import top.youzix.dsha.termux.BridgeRow
import top.youzix.dsha.termux.BridgeStat
import top.youzix.dsha.termux.StatusTone
import top.youzix.dsha.termux.TermuxSetup
import top.youzix.dsha.termux.TermuxController
import top.youzix.dsha.termux.canOpenWebNow
import top.youzix.dsha.termux.homeFrom
import top.youzix.dsha.ui.AppIcons
import top.yukonga.miuix.kmp.basic.BasicComponent
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
 * Everything here is a read of [TermuxController]'s snapshot plus a call back into it; which rows,
 * buttons and statistics exist is decided in `termux/TermuxUi.kt`, so the Material 3 engine draws
 * exactly the same set.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onOpenWeb: () -> Unit = {},
) {
    val snapshot = TermuxController.snapshot

    // One probe when the page first appears, which is also how the app notices a dsh that was
    // started or stopped outside it.
    LaunchedEffect(Unit) { TermuxController.refresh() }

    val home = homeFrom(snapshot)

    val icon = when {
        snapshot.setup != TermuxSetup.READY -> AppIcons.Grant
        !snapshot.dshInstalled -> AppIcons.Update
        snapshot.running -> AppIcons.Play
        else -> AppIcons.Update
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "status") {
            StatusCard(home = home, icon = icon)
        }

        items(home.rows.size) { index ->
            val row = home.rows[index]
            CompactStatusCard(
                row = row,
                icon = if (row.id == "bridge") AppIcons.Grant else AppIcons.Tune,
            )
        }

        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                home.stats.forEach { stat ->
                    StatisticCard(stat, modifier = Modifier.weight(1f))
                }
            }
        }


        item(key = "overview") {
            Column {
                SmallTitle(text = "概览")
                // 卡内是 BasicComponent：它自带 16dp 内边距，外层再给就会双重留白，所以这里不设。
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "应用版本",
                        summary = BuildConfig.VERSION_NAME,
                        startAction = { Icon(AppIcons.About, null, Modifier.size(22.dp)) },
                    )
                    BasicComponent(
                        title = "系统版本",
                        summary = "Android ${Build.VERSION.RELEASE}",
                        startAction = { Icon(AppIcons.Phones, null, Modifier.size(22.dp)) },
                    )
                    BasicComponent(
                        title = "dsh",
                        summary = if (snapshot.dshInstalled) {
                            snapshot.dshVersion.ifEmpty { "已安装" }
                        } else {
                            "未安装"
                        },
                        startAction = { Icon(AppIcons.Terminal, null, Modifier.size(22.dp)) },
                    )
                    BasicComponent(
                        title = "Termux",
                        summary = snapshot.termuxVersion?.let { "已安装 $it" } ?: "未安装",
                        startAction = { Icon(AppIcons.Terminal, null, Modifier.size(22.dp)) },
                    )
                    BasicComponent(
                        title = "Web 地址",
                        summary = if (snapshot.canOpenWebNow) snapshot.url.substringBefore("/?") else "未运行",
                        onClick = if (snapshot.canOpenWebNow) onOpenWeb else null,
                        startAction = { Icon(AppIcons.Web, null, Modifier.size(22.dp)) },
                    )
                }
            }
        }
    }
}

/**
 * The main block: a panel coloured by state, with the icon of what it is doing as a large faint
 * watermark, and the secondary buttons in a row along the bottom.
 *
 * Tapping the panel runs the action the state is asking for; the buttons are the ones that are not
 * that action. Padding is explicit (20dp) because a miuix `Card` has none of its own.
 */
@Composable
private fun StatusCard(home: BridgeHome, icon: ImageVector) {
    val (container, content) = colorsFor(home.status.tone)

    // 只有外观，没有 onClick —— 这一版不提供任何会执行命令的入口。
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        colors = CardDefaults.defaultColors(color = container, contentColor = content),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(88.dp)
                    .alpha(0.16f),
                tint = content,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = home.status.headline,
                    color = content,
                    style = MiuixTheme.textStyles.title4,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = home.status.detail,
                    color = content,
                    style = MiuixTheme.textStyles.body2,
                )
                Text(
                    text = home.status.hint,
                    color = content,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .alpha(0.75f),
                )
            }
        }
    }
}

/** A one-line prerequisite row: icon, what it is, and what a tap does. */
@Composable
private fun CompactStatusCard(row: BridgeRow, icon: ImageVector) {
    val (container, content) = colorsFor(if (row.active) StatusTone.OK else StatusTone.WARN)

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(color = container, contentColor = content),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text(text = row.title, color = content, style = MiuixTheme.textStyles.body2)
                Text(
                    text = row.summary,
                    color = content,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.alpha(0.75f),
                )
            }
            Text(
                text = row.hint,
                color = content,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.alpha(0.75f),
            )
        }
    }
}

/** One cell of the statistics row: a label and a value. */
@Composable
private fun StatisticCard(stat: BridgeStat, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stat.title,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stat.value,
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** The tone palette every block on this page shares. */
@Composable
private fun colorsFor(tone: StatusTone): Pair<Color, Color> = when (tone) {
    StatusTone.OK -> MiuixTheme.colorScheme.primaryContainer to MiuixTheme.colorScheme.onPrimaryContainer
    StatusTone.WARN, StatusTone.BAD ->
        MiuixTheme.colorScheme.errorContainer to MiuixTheme.colorScheme.onErrorContainer
    StatusTone.BUSY, StatusTone.IDLE ->
        MiuixTheme.colorScheme.surfaceContainerHigh to MiuixTheme.colorScheme.onSurfaceContainerHigh
}
