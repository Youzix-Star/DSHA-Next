/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * 直接取自 DSHA（github.com/Youzix-Star/DSHA）的同名文件，只换了包名。
 */

package top.youzix.dsha.termux

import android.content.Context

/**
 * 读取打包在 assets/scripts 下的 shell 脚本。
 *
 * 脚本以独立 .sh 文件维护（而不是拼在 Kotlin 字符串里），便于阅读、审查与复用；
 * 运行时读取内容后作为 bash 命令交给 Termux 执行，不需要写入 Termux 的私有目录。
 */
object Scripts {

    private val cache = HashMap<String, String>()

    @Synchronized
    fun load(context: Context, name: String): String = cache.getOrPut(name) {
        context.assets.open("scripts/$name").bufferedReader().use { it.readText() }
    }

    fun probe(context: Context): String = load(context, "probe.sh")
    fun status(context: Context): String = load(context, "status.sh")
    fun install(context: Context): String = load(context, "install.sh")
    fun start(context: Context): String = load(context, "start.sh")
    fun stop(context: Context): String = load(context, "stop.sh")
    fun logs(context: Context): String = load(context, "logs.sh")
}
