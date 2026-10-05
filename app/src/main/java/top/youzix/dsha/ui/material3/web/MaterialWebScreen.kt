/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.material3.web

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import top.youzix.dsha.ui.web.BrowserPane
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Visibility is decided by the card's own geometry (see [BrowserPane.publish]); all this does
    // is make sure nothing is left showing once the page is gone.
    DisposableEffect(Unit) {
        onDispose { BrowserPane.active = false }
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("网页", modifier = Modifier.padding(start = 12.dp)) },
                scrollBehavior = scrollBehavior,
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
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .padding(paddingValues + outerPadding),
        ) {
            // The strip is a list of one. That is what makes dragging it float the title and
            // stretch at the boundaries exactly as it does on 关于 and 设置.
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item(key = "address") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { BrowserState.goBack() }) {
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = "后退",
                                modifier = Modifier.alpha(if (BrowserState.canGoBack) 1f else 0.38f),
                            )
                        }
                        IconButton(onClick = { BrowserState.goForward() }) {
                            Icon(
                                imageVector = AppIcons.Forward,
                                contentDescription = "前进",
                                modifier = Modifier.alpha(if (BrowserState.canGoForward) 1f else 0.38f),
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
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // The card. What is drawn here is the frame; the page is a WebView parented to the
            // window, positioned over the rectangle this reports — the only hosting that renders
            // these pages correctly.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(BrowserCardInset)
                    .onGloballyPositioned { coordinates ->
                        BrowserPane.publish(coordinates)
                    },
            ) {
                if (BrowserState.pageUrl.isEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "start") {
                            Box(modifier = Modifier.fillParentMaxSize()) {
                                StartState()
                            }
                        }
                    }
                }
            }
        }
    }
}

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
            color = MaterialTheme.colorScheme.onBackground,
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

/** Inset that lets the card's rounded frame show around the page. */
private val BrowserCardInset = 10.dp
