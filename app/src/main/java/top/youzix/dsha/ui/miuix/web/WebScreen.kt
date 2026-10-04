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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.dsha.ui.AppIconText
import top.youzix.dsha.ui.AppIcons
import top.youzix.dsha.ui.miuix.dshaTextFieldColors
import top.youzix.dsha.ui.web.BrowserState
import top.youzix.dsha.ui.web.PlainWebActivity
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
    val context = LocalContext.current

    // Selecting this tab opens the browser window. The WebView renders correctly as its own
    // window and resisted every attempt to be hosted inside this tab's Compose tree, so the tab
    // is the doorway rather than the room.
    LaunchedEffect(Unit) { openBrowser(context) }

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
        ) {
            TextField(
                value = BrowserState.address,
                onValueChange = { BrowserState.onAddressChange(it) },
                modifier = Modifier.weight(1f),
                colors = dshaTextFieldColors(),
                label = "地址",
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { openBrowser(context) }),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            BrowserWindowCard(onOpen = { openBrowser(context) })
        }
    }
}

/** The tab's own body: a doorway, since the browser itself lives in its own window. */
@Composable
private fun BrowserWindowCard(onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = AppIconText,
            fontSize = 34.sp,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "网页在独立窗口里打开",
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "这个页签只负责把它叫起来。",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onOpen, minWidth = 160.dp, minHeight = 42.dp) {
            Text("打开浏览器")
        }
    }
}

/** The browser window is the same one the debug menu used to prove: it renders pages correctly. */
private fun openBrowser(context: android.content.Context) {
    val target = BrowserState.address.ifEmpty { BrowserState.pageUrl }
    context.startActivity(PlainWebActivity.intent(context, target))
}
