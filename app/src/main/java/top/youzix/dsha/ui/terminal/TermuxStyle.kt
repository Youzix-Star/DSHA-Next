/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Termux 的外观：欢迎语、提示符、配色。
 *
 * 三样东西都不是凭印象抄的，各有确切出处：
 *  1. 欢迎语 = Termux 自己那份 $PREFIX/etc/motd.sh 输出（本机是 0.118.3.64，包管理器 apt），
 *     这里复刻它「终端宽度 ≤ 60」那一条分支 —— 也正是 termux-app 首次启动时看到的那一段。
 *  2. 提示符 = $PREFIX/etc/bash.bashrc 里那行 PS1：
 *     `\[\e[0;32m\]\w\[\e[0m\] \[\e[0;97m\]\$\[\e[0m\]` —— 绿色路径、白色 $、后面一个空格。
 *  3. 配色 = termux-app 的 terminal-emulator/TerminalColorScheme.java 的默认色表
 *     （16 色 + 默认前景/背景/光标）。Termux 默认就是深色：纯黑底、纯白字。
 *
 * 这里是「外观」而不是「终端」：没有 PTY、没有 VTE 解析器，也没有真在跑的 shell。
 * 它画的是一段静态文本，只是画得跟 Termux 一模一样。
 */

package top.youzix.dsha.ui.terminal

import androidx.compose.ui.graphics.Color

/** Termux 的默认调色板 —— TerminalColorScheme.DEFAULT_COLORSCHEME 的前 16 项与末 3 项。 */
object TermuxColors {
    // 前 8 个是暗色
    val DimRed = Color(0xFFCD0000)
    val DimGreen = Color(0xFF00CD00)
    val DimYellow = Color(0xFFCDCD00)
    val DimBlue = Color(0xFF6495ED)
    val DimMagenta = Color(0xFFCD00CD)
    val DimCyan = Color(0xFF00CDCD)
    val DimWhite = Color(0xFFE5E5E5)

    // 后 8 个是亮色
    val MediumGrey = Color(0xFF7F7F7F)
    val BrightRed = Color(0xFFFF0000)
    val BrightGreen = Color(0xFF00FF00)
    val BrightYellow = Color(0xFFFFFF00)
    val LightBlue = Color(0xFF5C5CFF)
    val BrightMagenta = Color(0xFFFF00FF)
    val BrightCyan = Color(0xFF00FFFF)
    val BrightWhite = Color(0xFFFFFFFF)

    /** COLOR_INDEX_DEFAULT_FOREGROUND / BACKGROUND / CURSOR：白 / 黑 / 白。 */
    val Foreground = Color(0xFFFFFFFF)
    val Background = Color(0xFF000000)
    val Cursor = Color(0xFFFFFFFF)
}

/**
 * Termux 第一次启动时打印的那段。
 *
 * 逐行照抄 [motd.sh] 的窄终端分支 + apt 分支：`\e[1m` 是加粗，`\e[4m` 是下划线，
 * 行首的 `\e[1m`/`\e[0m` 与原文一一对应，所以行距、缩进、空行都跟真的一样。
 */
object TermuxBanner {

    /** 本机 `$TERMUX_VERSION`。它来自 bootstrap，不是 termux-app 的版本号。 */
    const val VERSION = "0.118.3.64"

    /** 提示符：绿色路径 + 白色 `$` + 一个空格。 */
    const val PROMPT_PATH = "~"
    const val PROMPT = "$ "
}

/**
 * 欢迎语的每一行，按 Termux 的排版。
 *
 * 用 `List<String>` 而不是一个大字符串，是因为要逐行渲染成不同的样式（加粗的标签、
 * 带下划线的链接）；换行与空行就是列表里的空串。
 */
val TERMUX_WELCOME: List<String> = listOf(
    "Welcome to Termux!",
    "",
    "Docs:       https://termux.dev/docs",
    "Donate:     https://termux.dev/donate",
    "Community:  https://termux.dev/community",
    "",
    "Working with packages:",
    "",
    " - Search:  pkg search <query>",
    " - Install: pkg install <package>",
    " - Upgrade: pkg upgrade",
    "",
    "Subscribing to additional repositories:",
    "",
    " - Root:    pkg install root-repo",
    " - X11:     pkg install x11-repo",
    "",
    "For fixing any repository issues,",
    "try 'termux-change-repo' command.",
    "",
    "Report issues at https://termux.dev/issues",
    "",
)
