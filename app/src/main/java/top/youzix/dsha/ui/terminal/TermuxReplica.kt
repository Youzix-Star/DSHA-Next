/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * 终端那一屏用的样式：prompt 行、普通输出行、链接与加粗标签。
 *
 * 放在共享层，因为它跟引擎无关：两套引擎画的是同一块画面，差别只在外面套什么。
 * 只用 foundation 的 AnnotatedString，不 import 任何一方的组件。
 */

package top.youzix.dsha.ui.terminal

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * 一行终端文字，按 Termux 的排版上样式。
 *
 * 三类行各有各的规矩，规则都是从 Termux 那边来的：
 *  - 提示符行（以 `~ $ ` 开头）：路径绿色、命令本身默认前景色；
 *  - 欢迎语里整行加粗的标题、带下划线的链接（`\e[1m` / `\e[4m` 的效果）；
 *  - 其余输出原样 —— 缩进是原文里的空格，不能动。
 */
fun terminalLine(line: String): AnnotatedString = buildAnnotatedString {
    if (line.startsWith(TermuxBanner.PROMPT)) {
        withStyle(SpanStyle(color = TermuxColors.DimGreen, fontWeight = FontWeight.Bold)) {
            append(TermuxBanner.PROMPT_PATH)
        }
        append(line.removePrefix(TermuxBanner.PROMPT_PATH))
        return@buildAnnotatedString
    }

    val link = line.indexOf("https://")
    if (link >= 0) {
        append(line.substring(0, link))
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(line.substring(link)) }
        return@buildAnnotatedString
    }

    if (line == "Welcome to Termux!" || line == "Working with packages:" ||
        line == "Subscribing to additional repositories:"
    ) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line) }
        return@buildAnnotatedString
    }

    append(line)
}
