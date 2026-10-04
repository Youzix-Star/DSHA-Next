/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.dsha.ui.material3.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import top.youzix.dsha.AppState
import top.youzix.dsha.BuildConfig
import top.youzix.dsha.ui.AppMark
import top.youzix.dsha.ui.AppIcons
import top.youzix.dsha.ui.UiEngine
import top.youzix.dsha.ui.UiEnginePrefs
import top.youzix.dsha.ui.material3.ThemeMode
import top.youzix.dsha.ui.material3.material3AppBarColor
import top.youzix.dsha.ui.material3.material3BlurEffect
import top.youzix.dsha.ui.material3.rememberMaterial3BlurBackdrop
import top.youzix.dsha.ui.material3.widgets.DropDownMenuWidget
import top.youzix.dsha.ui.material3.widgets.NavigationItemWidget
import top.youzix.dsha.ui.material3.widgets.SegmentedColumn
import top.youzix.dsha.ui.material3.widgets.SwitchWidget
import top.youzix.dsha.ui.predictiveback.PredictiveBackStyle
import top.youzix.dsha.ui.web.BrowserState
import top.youzix.dsha.ui.web.WEB_TEST_URL
import top.youzix.dsha.util.CrashHandler
import top.youzix.dsha.util.DeviceInfo
import top.youzix.dsha.util.UpdateChecker
import top.youzix.dsha.util.WebLog
import top.youzix.dsha.util.UpdateResult
import top.yukonga.miuix.kmp.blur.layerBackdrop

private const val REPOSITORY_URL = "https://github.com/Youzix-Star/DSHA-Next"

/** Taps on the signature line that put the diagnostic rows on show. */
private const val DEBUG_TAPS = 7

/**
 * The merged settings and about page: how the app looks, which engine draws it, and what it is
 * built on.
 */
