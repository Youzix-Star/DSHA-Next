/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.material3.web

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.dsha.ui.AppIconText
import top.youzix.dsha.ui.AppIcons
import top.youzix.dsha.ui.material3.material3AppBarColor
import top.youzix.dsha.ui.material3.material3BlurEffect
import top.youzix.dsha.ui.material3.rememberMaterial3BlurBackdrop
import top.youzix.dsha.ui.web.BrowserState
import top.youzix.dsha.ui.web.WebViewHost

/**
 * 网页 — the system WebView behind a plain address bar.
 *
 * The browser state lives in [BrowserState] rather than here, because this tab is disposed every
 * time the pager scrolls away from it. Only the address row belongs to Compose: the page below it
 * scrolls inside the WebView, which is why this page has no scrolling container of its own.
 */
@Composable
fun MaterialWebScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
) {
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    val canGoBack = BrowserState.canGoBack
    val canGoForward = BrowserState.canGoForward
    // A history button with nowhere to go fades instead of leaving the row: were it taken out, the
    // address bar would shift sideways mid-browse. It stays tappable and does nothing.
    val goBack: () -> Unit = { if (canGoBack) BrowserState.goBack() }
    val goForward: () -> Unit = { if (canGoForward) BrowserState.goForward() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("网页", modifier = Modifier.padding(start = 12.dp)) },
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
                // No `layerBackdrop` here, deliberately: the WebView must not be recorded into a
                // layer (Chromium's draw functor only runs on a direct hardware draw, so later
                // paints — fonts especially — never show up in a replayed layer). The blurred top
                // bar falls back to its flat colour on this page.
                .padding(paddingValues + outerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = goBack) {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = "后退",
                        modifier = Modifier.alpha(if (canGoBack) 1f else 0.38f),
                    )
                }
                IconButton(onClick = goForward) {
                    Icon(
                        imageVector = AppIcons.Forward,
                        contentDescription = "前进",
                        modifier = Modifier.alpha(if (canGoForward) 1f else 0.38f),
                    )
                }
                OutlinedTextField(
                    value = BrowserState.address,
                    onValueChange = { BrowserState.onAddressChange(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("地址") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { BrowserState.submit() }),
                )
                IconButton(onClick = { BrowserState.reload() }) {
                    Icon(imageVector = AppIcons.Refresh, contentDescription = "刷新")
                }
            }

            // Keeps the address row off the page below it; without it the WebView reads as part
            // of the toolbar rather than as the thing the address bar points at.
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                // The start state is Compose's, not the WebView's: there is nothing to load until
                // an address is submitted, so no WebView exists before that.
                if (BrowserState.pageUrl.isEmpty()) {
                    StartState()
                } else {
                    WebViewHost(modifier = Modifier.fillMaxSize())
                }
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
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "输入地址开始浏览",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
