/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.miuix.web

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.dsha.ui.AppIconText
import top.youzix.dsha.ui.AppIcons
import top.youzix.dsha.ui.miuix.dshaTextFieldColors
import top.youzix.dsha.ui.web.BrowserState
import top.youzix.dsha.ui.web.WebViewHost
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 网页 — the system WebView behind a plain address bar.
 *
 * The browser state lives in [BrowserState] rather than here, because this tab is disposed every
 * time the pager scrolls away from it.
 */
@Composable
fun WebScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    val tint = MiuixTheme.colorScheme.onBackground
    val dimmed = tint.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(contentPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = { BrowserState.goBack() },
                enabled = BrowserState.canGoBack,
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = "后退",
                    tint = if (BrowserState.canGoBack) tint else dimmed,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(
                onClick = { BrowserState.goForward() },
                enabled = BrowserState.canGoForward,
            ) {
                Icon(
                    imageVector = AppIcons.Forward,
                    contentDescription = "前进",
                    tint = if (BrowserState.canGoForward) tint else dimmed,
                    modifier = Modifier.size(24.dp),
                )
            }
            TextField(
                value = BrowserState.address,
                onValueChange = { BrowserState.onAddressChange(it) },
                modifier = Modifier.weight(1f),
                colors = dshaTextFieldColors(),
                label = "地址",
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { BrowserState.submit() }),
            )
            IconButton(onClick = { BrowserState.reload() }) {
                Icon(
                    imageVector = AppIcons.Refresh,
                    contentDescription = "刷新",
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (BrowserState.pageUrl.isEmpty()) {
                StartState()
            } else {
                WebViewHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Shown until the first address is opened; there is no WebView behind it yet. */
@Composable
private fun StartState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = AppIconText,
            fontSize = 40.sp,
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "输入地址开始浏览",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
