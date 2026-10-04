/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.miuix.licenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private data class LicenseEntry(val name: String, val license: String, val url: String)

/** Second-level page: the licences of everything this app is built on. */
@Composable
fun LicensesScreen(contentPadding: PaddingValues) {
    val uriHandler = LocalUriHandler.current
    val entries = remember {
        listOf(
            LicenseEntry("DSHA-Next（本项目）", "AGPL-3.0", "https://github.com/Youzix-Star/DSHA-Next"),
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "licenses") {
            Column {
                SmallTitle(text = "本项目基于 AGPL-3.0 开源")
                Card(modifier = Modifier.fillMaxWidth()) {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) {
                        }
                        ArrowPreference(
                            title = entry.name,
                            summary = entry.license,
                            onClick = { uriHandler.openUri(entry.url) },
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            Text(
                text = "点按可在浏览器中查看原文。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
