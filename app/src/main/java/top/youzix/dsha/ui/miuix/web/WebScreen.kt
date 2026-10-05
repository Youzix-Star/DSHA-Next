/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.miuix.web

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import top.youzix.dsha.ui.web.BrowserPane
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.youzix.dsha.ui.AppIconText
import top.youzix.dsha.ui.AppIcons
import top.youzix.dsha.ui.miuix.dshaTextFieldColors
import top.youzix.dsha.ui.web.BrowserState
import top.yukonga.miuix.kmp.basic.Button
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
    // True only while this tab is the one on screen, which is exactly when the window-level
    // browser should be showing.
    DisposableEffect(Unit) {
        BrowserPane.active = true
        onDispose { BrowserPane.active = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(contentPadding),
    ) {
        // Dragging this strip scrolls the page, so the top bar collapses and expands exactly as it
        // does on the home and about pages. Nothing here needs to move: what reacts is the bar,
        // fed by the unconsumed scroll deltas of a container whose content already fits.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .overScrollVertical(),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { BrowserState.goBack() },
                enabled = BrowserState.canGoBack,
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = "后退",
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
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // The card. What is drawn here is the frame; the page itself is a WebView parented to the
        // window, positioned over the rectangle this reports — the only hosting that renders these
        // pages correctly.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .padding(BrowserCardInset)
                .onGloballyPositioned { coordinates ->
                    BrowserPane.bounds = coordinates.boundsInWindow()
                },
        ) {
            if (BrowserState.pageUrl.isEmpty()) {
                // The window-level browser is hidden while there is nothing to show, so the card
                // says what to do — and dragging it scrolls too, since it is empty.
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maxHeight)
                            .overScrollVertical(),
                    ) {
                        StartState()
                    }
                }
            }
        }
    }
}

/** Inset that lets the card's rounded frame show around the page. */
private val BrowserCardInset = 10.dp

/** Shown while nothing is loaded: the window-level browser is hidden, so the card is empty. */
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
