/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.termux

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Where the Termux side stands, as far as this app can tell. */
enum class TermuxSetup {
    /** Termux itself is not installed, so nothing can be sent. */
    NOT_INSTALLED,

    /** Termux is installed, but this app does not hold its RUN_COMMAND permission yet. */
    PERMISSION,

    /**
     * Commands can be sent.
     *
     * The runner script travels inside every command, so there is nothing to install first; the one
     * file that does live on disk — the installer — is written by [prepare] without the user having
     * to ask for it.
     */
    READY,
}

/**
 * One observed state of the Termux side, rendered by both UI engines.
 *
 * [setup] and [dshInstalled]/[running] answer different questions — "may I send commands at all"
 * and "what is dsh doing" — and both are needed: a phone with dsh running but the permission
 * revoked still has to show the permission row.
 */
data class TermuxSnapshot(
    val setup: TermuxSetup,
    val termuxVersion: String?,
    val dshVersion: String,
    val dshInstalled: Boolean,
    val running: Boolean,
    val port: Int,
    val url: String,
    val lastError: String?,
    val lastOutput: String,
) {
    /** Whether dsh's web UI is up at a known URL. */
    val hasUrl: Boolean get() = running && url.isNotEmpty()

    companion object {
        val Initial = TermuxSnapshot(
            setup = TermuxSetup.NOT_INSTALLED,
            termuxVersion = null,
            dshVersion = "",
            dshInstalled = false,
            running = false,
            port = TermuxCommands.DEFAULT_PORT,
            url = "",
            lastError = null,
            lastOutput = "",
        )
    }
}

/**
 * The app's entire Termux conversation, in one place.
 *
 * The UI holds no Termux knowledge of its own: it reads [snapshot] and calls [probe], [setup],
 * [start], [stop], [install] and [run]. Both engines therefore behave identically, and the
 * protocol lives in exactly two files — [TermuxBridge] on the wire, this one on the state.
 *
 * **The state lives here, not in the Activity.** A `RUN_COMMAND` reply arrives as a broadcast and
 * the install it may belong to takes minutes, so keeping the state on something that outlives the
 * Activity is what stops a rotation from losing it. It is only ever touched from the main thread:
 * `Application.onCreate`, Compose callbacks and the broadcast receiver all run there.
 */
object TermuxController {

    private const val PREFS = "termux_prefs"
    private const val KEY_PORT = "web_port"

    /** `start` waits up to 40s inside the script for the token URL. */
    private const val START_TIMEOUT_MS = 60_000L

    /** A hand-typed command in the 终端 tab. */
    private const val SHELL_TIMEOUT_MS = 120_000L

    /** 控制台保留多少行 —— DSHA 用 400，同样理由：够往回翻，又不会无限长。 */
    private const val CONSOLE_LIMIT = 400

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var app: Application? = null

    /** Live state. Compose reads it; nothing writes it except this object. */
    var snapshot by mutableStateOf(TermuxSnapshot.Initial)
        private set

    /**
     * Whether a command is in flight, and what it is.
     *
     * Kept beside [snapshot] rather than inside it, the way DSHA keeps them: the terminal page reads
     * these two directly and nothing else, and a page that has to reconstruct "am I busy" from a
     * phase enum is a page that will get it wrong.
     */
    var busy by mutableStateOf(false)
        private set

    var busyLabel by mutableStateOf("")
        private set

    /**
     * The terminal page's scrollback: one entry per command, appended as each finishes.
     *
     * It lives here, not in the page, because the page is disposed every time the pager scrolls
     * away from it — a console whose history vanishes on a tab switch is not a console.
     */
    val consoleLog = mutableStateListOf<String>()

    /** Whether the installer has been written this session. */
    private var installingPrepared = false

    /** 每次进入应用最多自动拉起一次服务，避免反复重试 —— DSHA 的 `autoStartAttempted`。 */
    private var autoStartAttempted = false

    /** Called once from [top.youzix.dsha.DshaApp], so state survives the Activity. */
    fun attach(application: Application) {
        if (app != null) return
        app = application
        val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        snapshot = snapshot.copy(port = prefs.getInt(KEY_PORT, TermuxCommands.DEFAULT_PORT))
        // 唯一需要落盘的东西先写好：安装脚本。它不挡别的命令（那些把脚本内容随命令发过去），
        // 所以失败也不在这里报，等用户真的点「安装」时再说。
        prepare()
        refresh()
    }

