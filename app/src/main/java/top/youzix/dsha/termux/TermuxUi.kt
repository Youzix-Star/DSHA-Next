/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.termux

/**
 * The one line that turns `allow-external-apps` on.
 *
 * Quoted from DSHA's guide, because it is the one prerequisite this app cannot satisfy itself: the
 * file lives in Termux's private storage. Shown as a copy-me block, not as an action.
 */
const val ALLOW_EXTERNAL_APPS_COMMAND =
    "echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings"

/** How a status line should read, in a tone both engines can map onto their own palette. */
enum class StatusTone { OK, BUSY, IDLE, WARN, BAD }

/**
 * The main status block on 首页.
 *
 * Shaped like DSHA's `StatusCard`: a coloured panel that says what is happening, with the action that
 * changes it attached to the panel itself rather than listed underneath. `headline` is the answer,
 * `detail` the evidence, `hint` what a tap does.
 */
data class BridgeStatus(
    val tone: StatusTone,
    val headline: String,
    val detail: String,
    val hint: String,
    val action: (() -> Unit)?,
)

/** A one-line row under the main block: whether a prerequisite is satisfied. */
data class BridgeRow(
    val id: String,
    val title: String,
    val summary: String,
    val active: Boolean,
    val hint: String,
    val action: (() -> Unit)?,
)

/** One cell of the three-cell statistics row. */
data class BridgeStat(val title: String, val value: String)

/**
 * Everything 首页 draws.
 *
 * Pure data and lambdas — no Compose, no Android — which is what makes the whole page's logic
 * testable on CI without a device, and what keeps the two engines from disagreeing about which
 * actions exist.
 */
data class BridgeHome(
    val status: BridgeStatus,
    val rows: List<BridgeRow>,
    val stats: List<BridgeStat>,
    val buttons: List<BridgeAction>,
    /** A command the user has to run in Termux themselves; only `allow-external-apps` produces one. */
    val remedy: String?,
    val canOpenWeb: Boolean,
)

/** One secondary action, drawn as a button inside the main panel. */
data class BridgeAction(
    val id: String,
    val label: String,
    val primary: Boolean,
    val enabled: Boolean,
    val run: () -> Unit,
)

/**
 * Turns a snapshot into what 首页 shows.
 *
 * The order of the branches is the order of the questions a user actually has: can I talk to Termux
 * at all, is dsh installed, is it running. A phone with dsh running but the permission revoked gets
 * told about the permission — the stale "running" is not the current problem.
 */
