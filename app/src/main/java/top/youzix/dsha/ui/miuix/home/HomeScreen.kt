/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.miuix.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.youzix.dsha.BuildConfig
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
 * 首页.
 *
 * The app is an interface shell at this stage, so the page says what it is and what it is running
 * on, and nothing more. The remote-control surface lands here when it exists.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "brand") {
            BrandCard()
        }

        item(key = "overview") {
            Column {
                SmallTitle(text = "概览")
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "应用版本",
                        summary = BuildConfig.VERSION_NAME,
                        startAction = {
                            Icon(
                                imageVector = AppIcons.About,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                    )
                    BasicComponent(
                        title = "系统版本",
                        summary = "Android ${Build.VERSION.RELEASE}",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Phones,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** The one coloured surface on the page: what this app is, in three lines. */
@Composable
private fun BrandCard() {
    val containerColor = MiuixTheme.colorScheme.primaryContainer
    val contentColor = MiuixTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        // miuix's card carries no padding of its own, so the content pads itself.
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        colors = CardDefaults.defaultColors(color = containerColor, contentColor = contentColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "DSHA-Next",
                color = contentColor,
                style = MiuixTheme.textStyles.title4,
            )
            Text(
                text = "DSHA-Next Shell 的 Android 客户端",
                color = contentColor,
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                text = "遥控功能将在后续版本接入",
                color = contentColor,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .alpha(0.75f),
            )
        }
    }
}
