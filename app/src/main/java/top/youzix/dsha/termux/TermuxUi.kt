/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.termux

/**
 * One button the 首页 can show, decided here rather than in either engine.
 *
 * Both engines draw the same list from [homeActions], so "which buttons exist on a phone with dsh
 * installed but Termux's permission revoked" is answered once. Each engine only chooses how a
 * button looks and what an accent is called.
 */
data class BridgeAction(
    val id: String,
    val label: String,
    val detail: String,
    /** The one action the state is actually asking for; engines render it as the accent button. */
    val primary: Boolean,
    val enabled: Boolean,
    val run: () -> Unit,
)

/**
 * The one line that turns `allow-external-apps` on, quoted from DSHA's guide.
 *
 * It is shown as the label of a (deliberately inert) action so both engines render it in their own
 * code-block styling; the user copies it into Termux.
 */
const val ALLOW_EXTERNAL_APPS_COMMAND =
    "echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings"

/** How a status line should read, in a tone both engines can map onto their own palette. */
enum class StatusTone { OK, BUSY, IDLE, WARN, BAD }

/** The headline state of the Termux side, as the 首页 card shows it. */
data class BridgeStatus(
    val tone: StatusTone,
    val headline: String,
    val detail: String,
)

/**
 * The whole 首页 state: one headline, one paragraph, and the buttons.
 *
 * Pure data and lambdas — no Compose, no Android — so it is the one part of this feature that can
 * be unit-tested on CI without a device.
 */
data class BridgeHome(
    val status: BridgeStatus,
    val actions: List<BridgeAction>,
    val url: String,
    val canOpenWeb: Boolean,
    /**
     * A command the user has to run in Termux themselves, when the app cannot finish the job.
     *
     * Only one thing produces it: `allow-external-apps`. Every other prerequisite can be satisfied
     * from here, but that switch lives in Termux's private storage.
     */
    val remedy: String? = null,
)

/**
 * Turns a snapshot into what 首页 shows.
 *
 * The ordering is deliberate: a phone that cannot run commands at all is told *that* first, even
 * if a stale probe still remembers a running dsh.
 */