@Composable
fun MaterialAboutScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenWebTest: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf(CrashHandler.read(context)) }
    var showCrash by remember { mutableStateOf(false) }
    // Held as the "available" case rather than the whole result: the dialog only exists when there
    // is something to offer, so a plain Boolean for that would be a second source of truth.
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateResult.Available?>(null) }
    // Held the same way as the update result: null means there is nothing to show, so the dialog
    // needs no second Boolean to say whether it is open.
    var deviceInfo by remember { mutableStateOf<String?>(null) }
    var webLog by remember { mutableStateOf<String?>(null) }
    var showWebLog by remember { mutableStateOf(false) }
    // Read above the list, not inside it: the groups are declared by a non-composable DSL lambda,
    // so a value read down there would not be what brings this page back when it changes.
    val debugMode = AppState.debugMode
    val engine = AppState.engine
    val predictiveBackStyle = AppState.predictiveBackStyle
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("关于", modifier = Modifier.padding(start = 12.dp)) },
                scrollBehavior = scrollBehavior,
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
            contentPadding = paddingValues + outerPadding,
        ) {
            item { AppHeader(onNotify = onNotify) }

            item {
                SegmentedColumn(title = "外观") {
                    item {
                        DropDownMenuWidget(
                            title = "主题模式",
                            choice = ThemeMode.entries.indexOf(themeMode).coerceAtLeast(0),
                            data = ThemeMode.entries.map { it.label },
                            onChoiceChange = { index ->
                                ThemeMode.entries.getOrNull(index)?.let(onThemeModeChange)
                            },
                        )
                    }
                    item {
                        SwitchWidget(
                            title = "动态取色",
                            description = "跟随壁纸取色",
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange,
                        )
                    }
                    item {
                        DropDownMenuWidget(
                            icon = AppIcons.Back,
                            title = "预见式返回动画",
                            description = "二级页面返回时的跟手动画",
                            choice = PredictiveBackStyle.entries
                                .indexOf(predictiveBackStyle).coerceAtLeast(0),
                            data = PredictiveBackStyle.entries.map { it.label },
                            onChoiceChange = { index ->
                                PredictiveBackStyle.entries.getOrNull(index)?.let {
                                    AppState.predictiveBackStyle = it
                                    UiEnginePrefs.savePredictiveBackStyle(context, it)
                                }
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "引擎") {
                    item {
                        DropDownMenuWidget(
                            title = "界面引擎",
                            description = "切换整套界面实现",
                            choice = UiEngine.entries.indexOf(engine).coerceAtLeast(0),
                            data = UiEngine.entries.map { it.label },
                            onChoiceChange = { index ->
                                UiEngine.entries.getOrNull(index)?.let {
                                    AppState.engine = it
                                    UiEnginePrefs.save(context, it)
                                }
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "关于") {
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.SourceCode,
                            title = "获取源代码",
                            description = "GitHub 上的源码",
                            onClick = { uriHandler.openUri(REPOSITORY_URL) },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.License,
                            title = "开源许可",
                            description = "依赖的许可证",
                            onClick = onOpenLicenses,
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Log,
                            title = "崩溃日志",
                            description = if (crashLog.isNullOrBlank()) {
                                "没有记录"
                            } else {
                                "有一条记录，点按查看"
                            },
                            onClick = {
                                crashLog = CrashHandler.read(context)
                                showCrash = true
                            },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Refresh,
                            title = "检查更新",
                            description = if (checking) "检查中…" else "当前 v${BuildConfig.VERSION_NAME}",
                            onClick = {
                                // The check runs off the main thread and answers on it, so the row
                                // is only closed to further taps: it must not queue a second one.
                                if (!checking) {
                                    checking = true
                                    UpdateChecker.check { result ->
                                        checking = false
                                        when (result) {
                                            is UpdateResult.Available -> update = result
                                            UpdateResult.UpToDate -> onNotify("已是最新版本")
                                            is UpdateResult.Failed ->
                                                onNotify("检查失败：" + result.message)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }

            if (debugMode) {
                item {
                    SegmentedColumn(title = "调试") {
                        // Verifying the crash screen needs a crash, and waiting for a real bug to
                        // happen is not a test. Deliberately thrown on the main thread so the
                        // uncaught handler (and the report screen) see it exactly like a real one.
                        item {
                            NavigationItemWidget(
                                icon = AppIcons.Debug,
                                title = "模拟崩溃",
                                description = "让应用崩一次，看看崩溃报告页长什么样",
                                onClick = { throw IllegalStateException("模拟崩溃：这是调试里手动触发的") },
                            )
                        }
                        // The snapshot is taken at tap time, never during composition: the
                        // snapshot time inside the text only means something if it is the moment
                        // the row was pressed. Setting it is also what opens the dialog.
                        item {
                            NavigationItemWidget(
                                icon = AppIcons.Phones,
                                title = "设备信息",
                                description = "机型、系统、WebView 与 UA",
                                onClick = { deviceInfo = DeviceInfo.snapshot(context) },
                            )
                        }
                        item {
                            NavigationItemWidget(
                                icon = AppIcons.Web,
                                title = "网页自检",
                                description = "打开测试页：内联 / 同目录 / https / http 四路 CSS",
                                onClick = {
                                    BrowserState.open(WEB_TEST_URL)
                                    onOpenWebTest()
                                },
                            )
                        }
                        item {
                            NavigationItemWidget(
                                icon = AppIcons.Log,
                                title = "网页日志",
                                description = "WebView 拦下或加载失败的资源，以及 console 的报错",
                                onClick = {
                                    webLog = WebLog.read(context)
                                    showWebLog = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val report = crashLog
    if (showCrash) {
        AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text("崩溃日志") },
            text = {
                if (report.isNullOrBlank()) {
                    Text("没有崩溃记录。")
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = report,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            },
            confirmButton = {
                if (!report.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(report))
                            onNotify("已复制崩溃日志")
                        },
                    ) {
                        Text("复制")
                    }
                }
            },
            dismissButton = {
                if (report.isNullOrBlank()) {
                    TextButton(onClick = { showCrash = false }) { Text("关闭") }
                } else {
                    TextButton(
                        onClick = {
                            CrashHandler.clear(context)
                            crashLog = null
                            showCrash = false
                            onNotify("已清空")
                        },
                    ) {
                        Text("清空")
                    }
                }
            },
        )
    }

    val log = webLog
    if (showWebLog) {
        AlertDialog(
            onDismissRequest = { showWebLog = false },
            title = { Text("网页日志") },
            text = {
                if (log.isNullOrBlank()) {
                    Text("还没有记录。先去「网页」页签打开出问题的网址，再回来看。")
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = log,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Re-read rather than copying the snapshot the dialog opened with: the
                        // stylesheet count is written right after the page finishes, i.e. while
                        // this dialog is already on screen.
                        val fresh = WebLog.read(context).orEmpty()
                        if (fresh.isNotBlank()) {
                            clipboard.setText(AnnotatedString(fresh))
                            webLog = fresh
                            onNotify("已复制网页日志")
                        }
                        showWebLog = false
                    },
                ) {
                    Text("复制")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        WebLog.clear(context)
                        webLog = null
                        showWebLog = false
                    },
                ) {
                    Text("清空")
                }
            },
        )
    }

    val info = deviceInfo
    if (info != null) {
        AlertDialog(
            onDismissRequest = { deviceInfo = null },
            title = { Text("设备信息") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = info,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(info))
                        onNotify("已复制设备信息")
                        deviceInfo = null
                    },
                ) {
                    Text("复制")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceInfo = null }) { Text("关闭") }
            },
        )
    }

    val available = update
    if (available != null) {
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("发现新版本 v${available.version}") },
            text = {
                if (available.notes.isBlank()) {
                    Text("这个版本没有写更新说明。")
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = available.notes,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = available.apkUrl
                        if (target != null) {
                            uriHandler.openUri(target)
                        } else {
                            UpdateChecker.openReleasePage(context)
                        }
                        update = null
                    },
                ) {
                    Text(if (available.apkUrl != null) "下载" else "打开发布页")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        UpdateChecker.openReleasePage(context)
                        update = null
                    },
                ) {
                    Text("在浏览器中查看")
                }
            },
        )
    }
}

@Composable
private fun AppHeader(onNotify: (String) -> Unit) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The moving app mark. Its size is left to the design language's 104dp rather than the
        // width of the column, so it stays a mark and not a banner; the column centres it.
        AppMark(
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = "DSHA-Next", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Seven taps on the signature line put the diagnostic rows on show; nothing says so.
        Text(
            text = "Ciallo～(∠・ω c)⌒★",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable {
                taps += 1
                if (taps >= DEBUG_TAPS) {
                    taps = 0
                    val enabled = !AppState.debugMode
                    AppState.debugMode = enabled
                    UiEnginePrefs.saveDebugMode(context, enabled)
                    onNotify(if (enabled) "调试模式已开启" else "调试模式已关闭")
                }
            },
        )
    }
}
