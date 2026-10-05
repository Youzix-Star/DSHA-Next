/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Termux 那一屏的**内容**：欢迎语逐行上样式，末尾接提示符。
 *
 * 放在共享层而不是某个引擎里，因为它跟引擎无关 —— 两套引擎画的是同一块画面，
 * 差别只在外面套什么（miuix 有液体玻璃顶栏，Material 3 有自己的大标题栏）。
 * 这里只用 foundation 的 AnnotatedString，不 import 任何一方的组件。
 */

package top.youzix.dsha.ui.terminal

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * 整屏内容：欢迎语逐行，然后是一行空行加提示符。
 *
 * 加粗与下划线按 motd.sh 里的 `\e[1m` / `\e[4m` 给：`Welcome to Termux!`、`Docs:` 这类标签加粗，
 * 三个链接加下划线。其余部分就是 Termux 的默认前景色。
 */
/** 整屏内容，构造一次即可 —— 它是静态的，不必每次重组都重算。 */
val TermuxScreenText: AnnotatedString by lazy { termuxScreen() }

fun termuxScreen(): AnnotatedString = buildAnnotatedString {
    TERMUX_WELCOME.forEachIndexed { index, line ->
        if (index > 0) append('\n')
        append(styledLine(line))
    }
    // motd 结束后的那个空行，与 `~ $ ` 提示符
    append("\n\n")
    withStyle(SpanStyle(color = TermuxColors.DimGreen, fontWeight = FontWeight.Bold)) {
        append(TermuxBanner.PROMPT_PATH)
    }
    append(" ")
    append(TermuxBanner.PROMPT)
}

/**
 * 一行欢迎语，按 Termux 的排版上样式。
 *
 * 只有三处需要特判：整行加粗的标题行、行首的 ` - ` 项目符号（`pkg` 命令名加粗）、
 * 以及行尾的链接（下划线）。其余原样输出 —— 缩进是原文里的空格，不能动。
 */
fun styledLine(line: String): AnnotatedString = buildAnnotatedString {
    fun linkAt(text: String): Int = text.indexOf("https://")

    val link = linkAt(line)
    if (line.startsWith("Welcome to Termux!") || line == "Working with packages:" ||
        line == "Subscribing to additional repositories:"
    ) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line) }
        return@buildAnnotatedString
    }
    if (link >= 0) {
        append(line.substring(0, link))
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(line.substring(link)) }
        return@buildAnnotatedString
    }
    // ` - Search:  pkg search <query>`：冒号前是标签，冒号后的命令名加粗
    val colon = line.indexOf(':')
    if (line.startsWith(" - ") && colon > 0) {
        append(line.substring(0, colon + 1))
        append(line.substring(colon + 1))
        return@buildAnnotatedString
    }
    append(line)
}