    // ------------------------------------------------------------ 状态（DSHA 的 refresh 流程）

    /** 上一次状态脚本读到的 Termux 事实 —— DSHA 的 `applyStatus()` 结果。 */
    var facts by mutableStateOf(TermuxFacts.Unknown)
        private set

    /** 桥接失败的原因，与 [TermuxSnapshot.lastError] 分开存 —— DSHA 就是这么分的。 */
    var bridgeError by mutableStateOf<String?>(null)
        private set

    private var refreshing = false

    /**
     * 重新检测环境与运行状态 —— DSHA 的 `DshaController.refresh()`，逐段照搬。
     *
     * 顺序有讲究：先用本地事实判断「能不能说话」，能说话才发探针，探针通了才读状态。一次刷新
     * 最多两次往返，而且失败会落到具体原因（没装 / 没权限 / 没开 allow-external-apps），
     * 不是笼统的「超时」。
     */
    fun refresh() {
        val context = app ?: return
        if (refreshing) return
        scope.launch {
            refreshing = true
            try {
                val installed = TermuxCommands.isInstalled(context)
                val permitted = installed && TermuxCommands.hasPermission(context)
                snapshot = snapshot.copy(
                    setup = when {
                        !installed -> TermuxSetup.NOT_INSTALLED
                        !permitted -> TermuxSetup.PERMISSION
                        else -> TermuxSetup.READY
                    },
                    termuxVersion = if (installed) TermuxCommands.installedVersion(context) else null,
                )
                if (!installed) {
                    snapshot = snapshot.copy(lastError = null)
                    return@launch
                }
                if (!permitted) {
                    snapshot = snapshot.copy(lastError = "尚未授予 RUN_COMMAND 权限")
                    return@launch
                }
                val probe = TermuxBridge.run(context, Scripts.probe(context), "DSHA-Next 环境检测", 20_000L)
                if (probe.ok && probe.stdout.contains("dsha-bridge-ok")) {
                    bridgeError = null
                    snapshot = snapshot.copy(lastError = null)
                    applyStatus(TermuxBridge.run(context, Scripts.status(context), "DSHA-Next 读取状态", 30_000L))
                    maybeAutoStart()
                } else {
                    // Termux 拒绝外部调用时错误文本里带 allow-external-apps；这不是超时，
                    // 而是一条确切的修复路径，首页会把它显示成可复制的命令。
                    bridgeError = probe.errorText
                    snapshot = snapshot.copy(lastError = probe.errorText)
                }
            } finally {
                refreshing = false
            }
        }
    }

    /** DSHA 的 `applyStatus()`。 */
    private fun applyStatus(result: TermuxBridge.Result) {
        facts = TermuxFacts.from(result.stdout)
        snapshot = snapshot.copy(
            dshInstalled = facts.dshBinAvailable,
            dshVersion = facts.dshVersion.takeIf { it != "-" }.orEmpty(),
            running = facts.serverRunning,
            url = if (facts.serverRunning) "http://127.0.0.1:${TermuxCommands.DEFAULT_PORT}" else "",
        )
    }

    /**
     * 状态已知且开了自动启动时拉起一次服务 —— DSHA 的 `maybeAutoStart()`。
     *
     * 我们还没有「自动启动」这个偏好项；等加上开关时在这里读它即可，刷新流程不用再改。
     */
    private fun maybeAutoStart() {
        if (autoStartAttempted || busy || snapshot.running || !facts.dshBinAvailable) return
        autoStartAttempted = true
        appendConsole("> 自动启动 DSH 服务")
        startServer()
    }

    /**
     * 把安装脚本写进 `~/.dsha` —— 唯一需要落盘的东西。
     *
     * 终端与探针都不需要它（脚本内容随命令发过去），所以失败只记在状态里，
     * 等用户真的点「安装」时才浮出来。
     */
    private fun prepare() {
        val context = app ?: return
        if (installingPrepared) return
        installingPrepared = true
        val source = TermuxCommands.setupSource(context) ?: run {
            installingPrepared = false
            snapshot = snapshot.copy(lastError = "APK 里没有 install-dsh.sh 资源")
            return
        }
        scope.launch {
            val result = TermuxBridge.run(context, source, "写入安装脚本", TermuxCommands.PROBE_TIMEOUT_MS)
            if (!result.ok) installingPrepared = false
        }
    }

