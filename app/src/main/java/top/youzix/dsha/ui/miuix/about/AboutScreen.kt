/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.miuix.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.dsha.AppState
import top.youzix.dsha.BuildConfig
import top.youzix.dsha.ui.AppIconText
import top.youzix.dsha.ui.AppIcons
import top.youzix.dsha.ui.UiEngine
import top.youzix.dsha.ui.UiEnginePrefs
import top.youzix.dsha.ui.miuix.ThemeModeOptions
import top.youzix.dsha.ui.predictiveback.PredictiveBackStyle
import top.youzix.dsha.util.CrashHandler
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val REPOSITORY_URL = "https://github.com/Youzix-Star/DSHA-Next"

/** How many taps on the subtitle it takes to bring the diagnostic rows out. */
private const val TAPS_TO_UNLOCK = 7

/**
 * 关于 — the settings page and the about page in one.
 *
 * Everything that configures the shell (appearance, engine) sits above everything that only
 * describes it, because this is now the only place either of them can live.
 */
@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    colorSchemeMode: ColorSchemeMode,
    onColorSchemeModeChange: (ColorSchemeMode) -> Unit,
    useLiquidGlass: Boolean,
    onUseLiquidGlassChange: (Boolean) -> Unit,
    engine: UiEngine,
    onEngineChange: (UiEngine) -> Unit,
    onOpenLicenses: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf(CrashHandler.read(context)) }
    var showCrash by remember { mutableStateOf(false) }

    val themeItems = remember { ThemeModeOptions.map { DropdownItem(text = it.second) } }
    val engineItems = remember { UiEngine.entries.map { DropdownItem(text = it.label) } }
    val backStyleItems = remember { PredictiveBackStyle.entries.map { DropdownItem(text = it.label) } }
    val selectedThemeIndex = ThemeModeOptions
        .indexOfFirst { it.first == colorSchemeMode }
        .coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            AppHeader(onNotify = onNotify)
        }

        item(key = "appearance") {
            Column {
                SmallTitle(text = "外观")
                Card(modifier = Modifier.fillMaxWidth()) {
                    WindowSpinnerPreference(
                        title = "主题模式",
                        items = themeItems,
                        selectedIndex = selectedThemeIndex,
                        onSelectedIndexChange = { index ->
                            ThemeModeOptions.getOrNull(index)?.let { onColorSchemeModeChange(it.first) }
                        },
                    )
                    WindowSpinnerPreference(
                        title = "预见式返回动画",
                        summary = "二级页面的返回跟手动画",
                        items = backStyleItems,
                        selectedIndex = PredictiveBackStyle.entries
                            .indexOf(AppState.predictiveBackStyle).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            PredictiveBackStyle.entries.getOrNull(index)?.let {
                                AppState.predictiveBackStyle = it
                                UiEnginePrefs.savePredictiveBackStyle(context, it)
                            }
                        },
                    )
                    SwitchPreference(
                        title = "液态玻璃底栏",
                        summary = "底栏实时模糊与高光",
                        checked = useLiquidGlass,
                        onCheckedChange = onUseLiquidGlassChange,
                    )
                }
            }
        }

        item(key = "engine") {
            Column {
                SmallTitle(text = "引擎")
                Card(modifier = Modifier.fillMaxWidth()) {
                    WindowSpinnerPreference(
                        title = "界面引擎",
                        summary = "切换整套界面实现",
                        items = engineItems,
                        selectedIndex = UiEngine.entries.indexOf(engine).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            UiEngine.entries.getOrNull(index)?.let(onEngineChange)
                        },
                    )
                }
            }
        }

        item(key = "about") {
            Column {
                SmallTitle(text = "关于")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "获取源代码",
                        summary = "GitHub 上的源码",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.SourceCode,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = { uriHandler.openUri(REPOSITORY_URL) },
                    )
                    ArrowPreference(
                        title = "开源许可",
                        summary = "依赖的许可证",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.License,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = onOpenLicenses,
                    )
                    ArrowPreference(
                        title = "崩溃日志",
                        summary = if (crashLog.isNullOrBlank()) "没有记录" else "有一条记录，点按查看",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Log,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = {
                            crashLog = CrashHandler.read(context)
                            showCrash = true
                        },
                    )
                }
            }
        }

        // Emitted from composable scope so the switch is actually observed: a read inside the
        // LazyListScope lambda above would not recompose this list.
        item(key = "debug") {
            if (AppState.debugMode) {
                Column {
                    SmallTitle(text = "调试")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        // Verifying the crash screen needs a crash, and waiting for a real bug to
                        // happen is not a test. Deliberately thrown on the main thread so the
                        // uncaught handler (and the report screen) see it exactly like a real one.
                        ArrowPreference(
                            title = "模拟崩溃",
                            summary = "让应用崩一次，看看崩溃报告页长什么样",
                            startAction = {
                                Icon(
                                    imageVector = AppIcons.Debug,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            onClick = { throw IllegalStateException("模拟崩溃：这是调试里手动触发的") },
                        )
                    }
                }
            }
        }
    }

    if (showCrash) {
        val report = crashLog
        OverlayDialog(
            show = true,
            title = "崩溃日志",
            onDismissRequest = { showCrash = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (report.isNullOrBlank()) {
                    Text("没有崩溃记录。")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = report,
                            style = MiuixTheme.textStyles.footnote1,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(report))
                            onNotify("已复制崩溃日志")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("复制")
                    }
                    Button(
                        onClick = {
                            CrashHandler.clear(context)
                            crashLog = null
                            showCrash = false
                            onNotify("已清空")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("清空")
                    }
                }
                Button(onClick = { showCrash = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭")
                }
            }
        }
    }
}

/**
 * The brand block, and the way into the diagnostic rows.
 *
 * Seven taps on the subtitle is the only door to debug mode: nothing on the page advertises it,
 * which is the point.
 */
@Composable
private fun AppHeader(onNotify: (String) -> Unit) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The mark is drawn as text, not as the launcher bitmap: the launcher resource is an
        // adaptive icon, which `painterResource` cannot load. Text also follows the theme's ink
        // instead of baking one in.
        Text(
            text = AppIconText,
            fontSize = 52.sp,
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "DSHA-Next",
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ciallo～(∠・ω c)⌒★",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable {
                taps += 1
                if (taps >= TAPS_TO_UNLOCK) {
                    taps = 0
                    AppState.debugMode = !AppState.debugMode
                    UiEnginePrefs.saveDebugMode(context, AppState.debugMode)
                    onNotify(if (AppState.debugMode) "调试模式已开启" else "调试模式已关闭")
                }
            },
        )
    }
}
