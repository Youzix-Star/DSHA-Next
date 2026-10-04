/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess
import top.youzix.dsha.BuildConfig
import top.youzix.dsha.ui.crash.CrashReportActivity

/**
 * Records the last crash to a file the user can actually get at, together with where the app was.
 *
 * Without this a crash is a disappearing window: the app is gone, the report is in a ring buffer
 * nobody can read, and all that is left is "it closed on me". The breadcrumbs matter as much as
 * the stack — they say which screen was being built when it happened.
 */
object CrashHandler {
    private const val DIR = "crash"
    private const val FILE_NAME = "latest.txt"
    private const val MAX_CRUMBS = 40

    private val crumbs = ArrayDeque<String>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Records where the app is, so the next report says what the user was doing. */
    fun note(where: String) {
        synchronized(crumbs) {
            crumbs.addLast("${timeFormat.format(Date())} $where")
            while (crumbs.size > MAX_CRUMBS) crumbs.removeFirst()
        }
    }

    /**
     * Installs the handler. Call once, from [top.youzix.dsha.DshaApp], and only in the main process:
     * the crash screen runs in its own, and it must not install a handler that could put a second
     * crash screen on top of the first.
     */
    fun install(context: Context) {
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            // The screen lives in another process, so it survives the kill below; the report is
            // already on disk, so it has something to show even if the launch itself fails.
            runCatching { app.startActivity(Intent(app, CrashReportActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            // Deliberately not handed to the platform handler: that is what puts up the "app has
            // stopped" dialog over this screen. The stack still reaches logcat either way.
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    /** Where the report lives; shown to the user so they can find it without the app. */
    fun file(context: Context): File {
        val dir = (context.getExternalFilesDir(null) ?: context.filesDir).let { File(it, DIR) }
        return File(dir, FILE_NAME)
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val target = file(context)
        target.parentFile?.mkdirs()

        val trace = StringWriter()
        error.printStackTrace(PrintWriter(trace))

        val report = buildString {
            appendLine("喵喵助手 崩溃报告")
            appendLine("时间: ${stampFormat.format(Date())}")
            appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(
                "设备: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
                    "(SDK ${Build.VERSION.SDK_INT})",
            )
            appendLine("线程: ${thread.name}")
            appendLine()
            appendLine("最近的界面：")
            synchronized(crumbs) { crumbs.forEach { appendLine("  $it") } }
            appendLine()
            appendLine("异常：")
            append(trace.toString())
        }

        target.writeText(report)
    }
}
