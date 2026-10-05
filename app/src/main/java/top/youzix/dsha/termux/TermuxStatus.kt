/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * 首页读到的 Termux 事实。
 *
 * 字段与解析方式都取自 DSHA 的 `DshaController.applyStatus()`：它的 `status.sh` 输出
 * `KEY=VALUE` 行，控制器把它们读成这几个值。保留同样的键名，所以同一份 status.sh 两边通用。
 */

package top.youzix.dsha.termux

/** DSHA 的 `applyStatus()` 读出来的东西。 */
data class TermuxFacts(
    val repoCloned: Boolean,
    val dshBinAvailable: Boolean,
    val installDirReady: Boolean,
    val dshVersion: String,
    val nodeVersion: String,
    val serverRunning: Boolean,
) {
    companion object {
        val Unknown = TermuxFacts(
            repoCloned = false,
            dshBinAvailable = false,
            installDirReady = false,
            dshVersion = "-",
            nodeVersion = "-",
            serverRunning = false,
        )

        /** 逐行 `KEY=VALUE` —— 与 DSHA 的解析同一套写法。 */
        fun from(stdout: String): TermuxFacts {
            val map = stdout.lineSequence()
                .mapNotNull { line ->
                    val at = line.indexOf('=')
                    if (at <= 0) null else line.take(at).trim() to line.substring(at + 1).trim()
                }
                .toMap()
            return TermuxFacts(
                repoCloned = map["repo"] == "yes",
                dshBinAvailable = map["dsh_bin"] == "yes",
                installDirReady = map["install_dir"] == "yes",
                dshVersion = map["dsh_version"]?.takeIf { it.isNotBlank() } ?: "-",
                nodeVersion = map["node"]?.takeIf { it.isNotBlank() } ?: "-",
                serverRunning = map["server"] == "running",
            )
        }
    }
}