fun homeFrom(
    snapshot: TermuxSnapshot,
    onOpenWeb: () -> Unit,
    onProbe: () -> Unit = { TermuxController.probe() },
    onInstall: () -> Unit = { TermuxController.install() },
    onStart: () -> Unit = { TermuxController.start() },
    onStop: () -> Unit = { TermuxController.stop() },
    onReadLog: () -> Unit = { TermuxController.readLog() },
    onOpenTermux: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
): BridgeHome {
    val busy = snapshot.phase == TermuxPhase.BUSY
    val ready = snapshot.setup == TermuxSetup.READY

    fun button(id: String, label: String, primary: Boolean = false, enabled: Boolean = !busy, run: () -> Unit) =
        BridgeAction(id, label, primary, enabled, run)

    // ---- the main block: one question, one answer, one action -------------------------------
    val status = when {
        snapshot.setup == TermuxSetup.NOT_INSTALLED -> BridgeStatus(
            tone = StatusTone.WARN,
            headline = "需要先装 Termux",
            detail = "这个应用自己不执行命令：安装、启动、停止都由手机上的 Termux 代劳。" +
                "请装 F-Droid 版（不要用 Google Play 版），装好回来点一下。",
            hint = "点击获取 Termux",
            action = onOpenTermux,
        )

        snapshot.setup == TermuxSetup.PERMISSION -> BridgeStatus(
            tone = StatusTone.WARN,
            headline = "Termux 还没授权",
            detail = "Termux 用一条叫 RUN_COMMAND 的权限决定谁可以让它执行命令。" +
                "这条权限由 Termux 声明，需要你点一下允许。",
            hint = "点击请求权限",
            action = onRequestPermission,
        )

        !snapshot.dshInstalled -> BridgeStatus(
            tone = StatusTone.WARN,
            headline = "dsh 还没安装",
            detail = "Termux 已连通。安装会用到 DSHA-Next-Shell 的安装脚本，" +
                "要编译原生模块，大约 2～10 分钟，进度在 Termux 窗口里看。",
            hint = "点击开始安装",
            action = onInstall,
        )

        snapshot.running -> BridgeStatus(
            tone = StatusTone.OK,
            headline = "dsh 正在运行",
            detail = if (snapshot.url.isNotEmpty()) {
                "网页界面：${snapshot.url.substringBefore("/?")}"
            } else {
                "进程在跑，但日志里还没有带 token 的地址，可以点「日志」看看。"
            },
            hint = "点击停止",
            action = onStop,
        )

        else -> BridgeStatus(
            tone = StatusTone.IDLE,
            headline = "dsh 已就绪，当前没在运行",
            detail = "点一下会后台启动 dsh web（端口 ${snapshot.port}），" +
                "并把日志里带 token 的地址取回来。",
            hint = "点击启动",
            action = onStart,
        )
    }

    // ---- the two prerequisite rows ----------------------------------------------------------
    val rows = listOf(
        BridgeRow(
            id = "bridge",
            title = if (ready) "Termux 已连通" else "Termux 未连通",
            summary = when (snapshot.setup) {
                TermuxSetup.NOT_INSTALLED -> "没有检测到 com.termux"
                TermuxSetup.PERMISSION -> "缺少 RUN_COMMAND 权限"
                TermuxSetup.READY -> "命令可以下发到本机 Termux"
            },
            active = ready,
            hint = if (ready) "已连通" else "去修复",
            action = if (ready) onProbe else onRequestPermission,
        ),
        BridgeRow(
            id = "runtime",
            title = if (snapshot.dshInstalled) "运行环境已就绪" else "运行环境未安装",
            summary = if (snapshot.dshInstalled) {
                "dsh ${snapshot.dshVersion.ifEmpty { "已安装" }}"
            } else {
                "缺少 dsh 命令"
            },
            active = snapshot.dshInstalled,
            hint = if (snapshot.dshInstalled) "已就绪" else "去安装",
            action = if (snapshot.dshInstalled) onProbe else onInstall,
        ),
    )

    // ---- three cells, readable at a glance --------------------------------------------------
    val stats = listOf(
        BridgeStat("服务", if (snapshot.running) "运行中" else "已停止"),
        BridgeStat("桥接", if (ready) "已连通" else "未连通"),
        BridgeStat("环境", if (snapshot.dshInstalled) "已就绪" else "未安装"),
    )

    // ---- secondary actions ------------------------------------------------------------------
    val buttons = buildList {
        add(button("probe", "重新检测", enabled = !busy) { onProbe() })
        if (snapshot.setup == TermuxSetup.NOT_INSTALLED) {
            add(button("openTermux", "获取 Termux", primary = true) { onOpenTermux() })
        }
        if (snapshot.setup == TermuxSetup.PERMISSION) {
            add(button("requestPermission", "请求权限", primary = true) { onRequestPermission() })
        }
        if (ready && snapshot.dshInstalled) {
            if (snapshot.canOpenWebNow) add(button("openWeb", "打开网页界面", primary = true) { onOpenWeb() })
            add(button("log", "日志") { onReadLog() })
            add(button("reinstall", "重装 / 升级") { onInstall() })
            if (snapshot.running) add(button("stop", "停止") { onStop() }) else add(button("start", "启动", primary = true) { onStart() })
        }
    }

    // Termux answers a refused command with an error that names this property; that is the one
    // failure the app cannot fix from here, so it says exactly what to run instead.
    val remedy = if (snapshot.lastError?.contains("allow-external-apps") == true) {
        ALLOW_EXTERNAL_APPS_COMMAND
    } else {
        null
    }

    return BridgeHome(
        status = if (remedy == null) {
            status
        } else {
            status.copy(
                tone = StatusTone.BAD,
                headline = "Termux 拒绝了外部调用",
                detail = "Termux 的 allow-external-apps 没开。这个开关在 Termux 自己的私有目录里，" +
                    "别的应用写不进去，只能在 Termux 里执行下面这行命令，然后回来点「重新检测」。",
                hint = "只能在 Termux 里开",
                action = null,
            )
        },
        rows = rows,
        stats = stats,
        buttons = buttons,
        remedy = remedy,
        canOpenWeb = snapshot.canOpenWebNow,
    )
}

/** Whether the URL is worth offering to the 网页 tab right now. */
val TermuxSnapshot.canOpenWebNow: Boolean get() = setup == TermuxSetup.READY && hasUrl

/** Just the authority of the token URL — the token itself is not something to print on 首页. */
val TermuxSnapshot.webUrlSummary: String
    get() = url.substringBefore("/?").ifEmpty { "http://127.0.0.1:$port" }

/**
 * The 终端 tab's welcome text.
 *
 * It says what the tab is rather than pretending to be a terminal emulator: there is no PTY here,
 * each line is one `app-shell` round trip, and the output is what came back. Cheaper than a user
 * discovering that `vim` cannot work by trying it.
 */
fun terminalWelcome(snapshot: TermuxSnapshot): String = buildString {
    append("DSHA-Next 终端\n")
    append("每一行命令都是一次 Termux app-shell 调用，没有交互式 TTY。\n")
    when (snapshot.setup) {
        TermuxSetup.NOT_INSTALLED -> append("状态：没有找到 Termux。\n")
        TermuxSetup.PERMISSION -> append("状态：Termux 还没授权给本应用。\n")
        TermuxSetup.READY -> append("状态：命令可以直接执行，不需要任何准备步骤。\n")
    }
}

/** The commands offered above the input. */
fun terminalShortcuts(snapshot: TermuxSnapshot): List<String> = buildList {
    add("dsh --version")
    if (snapshot.dshInstalled) {
        add("dsh web --help")
        add("pgrep -af 'lib/bin.js web'")
        add("tail -n 60 ~/.dsha/web.log")
    }
    add("ls -a ~/.dsha")
    add("node -v")
}
