/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.os.Bundle
import android.os.Process
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button as AndroidButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.youzix.dsha.MainActivity
import top.youzix.dsha.ui.miuix.MiuixAppTheme
import top.youzix.dsha.util.CrashHandler
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The screen that appears when the app dies instead of the app simply vanishing.
 *
 * It runs in its own process (`android:process=":crash"`), so a crash in the main process cannot
 * take it down with it. The report is already on disk by the time this opens — [CrashHandler]
 * writes it before launching anything — so this screen only has to show it and hand it over.
 *
 * The interface is the app's own (miuix, same as everywhere else), with one concession to what
 * this screen is for: if setting up the Compose tree fails at all, it falls back to a plain-view
 * report. A crash screen that cannot draw is worse than an ugly one.
 */
class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val report = CrashHandler.read(this) ?: "（没有读到崩溃文件，可能没写成功）"
        val path = runCatching { CrashHandler.file(this).absolutePath }.getOrDefault("?")

        val composed = runCatching {
            setContent {
                MiuixAppTheme(colorSchemeMode = ColorSchemeMode.System) {
                    CrashReportScreen(
                        report = report,
                        path = path,
                        onCopy = { copy(report) },
                        onShare = { share(report) },
                        onRestart = { restart() },
                        onClose = { close() },
                    )
                }
            }
        }.isSuccess

        if (!composed) setContentView(fallbackView(report, path))
    }

    /** Leaves nothing behind: this process exists only to show the report. */
    private fun close() {
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }

    private fun restart() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
        close()
    }

    private fun copy(report: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("DSHA-Next 崩溃报告", report))
        toast("报告已复制")
    }

    private fun share(report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DSHA-Next 崩溃报告")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching { startActivity(Intent.createChooser(intent, "分享崩溃报告")) }
    }

    private fun toast(message: String) {
        runCatching { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    /* ------------------------------------------------------------- the plain-view fallback */

    private fun fallbackView(report: String, path: String): View {
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121212.toInt())
            setPadding(pad, pad, pad, pad)
        }

        root.addView(text("应用崩溃了", 22f, AndroidColor.WHITE, bold = true))
        root.addView(
            text("报告里有崩溃位置和最后经过的界面。复制后发给开发者，就能定位问题。", 13f, 0xFFAAAAAA.toInt())
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
            fallbackRow(
                fallbackButton("复制报告") { copy(report) },
                fallbackButton("分享报告") { share(report) },
            ),
        )
        root.addView(
            fallbackRow(
                fallbackButton("重启应用") { restart() },
                fallbackButton("关闭") { close() },
            ),
        )
        root.addView(
            text("日志文件：$path", 11f, 0xFF888888.toInt())
                .apply { setPadding(0, dp(12), 0, 0) },
        )
        return root
    }

    private fun fallbackRow(vararg views: AndroidButton) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(10), 0, 0)
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun fallbackButton(label: String, onClick: () -> Unit) = AndroidButton(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

@Composable
private fun CrashReportScreen(
    report: String,
    path: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        Text(
            text = "应用崩溃了",
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "报告里有崩溃位置和最后经过的界面。复制后发给开发者，就能定位问题。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            insideMargin = PaddingValues(12.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = report,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("复制报告") }
            Button(onClick = onShare, modifier = Modifier.weight(1f)) { Text("分享报告") }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRestart, modifier = Modifier.weight(1f)) { Text("重启应用") }
            Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("关闭") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "日志文件：$path",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
