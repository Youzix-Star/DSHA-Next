/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * 直接取自 DSHA（github.com/Youzix-Star/DSHA）的
 * app/src/main/java/com/youzixstar/dsha/termux/TermuxBridge.kt —— 同一版权人、同为 AGPL-3.0-only，
 * 只把包名从 com.youzixstar.dsha 换成 top.youzix.dsha，逻辑一行未改。
 *
 * 这是「终端能不能用」这件事上唯一在手机上真跑过的那份实现，所以终端的行为实现整体复用它，
 * 而不是我们另写一套再逐条对齐。
 */

package top.youzix.dsha.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 与 Termux 通信的桥。
 *
 * 走 Termux 官方公开的 RUN_COMMAND 接口（[官方文档](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)）：
 * 把命令交给 com.termux.app.RunCommandService 执行，结果通过 PendingIntent 回传。
 *
 * 生效前提（安装引导会逐项检查）：
 *  1. 已安装 Termux（F-Droid 版，非 Google Play 版）；
 *  2. 本应用已获得 com.termux.permission.RUN_COMMAND 权限（dangerous，需运行时申请）；
 *  3. Termux 侧 ~/.termux/termux.properties 中 allow-external-apps = true。
 *
 * 常量取值以 termux-app 源码 TermuxConstants.java 为准。
 */
object TermuxBridge {

    const val TERMUX_PACKAGE = "com.termux"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    /** Termux 内的家目录与可执行文件前缀（Termux 自身固定不变） */
    const val TERMUX_HOME = "/data/data/com.termux/files/home"
    const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    const val TERMUX_BASH = "$TERMUX_PREFIX/bin/bash"

    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val SERVICE_CLASS = "com.termux.app.RunCommandService"

    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_RUNNER = "com.termux.RUN_COMMAND_RUNNER"
    private const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    /** 结果 Bundle 的键名，见 ResultSender / TermuxPluginUtils */
    private const val EXTRA_RESULT_BUNDLE = "result"
    private const val KEY_STDOUT = "stdout"
    private const val KEY_STDERR = "stderr"
    private const val KEY_EXIT_CODE = "exitCode"
    private const val KEY_ERRMSG = "errmsg"

    /** 后台执行，不占用可见的终端会话 */
    private const val RUNNER_APP_SHELL = "app-shell"

    internal const val EXTRA_REQUEST_CODE = "top.youzix.dsha.request_code"

    private const val TAG = "DshaTermux"

    private val nextRequestCode = AtomicInteger(1000)
    private val waiters = ConcurrentHashMap<Int, CompletableDeferred<Result>>()

    data class Result(
        val stdout: String,
        val stderr: String,
        val exitCode: Int?,
        val errmsg: String?,
    ) {
        val ok: Boolean get() = exitCode == 0 && errmsg == null

        /** 出错时的可读描述 */
        val errorText: String
            get() = when {
                errmsg != null -> errmsg
                stderr.isNotBlank() -> stderr.trim()
                exitCode != null -> "退出码 $exitCode"
                else -> "未知错误"
            }

        /** 合并后的输出，便于直接展示 */
        val combined: String
            get() = buildString {
                if (stdout.isNotBlank()) append(stdout.trim())
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(stderr.trim())
                }
            }
    }

    fun isTermuxInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun isRunCommandPermissionGranted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED

    /** Termux 是否提示 allow-external-apps 未开启 */
    fun isAllowExternalAppsError(text: String?): Boolean =
        text != null && text.contains("allow-external-apps")

    /**
     * 在 Termux 中后台执行一段 bash 脚本，等待结果返回。
     *
     * @param timeoutMs 超时时间；安装等长任务需要给足（默认 10 分钟）
     */
    suspend fun run(
        context: Context,
        command: String,
        label: String = "DSHA-Next",
        timeoutMs: Long = 10 * 60 * 1000L,
    ): Result {
        val requestCode = nextRequestCode.incrementAndGet()
        val deferred = CompletableDeferred<Result>()
        waiters[requestCode] = deferred

        val receiverIntent = Intent(context, TermuxResultReceiver::class.java)
            .putExtra(EXTRA_REQUEST_CODE, requestCode)

        // FLAG_MUTABLE 是必须的：Termux 用 send(context, code, resultIntent) 把结果 bundle 挂在
        // 它自己的 Intent 上，平台只在 PendingIntentRecord.sendInner 里经 Intent.fillIn 合并进
        // 最终 Intent；FLAG_IMMUTABLE 会让那一段被整块跳过，结果永远回不来。
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, receiverIntent, piFlags)

        val intent = Intent().apply {
            action = ACTION_RUN_COMMAND
            setClassName(TERMUX_PACKAGE, SERVICE_CLASS)
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", command))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(EXTRA_RUNNER, RUNNER_APP_SHELL)
            putExtra(EXTRA_COMMAND_LABEL, label)
            putExtra(EXTRA_PENDING_INTENT, pendingIntent)
        }

        return try {
            context.startService(intent)
            withTimeoutOrNull(timeoutMs) { deferred.await() }
                ?: Result("", "", null, "执行超时（${timeoutMs / 1000} 秒未返回结果）")
        } catch (e: Exception) {
            Log.w(TAG, "RUN_COMMAND 调用失败", e)
            Result("", "", null, e.message ?: e.javaClass.simpleName)
        } finally {
            waiters.remove(requestCode)
        }
    }

    internal fun deliver(requestCode: Int, result: Result) {
        waiters.remove(requestCode)?.complete(result)
    }

    /** 由 [TermuxResultReceiver] 解析回传的 Intent */
    internal fun parseResult(intent: Intent): Pair<Int, Result>? {
        val code = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        if (code < 0) return null
        val bundle = intent.getBundleExtra(EXTRA_RESULT_BUNDLE)
        val exitCode = if (bundle != null && bundle.containsKey(KEY_EXIT_CODE)) {
            bundle.getInt(KEY_EXIT_CODE)
        } else {
            null
        }
        return code to Result(
            stdout = bundle?.getString(KEY_STDOUT).orEmpty(),
            stderr = bundle?.getString(KEY_STDERR).orEmpty(),
            exitCode = exitCode,
            errmsg = bundle?.getString(KEY_ERRMSG),
        )
    }
}
