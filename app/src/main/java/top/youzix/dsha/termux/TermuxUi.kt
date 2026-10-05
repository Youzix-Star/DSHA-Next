/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * 首页那点状态文案。
 *
 * 只读。终端那一页现在是 Termux 的画面（见 ui/terminal 下的三个文件），不再执行命令，
 * 所以首页也不提供任何会执行命令的入口：一个按下去就能以 Termux 身份执行任意命令的按钮，
 * 和「先不整一键执行」是矛盾的。这里只把看到的事实说清楚。
 */

package top.youzix.dsha.termux

/** 状态面板的语气，两个引擎各自映射到自己的配色。 */
enum class StatusTone { OK, BUSY, IDLE, WARN, BAD }

/** 首页要画的一切。纯数据，没有动作、没有 lambda。 */
data class BridgeHome(
    val status: BridgeStatus,
    val rows: List<BridgeRow>,
    val stats: List<BridgeStat>,
    val canOpenWeb: Boolean,
)

/** 主面板：一句话说清现在什么情况。 */
data class BridgeStatus(
    val tone: StatusTone,
    val headline: String,
    val detail: String,
    val hint: String,
)

/** 主面板下面的一行前置条件。 */
data class BridgeRow(
    val id: String,
    val title: String,
    val summary: String,
    val active: Boolean,
    val hint: String,
)

/** 三格统计里的一格。 */
data class BridgeStat(val title: String, val value: String)

/**
 * 首页该显示什么。
 *
 * 分支顺序就是用户会问的顺序：能不能跟 Termux 说话、dsh 装没装、在不在跑。权限被撤销时，
 * 即使上一次探测还记得「在跑」，也该先报权限，那才是当前挡在前面的问题。
 */
fun statusFrom(snapshot: TermuxSnapshot): BridgeStatus = when {
    snapshot.setup == TermuxSetup.NOT_INSTALLED -> BridgeStatus(
        tone = StatusTone.WARN,
        headline = "没有找到 Termux",
        detail = "这个应用自己不执行命令：它把要做的事交给手机上的 Termux。" +
            "请安装 F-Droid 版（不要用 Google Play 版）。",
        hint = "装好后回到本页会自动检测",
    )

    snapshot.setup == TermuxSetup.PERMISSION -> BridgeStatus(
        tone = StatusTone.WARN,
        headline = "Termux 还没授权",
        detail = "Termux 用一条叫 RUN_COMMAND 的权限决定谁可以让它执行命令。" +
            "这条权限由 Termux 声明，需要你点一下允许。",
        hint = "授权后回到本页会自动检测",
    )

    !snapshot.dshInstalled -> BridgeStatus(
        tone = StatusTone.WARN,
        headline = "dsh 还没安装",
        detail = "Termux 已连通，但还没有 dsh 命令。",
        hint = "在 Termux 里装好 dsh",
    )

    snapshot.running -> BridgeStatus(
        tone = StatusTone.OK,
        headline = "dsh 正在运行",
        detail = "网页界面：${snapshot.webUrlSummary}",
        hint = "可在网页页签打开",
    )

    else -> BridgeStatus(
        tone = StatusTone.IDLE,
        headline = "dsh 已就绪，当前没在运行",
        detail = "dsh 命令在，服务没起。本版只做展示，不代为启停。",
        hint = "在 Termux 里执行 dsh web",
    )
}

/** 两行前置条件。 */
fun rowsFrom(snapshot: TermuxSnapshot): List<BridgeRow> {
    val ready = snapshot.setup == TermuxSetup.READY
    return listOf(
        BridgeRow(
            id = "bridge",
            title = if (ready) "Termux 已连通" else "Termux 未连通",
            summary = when (snapshot.setup) {
                TermuxSetup.NOT_INSTALLED -> "没有检测到 com.termux"
                TermuxSetup.PERMISSION -> "缺少 RUN_COMMAND 权限"
                TermuxSetup.READY -> "命令可以下发到本机 Termux"
            },
            active = ready,
            hint = if (ready) "已连通" else "未连通",
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
            hint = if (snapshot.dshInstalled) "已就绪" else "未安装",
        ),
    )
}

/** 三格统计。 */
fun statsFrom(snapshot: TermuxSnapshot): List<BridgeStat> = listOf(
    BridgeStat("服务", if (snapshot.running) "运行中" else "已停止"),
    BridgeStat("桥接", if (snapshot.setup == TermuxSetup.READY) "已连通" else "未连通"),
    BridgeStat("环境", if (snapshot.dshInstalled) "已就绪" else "未安装"),
)

/** 首页要画的一切，一次取齐。 */
fun homeFrom(snapshot: TermuxSnapshot): BridgeHome = BridgeHome(
    status = statusFrom(snapshot),
    rows = rowsFrom(snapshot),
    stats = statsFrom(snapshot),
    canOpenWeb = snapshot.canOpenWebNow,
)

/** 网页页签是否值得给出 dsh 的入口。 */
val TermuxSnapshot.canOpenWebNow: Boolean get() = setup == TermuxSetup.READY && hasUrl

/** 只给地址的 authority；token 不该出现在首页上。 */
val TermuxSnapshot.webUrlSummary: String
    get() = url.substringBefore("/?").ifEmpty { "http://127.0.0.1:$port" }
