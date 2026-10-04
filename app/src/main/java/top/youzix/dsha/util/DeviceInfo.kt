/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.youzix.dsha.AppState
import top.youzix.dsha.BuildConfig

/**
 * One paste-ready snapshot of what this build is running on.
 *
 * It exists so a bug report can start with the facts instead of an exchange: which device, which
 * Android, which WebView — the three that decide most rendering questions and are the hardest to
 * guess. Read at the moment it is asked for, so the snapshot time means something.
 */
object DeviceInfo {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun snapshot(context: Context): String = buildString {
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}(${Build.DEVICE})")
        appendLine("Package Name: ${context.packageName}")
        appendLine("App Version: ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
        appendLine("Snapshot Time: ${LocalDateTime.now().format(stamp)}")
        appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("Display Size: ${displaySize(context)}")
        appendLine("Android Version: ${Build.VERSION.RELEASE} API${Build.VERSION.SDK_INT}")
        appendLine("Language: ${Locale.getDefault().toLanguageTag()}")
        appendLine("UI Engine: ${AppState.engine.label}")
        appendLine("Screen Density: ${context.resources.displayMetrics.densityDpi} dpi")
        appendLine("Dark Theme: ${isDarkNow(context)}")
        val webView = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
        appendLine("WebView Impl: ${webView?.packageName ?: "unknown"}")
        appendLine("WebView Version: ${webView?.versionName ?: "unknown"}")
        // The full UA, not just the version: it is what a server sees, and the only line here
        // that can be compared against what a site actually received.
        append("User-Agent: ${runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull() ?: "unknown"}")
    }

    /** The real screen, not the window: this is what a screenshot will match. */
    private fun displaySize(context: Context): String {
        val manager = context.getSystemService(WindowManager::class.java)
        val bounds = manager?.currentWindowMetrics?.bounds ?: return "unknown"
        return "${bounds.width()}x${bounds.height()}"
    }

    private fun isDarkNow(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
