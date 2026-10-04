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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import top.youzix.dsha.ui.web.PlainWebActivity

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
            ) {
                OutlinedTextField(
                    value = BrowserState.address,
                    onValueChange = { BrowserState.onAddressChange(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("地址") },
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
        Button(onClick = onOpen) {
            Text("打开浏览器")
        }
    }
}

/** The browser window is the same one the debug menu used to prove: it renders pages correctly. */
private fun openBrowser(context: android.content.Context) {
    val target = BrowserState.address.ifEmpty { BrowserState.pageUrl }
    context.startActivity(PlainWebActivity.intent(context, target))
}