fun homeFrom(
    snapshot: TermuxSnapshot,
    onOpenWeb: () -> Unit,
    onProbe: () -> Unit = { TermuxController.probe() },
    onSetup: () -> Unit = { TermuxController.setup() },
    onInstall: () -> Unit = { TermuxController.install() },
    onStart: () -> Unit = { TermuxController.start() },
    onStop: () -> Unit = { TermuxController.stop() },
    onReadLog: () -> Unit = { TermuxController.readLog() },
    onOpenTermux: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
): BridgeHome {
    val busy = snapshot.phase == TermuxPhase.BUSY
    val actions = mutableListOf<BridgeAction>()

    fun action(
        id: String,
        label: String,
        detail: String,
        primary: Boolean = false,
        enabled: Boolean = !busy,
        run: () -> Unit,
    ) {
        actions += BridgeAction(id, label, detail, primary, enabled, run)
    }

    val status = when (snapshot.setup) {
        TermuxSetup.NOT_INSTALLED -> BridgeStatus(
            tone = StatusTone.WARN,
            headline = "没有找到 Termux",
            detail = "这个应用自己不执行命令：它把安装、启动、停止交给手机上的 Termux。" +
                "请先安装 Termux（F-Droid 或 GitHub 版本），装好后再回来点「重新检测」。",
        )

        TermuxSetup.PERMISSION -> BridgeStatus(
            tone = StatusTone.WARN,
            headline = "Termux 还没授权给本应用",
            detail = "Termux 用一条受保护的权限 com.termux.permission.RUN_COMMAND 决定谁可以让它" +
                "执行命令。这条权限由 Termux 声明、属于 dangerous 级别，所以要由你点一下允许。",
        )

        TermuxSetup.SCRIPTS_MISSING -> BridgeStatus(
            tone = StatusTone.IDLE,
            headline = "还没有准备 Termux 侧脚本",
            detail = "点下面的「准备」会在 Termux 的 ~/.dsha 里写入一个启动脚本 " +
                "（run.sh）和一份安装脚本。这个应用只能请 Termux 自己写自己的目录，" +
                "所以这一步必须由 Termux 执行。",
        )

        TermuxSetup.READY -> when {
            !snapshot.dshInstalled -> BridgeStatus(
                tone = StatusTone.IDLE,
                headline = "Termux 就绪，dsh 还没装",
                detail = "点「安装 dsh」会在 Termux 里打开一个会话，用 DSHA-Next-Shell 的" +
                    "安装脚本装上原版 @deepseek-ai/dsh。要编译原生模块，大约 2～10 分钟。",
            )

            snapshot.running -> BridgeStatus(
                tone = StatusTone.OK,
                headline = "dsh 正在运行",
                detail = if (snapshot.url.isNotEmpty()) {
                    "网页界面在 ${snapshot.url.substringBefore("/?")}，点「打开网页界面」直接进去。"
                } else {
                    "进程在跑，但还没有从日志里读到带 token 的地址；可以点「查看日志」。"
                },
            )

            else -> BridgeStatus(
                tone = StatusTone.IDLE,
                headline = "dsh 已就绪，当前没有运行",
                detail = "点「启动 dsh」会后台拉起 dsh web（默认端口 ${snapshot.port}）并在日志里" +
                    "拿到带 token 的地址。",
            )
        }
    }

    when (snapshot.setup) {
        TermuxSetup.NOT_INSTALLED -> {
            // Nothing can be sent at all here, so the accent button is the one that helps:
            // getting Termux installed. Whether it opens F-Droid or the project page is the
            // engine's business, so the callback is supplied by the caller.
            action("probe", "重新检测", "重新看看 Termux 装了没有") { onProbe() }
            action("openTermux", "去装 Termux", "打开应用商店页面或浏览器", primary = true) { onOpenTermux() }
        }

        TermuxSetup.PERMISSION -> {
            action("probe", "重新检测", "授权之后点这里刷新状态") { onProbe() }
            action(
                id = "requestPermission",
                label = "请求权限",
                detail = "弹出系统授权框（Termux 必须已安装）",
                primary = true,
            ) { onRequestPermission() }
        }

        TermuxSetup.SCRIPTS_MISSING -> {
            action("probe", "重新检测", "看看脚本是不是已经在了") { onProbe() }
            action("setup", "准备", "写入 ~/.dsha/run.sh 与安装脚本", primary = true) { onSetup() }
        }

        TermuxSetup.READY -> {
            action("probe", "重新检测", "重新读取 dsh 的安装与运行状态") { onProbe() }
            if (snapshot.dshInstalled) {
                action("log", "查看日志", "打印 ~/.dsha/web.log 的尾部") { onReadLog() }
                action(
                    id = "install",
                    label = "重装 / 升级 dsh",
                    detail = "在 Termux 里重跑一遍安装脚本",
                ) { onInstall() }
                if (snapshot.running) {
                    action("stop", "停止 dsh", "结束 dsh web 进程") { onStop() }
                } else {
                    action("start", "启动 dsh", "后台启动 dsh web 并取回带 token 的地址", primary = true) { onStart() }
                }
            } else {
                action("install", "安装 dsh", "在 Termux 里跑 DSHA-Next-Shell 的安装脚本", primary = true) { onInstall() }
            }
        }
    }

    if (snapshot.canOpenWebNow) {
        action("openWeb", "打开网页界面", snapshot.webUrlSummary, primary = true) { onOpenWeb() }
    }

    // Termux 拒绝外部调用时，错误文本里带 allow-external-apps。这不是超时，而是一条确切的
    // 修复路径 —— 而它只能由用户在 Termux 里执行：那个文件在 Termux 的私有目录里。
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
            )
        },
        actions = actions,
        url = snapshot.url,
        canOpenWeb = snapshot.canOpenWebNow,
        remedy = remedy,
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
 * each command is one `app-shell` round trip, and the output is what came back. Saying so is
 * cheaper than a user discovering that `vim` cannot work.
 */
fun terminalWelcome(snapshot: TermuxSnapshot): String = buildString {
    append("DSHA-Next 终端\n")
    append("每一行命令都是一次 Termux app-shell 调用，没有交互式 TTY。\n")
    when (snapshot.setup) {
        TermuxSetup.NOT_INSTALLED -> append("状态：没有找到 Termux。\n")
        TermuxSetup.PERMISSION -> append("状态：Termux 还没授权给本应用。\n")
        TermuxSetup.SCRIPTS_MISSING -> append("状态：还没有准备 ~/.dsha/run.sh。\n")
        TermuxSetup.READY -> append("命令在 ${TermuxBridge.PREFIX}/bin 前置的 PATH 里执行；默认工作目录是 Termux 的 home。\n")
    }
}

/** The commands offered above the input, when dsh is installed. */
fun terminalShortcuts(snapshot: TermuxSnapshot): List<Pair<String, String>> = buildList {
    add("dsh --version" to "版本")
    if (snapshot.dshInstalled) {
        add("dsh web --help" to "dsh web 选项")
        add("pgrep -af 'lib/bin.js web'" to "看 dsh 进程")
        add("tail -n 60 ~/.dsha/web.log" to "web 日志")
    }
    add("ls -a ~/.dsha" to "看 ~/.dsha")
    add("pkg list-installed | head -20" to "已装包")
}
