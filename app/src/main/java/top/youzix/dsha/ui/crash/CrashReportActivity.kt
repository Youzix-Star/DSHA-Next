/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Process
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import top.youzix.dsha.MainActivity
import top.youzix.dsha.util.CrashHandler

/**
 * The screen that appears when the app dies instead of the app simply vanishing.
 *
 * It runs in its own process (`android:process=":crash"`), so a crash in the main process cannot
 * take it down with it, and it builds its interface in code out of plain views: no Compose, no
 * theme resources, nothing that could fail the same way the app just did.
 *
 * The report itself is already on disk by the time this opens — [CrashHandler] writes it before
 * launching anything — so this screen only has to show it and give the user a way to hand it over.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = CrashHandler.read(this) ?: "（没有读到崩溃文件，可能没写成功）"
        val path = runCatching { CrashHandler.file(this).absolutePath }.getOrDefault("?")

        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121212.toInt())
            setPadding(pad, pad, pad, pad)
        }

        root.addView(
            text("崩溃了", 22f, Color.WHITE, bold = true),
        )
        root.addView(
            text("下面是这次的报告。点「复制」就能整段贴给我，日志文件在：\n$path", 13f, 0xFFAAAAAA.toInt())
                .apply { setPadding(0, dp(6), 0, pad) },
        )

        val body = text(report, 11f, 0xFFE0E0E0.toInt()).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(
            ScrollView(this).apply {
                setBackgroundColor(0xFF1E1E1E.toInt())
                addView(body)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        root.addView(
            row(
                button("复制") {
                    val clipboard = getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("DSHA-Next 崩溃报告", report))
                    toast("已复制，贴给我就行")
                },
                button("分享") {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "DSHA-Next 崩溃报告")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    runCatching { startActivity(Intent.createChooser(intent, "分享崩溃报告")) }
                },
            ),
        )
        root.addView(
            row(
                button("重启应用") {
                    runCatching {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        )
                    }
                    close()
                },
                button("关闭") { close() },
            ),
        )

        setContentView(root)
    }

    /** Leaves nothing behind: this process exists only to show the report. */
    private fun close() {
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun row(vararg views: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, pad(), 0, 0)
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun toast(message: String) {
        runCatching { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    private fun pad() = dp(10)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