    /**
     * 安装 / 更新运行环境 —— DSHA 的 `DshaController.installRuntime()`。
     *
     * 在 Termux 里跑安装脚本；超时给到 40 分钟，因为首次要下载依赖并做原生编译。
     */
    fun installRuntime() = runSetupTask(
        label = "安装 DSH 运行环境（首次约 5~15 分钟）",
        command = { val context = app!!; Scripts.install(context) },
        timeoutMs = 40 * 60 * 1000L,
    )

    /**
     * 启动 DSH Web 服务 —— DSHA 的 `DshaController.startServer()`。
     */
    fun startServer() = runSetupTask(
        label = "启动 DSH 服务",
        command = { val context = app!!; Scripts.start(context) },
        timeoutMs = 3 * 60 * 1000L,
    )

    /**
     * 停止服务 —— DSHA 的 `DshaController.stopServer()`：用户主动停止后复位自动启动标记，
     * 使下次进入应用仍可按偏好自动启动。
     */
    fun stopServer() {
        autoStartAttempted = false
        runSetupTask(
            label = "停止 DSH 服务",
            command = { val context = app!!; Scripts.stop(context) },
            timeoutMs = 60_000L,
        )
    }

    /** 读服务日志进控制台 —— DSHA 的 `DshaController.readServerLog()`。 */
    fun readServerLog() {
        val context = app ?: return
        runConsoleTask("读取 DSH 日志", Scripts.logs(context), 60_000L)
    }

    /**
     * 在 Termux 中执行用户输入的命令，输出进控制台 —— DSHA 的 `DshaController.sendCommand()`。
     *
     * 这是终端页唯一的行为实现：一行命令一次 app-shell 往返，没有 PTY。
     */
    fun sendCommand(raw: String) {
        val context = app ?: return
        val command = raw.trim()
        if (command.isEmpty() || busy) return
        busy = true
        busyLabel = "执行命令"
        appendConsole("$ $command")
        scope.launch {
            try {
                val result = TermuxBridge.run(context, command, "DSHA-Next 控制台", 3 * 60 * 1000L)
                if (result.combined.isNotBlank()) appendConsole(result.combined)
                if (!result.ok) appendConsole("[失败] ${result.errorText}")
            } finally {
                busy = false
                busyLabel = ""
            }
        }
    }

    /** 清空控制台 —— DSHA 的 `DshaController.clearConsole()`。 */
    fun clearConsole() {
        consoleLog.clear()
    }

    /** DSHA 的 `runSetupTask`：会改变状态的命令，按下置忙、结束刷新。 */
    private fun runSetupTask(label: String, command: () -> String, timeoutMs: Long) {
        val context = app ?: return
        if (busy) return
        busy = true
        busyLabel = label
        appendConsole("> $label")
        scope.launch {
            try {
                val result = TermuxBridge.run(context, command(), label, timeoutMs)
                if (result.combined.isNotBlank()) appendConsole(result.combined)
                if (!result.ok) appendConsole("[失败] ${result.errorText}")
            } finally {
                busy = false
                busyLabel = ""
                refresh()
            }
        }
    }

    /** DSHA 的 `runConsoleTask`：只往控制台写，不改状态。 */
    private fun runConsoleTask(label: String, command: String, timeoutMs: Long) {
        val context = app ?: return
        if (busy) return
        busy = true
        busyLabel = label
        scope.launch {
            try {
                val result = TermuxBridge.run(context, command, label, timeoutMs)
                if (result.combined.isNotBlank()) appendConsole(result.combined)
                if (!result.ok) appendConsole("[失败] ${result.errorText}")
            } finally {
                busy = false
                busyLabel = ""
            }
        }
    }

    private fun appendConsole(line: String) {
        consoleLog.add(line)
        while (consoleLog.size > CONSOLE_LIMIT) consoleLog.removeAt(0)
    }

    /** Remembers the port the next `start` should use. */
    fun setPort(context: Context, port: Int) {
        val safe = port.coerceIn(1, 65535)
        snapshot = snapshot.copy(port = safe)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PORT, safe).apply()
    }

    /** The URL to open in the 网页 tab, or an empty string while dsh is down. */
    fun webUrl(): String = if (snapshot.hasUrl) snapshot.url else ""
}
