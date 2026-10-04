/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.dsha.ui.material3.licenses

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import top.youzix.dsha.ui.material3.material3AppBarColor
import top.youzix.dsha.ui.material3.material3BlurEffect
import top.youzix.dsha.ui.material3.rememberMaterial3BlurBackdrop
import top.youzix.dsha.ui.material3.widgets.NavigationItemWidget
import top.youzix.dsha.ui.material3.widgets.SegmentedColumn
import top.yukonga.miuix.kmp.blur.layerBackdrop

private data class LicenseEntry(val name: String, val license: String, val url: String)

/** Second-level page: the licences of everything this app is built on. */
@Composable
fun MaterialLicensesScreen(onBack: () -> Unit, useBlur: Boolean) {
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    val entries = remember {
        listOf(
            LicenseEntry(
                "DSHA-Next（本项目）",
                "AGPL-3.0",
                "https://github.com/Youzix-Star/DSHA-Next",
            ),
            LicenseEntry(
                "NekoPlus（界面框架来源）",
                "AGPL-3.0",
                "https://github.com/Youzix-Star/NekoPlus",
            ),
            LicenseEntry("miuix", "Apache License 2.0", "https://github.com/compose-miuix-ui/miuix"),
            LicenseEntry(
                "AndroidX / Jetpack Compose",
                "Apache License 2.0",
                "https://cs.android.com/androidx/platform/frameworks/support",
            ),
            LicenseEntry(
                "Material Icons / Material Symbols",
                "Apache License 2.0",
                "https://fonts.google.com/icons",
            ),
            LicenseEntry(
                "AndroidLiquidGlass (Kyant0)",
                "Apache License 2.0",
                "https://github.com/Kyant0/AndroidLiquidGlass",
            ),
            LicenseEntry(
                "Kotlin / kotlinx.coroutines",
                "Apache License 2.0",
                "https://github.com/JetBrains/kotlin",
            ),
            LicenseEntry(
                "InstallerX-Revived",
                "GPL-3.0",
                "https://github.com/wxxsfxyzm/InstallerX-Revived",
            ),
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("开源许可", modifier = Modifier.padding(start = 12.dp)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
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
        contentPadding = paddingValues + PaddingValues(bottom = 24.dp),
    ) {
        item {
            SegmentedColumn(title = "本项目基于 AGPL-3.0 开源") {
                entries.forEach { entry ->
                    item(key = entry.name) {
                        NavigationItemWidget(
                            title = entry.name,
                            description = entry.license,
                            onClick = { uriHandler.openUri(entry.url) },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "点按可在浏览器中查看原文。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
    }
    }
}
