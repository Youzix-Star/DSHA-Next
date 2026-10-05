/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * 首页要用到的那点「与 Termux 有关的文案」。
 *
 * 动作模型（按钮、可点面板）已经删掉了：终端那一页现在只是 Termux 的画面，不再执行命令，
 * 首页也不该留着一键按钮 —— 一个按下去会「以 Termux 身份执行任意命令」的按钮，
 * 与「先不整一键执行」是矛盾的。这里只剩状态文案与两条派生属性。
 */

package top.youzix.dsha.termux

/** 状态面板用的语气，两个引擎各自映射到自己的配色。 */
enum class StatusTone { OK, BUSY, IDLE, WARN, BAD }

/** 首页的整体状态（只读）。 */
data class BridgeHome(
    val status: BridgeStatus,
    val rows: List<BridgeRow>,
    val stats: List<BridgeStat>,
    val canOpenWeb: Boolean,
)

/** 首页主面板的一行文案（只读）。 */
data class BridgeStatus(
    val tone: StatusTone,
    val headline: String,
    val detail: String,
    val hint: String,
)

/** 主面板下面的一行前置条件（只读）。 */
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
 * 首页主面板该显示什么 —— 只读。
 *
 * 分支顺序就是用户会问的顺序：能不能跟 Termux 说话、dsh 装没装、在不在跑。
 * 权限被撤销时即使上一次探测还记得「在跑」，也该先报权限 —— 那才是当前的问题。
 */
fun homeFrom(
    snapshot: TermuxSnapshot,
): BridgeHome = BridgeHome(
    status = statusFrom(snapshot),
    rows = rowsFrom(snapshot),
    stats = statsFrom(snapshot),
    canOpenWeb = snapshot.canOpenWebNow,
)

/** 网页页签是否值得给出 dsh 的入口。 */
val TermuxSnapshot.canOpenWebNow: Boolean get() = setup == TermuxSetup.READY && hasUrl

/** 只给地址的 authority —— token 不该出现在首页上。 */
val TermuxSnapshot.webUrlSummary: String
    get() = url.substringBefore("/?").ifEmpty { "http://127.0.0.1:$port" }
