/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The shell follows InstallerX-Revived's Material 3 layout (GPL-3.0): an outer Scaffold that owns
 * only the bottom navigation, with each page carrying its own large top app bar.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.dsha.ui.material3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.youzix.dsha.AppState
import top.youzix.dsha.ui.AppIcons
import top.youzix.dsha.ui.material3.about.MaterialAboutScreen
import top.youzix.dsha.ui.material3.home.MaterialHomeScreen
import top.youzix.dsha.ui.material3.licenses.MaterialLicensesScreen
import top.youzix.dsha.ui.material3.terminal.MaterialTerminalScreen
import top.youzix.dsha.ui.material3.web.MaterialWebScreen
import top.youzix.dsha.ui.material3.widgets.SwipeableSnackbarHost
import top.youzix.dsha.ui.predictiveback.PredictiveBackHost
import top.youzix.dsha.ui.rememberMainPagerState
import top.youzix.dsha.util.CrashHandler

private const val TAB_HOME = 0
private const val TAB_WEB = 1
private const val TAB_TERMINAL = 2
private const val TAB_ABOUT = 3

/**
 * Second-level pages. Only these take part in predictive back: switching between the bottom
 * navigation tabs moves between siblings, so there is no parent screen to preview.
 */
private enum class MaterialSubPage(val title: String) {
    Licenses("开源许可"),
}

@Composable
fun MaterialApp() {
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    var dynamicColor by remember { mutableStateOf(true) }
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    MaterialAppTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        MaterialShell(
            themeMode = themeMode,
            onThemeModeChange = { themeMode = it },
            dynamicColor = dynamicColor,
            onDynamicColorChange = { dynamicColor = it },
        )
    }
}

@Composable
private fun MaterialShell(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    var subPage by remember { mutableStateOf<MaterialSubPage?>(null) }

    val labels = listOf("首页", "网页", "终端", "关于")
    val icons: List<ImageVector> = listOf(
        AppIcons.Home,
        AppIcons.Web,
        AppIcons.Terminal,
        AppIcons.About,
    )

    val pagerState = rememberPagerState(pageCount = { labels.size })
    val mainPagerState = rememberMainPagerState(pagerState)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val notify: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Adopt the pager's position after the user swipes between tabs by hand.
    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
        // Breadcrumb: if the app dies, the report should say which tab was being built.
        CrashHandler.note("页签 ${labels.getOrNull(pagerState.currentPage) ?: pagerState.currentPage}")
    }

    // ---- level-1 back: no predictive visual, just the tab-switch animation back to home ----
    // Only armed while a tab is showing and it is not the first one, mirroring the reference
    // project's "we are on the main route and the back stack is empty" condition. When a
    // second-level page is open, its own PredictiveBackHandler owns the gesture instead.
    BackHandler(enabled = subPage == null && mainPagerState.selectedPage != 0) {
        mainPagerState.animateToPage(0)
    }

    // ---- second-level pages ----
    // A level-two page owns its whole surface, app bar included, exactly as the four tabs do. The
    // shell used to stack a second, large app bar on top of the page's own, which is what pushed
    // that page's first control a third of a screen down the screen.
    Box(modifier = Modifier.fillMaxSize()) {
        PredictiveBackHost(
            subPageOpen = subPage != null,
            style = AppState.predictiveBackStyle,
            onDismissed = { subPage = null },
            levelOne = {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    bottomBar = {
                        NavigationBar {
                            labels.forEachIndexed { index, label ->
                                NavigationBarItem(
                                    // Driven by selectedPage, not the pager's currentPage, so the
                                    // highlight lands the moment the tab is tapped.
                                    selected = mainPagerState.selectedPage == index,
                                    onClick = { mainPagerState.animateToPage(index) },
                                    icon = {
                                        Icon(imageVector = icons[index], contentDescription = label)
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { outerPadding ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                    ) { page ->
                        when (page) {
                            TAB_HOME -> MaterialHomeScreen(
                                outerPadding = outerPadding,
                                useBlur = AppState.useBlur,
                                onNotify = notify,
                                onOpenWeb = {
                                    val url = top.youzix.dsha.termux.TermuxController.webUrl()
                                    if (url.isNotEmpty()) {
                                        top.youzix.dsha.ui.web.BrowserState.open(url)
                                        mainPagerState.animateToPage(TAB_WEB)
                                    }
                                },
                            )

                            TAB_WEB -> MaterialWebScreen(
                                outerPadding = outerPadding,
                                useBlur = AppState.useBlur,
                            )

                            TAB_TERMINAL -> MaterialTerminalScreen(
                                outerPadding = outerPadding,
                                useBlur = AppState.useBlur,
                            )

                            TAB_ABOUT -> MaterialAboutScreen(
                                outerPadding = outerPadding,
                                useBlur = AppState.useBlur,
                                themeMode = themeMode,
                                onThemeModeChange = onThemeModeChange,
                                dynamicColor = dynamicColor,
                                onDynamicColorChange = onDynamicColorChange,
                                onOpenLicenses = { subPage = MaterialSubPage.Licenses },
                                onNotify = notify,
                                onOpenWebTest = { mainPagerState.animateToPage(TAB_WEB) },
                            )
                        }
                    }
                }
            },
            subPage = { closeSubPage ->
                LaunchedEffect(subPage) {
                    CrashHandler.note("二级页面 ${subPage?.title ?: ""}")
                }
                when (subPage) {
                    MaterialSubPage.Licenses -> MaterialLicensesScreen(
                        onBack = closeSubPage,
                        useBlur = AppState.useBlur,
                    )

                    null -> Unit
                }
            },
        )

        // The host lives here, not inside the tab Scaffold: a second-level page is drawn over that
        // Scaffold, so a message posted from one used to appear behind it. From the root it sits on
        // top of everything, the way a toast would.
        SwipeableSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = 88.dp +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
        )
    }
}
