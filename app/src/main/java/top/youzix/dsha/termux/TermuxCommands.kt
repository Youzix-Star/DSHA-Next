/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * 这个应用需要的那几条「Termux 事实」。
 *
 * 命令怎么下发不再由这里决定 —— 那是 DSHA 的 [TermuxBridge]（`bash -lc` + PendingIntent +
 * 协程等待），终端与首页都走它。这里只留三件事：查 Termux 装没装／有没有权限、把授权页与安装
 * 脚本取出来、以及把 status.sh 的 `KEY=VALUE` 解析成 [TermuxFacts]。
 *
 * 曾经这里还存着一整套「自己拼 RUN_COMMAND intent」的代码；首页改成走 DSHA 的 `refresh()`
 * 之后它全部没人用了，删掉。留一份没人调用的传输层，就是等着它和真在用的那份漂移。
 */

package top.youzix.dsha.termux

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.security.MessageDigest

object TermuxCommands {

    /** `dsh web` 的监听端口，DSHA 的 start.sh / status.sh 里也是这个数。 */
    const val DEFAULT_PORT = 3080

    /** 一条命令该等多久。 */
    const val PROBE_TIMEOUT_MS = 20_000L

    fun isInstalled(context: Context): Boolean = TermuxBridge.isTermuxInstalled(context)

    fun hasPermission(context: Context): Boolean = TermuxBridge.isRunCommandPermissionGranted(context)

    fun installedVersion(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(TermuxBridge.TERMUX_PACKAGE, 0).versionName
    }.getOrNull()

    /**
     * 授权页。
     *
     * 能弹系统授权框时用不上它（见首页的 permissionLauncher）；这条是 Termux 没装、或系统没有
     * 权限详情页时的退路。
     */
    fun permissionIntent(context: Context): Intent {
        val detail = Intent("android.intent.action.MANAGE_APP_PERMISSION").apply {
            putExtra("android.intent.extra.PACKAGE_NAME", TermuxBridge.TERMUX_PACKAGE)
            putExtra("android.intent.extra.PERMISSION_NAME", TermuxBridge.PERMISSION_RUN_COMMAND)
        }
        if (detail.resolveActivity(context.packageManager) != null) {
            return detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", TermuxBridge.TERMUX_PACKAGE, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 写安装脚本用的一条命令。
     *
     * 唯一需要落盘的东西：脚本内容随命令发过去是 DSHA 的做法，但 18 KB 的安装脚本每次重发
     * 不划算，而且用户可能想自己读它，所以它落成 `~/.dsha/install-dsh.sh`。
     *
     * 载荷是 base64，所以脚本里任何字符都不会被路上的 shell 改掉。
     */
    fun setupSource(context: Context): String? {
        val installer = readInstaller(context) ?: return null
        val encoded = android.util.Base64.encodeToString(
            installer.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        return "set -e; d=\"\$HOME/.dsha\"; mkdir -p \"\$d\"; " +
            "echo $encoded | base64 -d > \"\$d/install-dsh.sh\"; " +
            "chmod 700 \"\$d/install-dsh.sh\"; echo installed"
    }

    /** 读 APK 里那份 DSHA-Next-Shell 安装脚本。 */
    fun readInstaller(context: Context): String? =
        runCatching { context.assets.open("install-dsh.sh").bufferedReader().use { it.readText() } }.getOrNull()

    /** 安装脚本的摘要，用于诊断（出问题时能对上是哪一份）。 */
    fun bundledInstallerHash(context: Context): String =
        MessageDigest.getInstance("SHA-256")
            .digest(readInstaller(context).orEmpty().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
