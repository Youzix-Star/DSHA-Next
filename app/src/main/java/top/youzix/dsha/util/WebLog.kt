/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * What the WebView refused to load, and why.
 *
 * A WebView says nothing when a page loses its stylesheet: the request is blocked or fails, the
 * page renders bare, and from the outside that looks identical to "the site is broken". The
 * reason is reported only to the JavaScript console — mixed content, CSP, a 403 from a CDN — so
 * this keeps a small, capped record of console warnings and failed resources, and the debug menu
 * can hand it over.
 *
 * Appends happen off the main thread: this is called from WebView callbacks, which run on it.
 */
object WebLog {
    private const val MAX_LINES = 600

    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "web-log").apply { isDaemon = true }
    }

    /** One line, timestamped and appended; the file keeps only the last [MAX_LINES] lines. */
    fun note(context: Context, line: String) {
        val app = context.applicationContext
        val entry = "${stamp.format(Date())} $line"
        writer.execute {
            runCatching {
                val target = file(app)
                target.parentFile?.mkdirs()
                target.appendText(entry + "\n")
                trim(target)
            }
        }
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** Where the log lives; shown to the user so it can be found without the app. */
    fun file(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(File(base, "webview"), "webview.log")
    }

    private fun trim(target: File) {
        val lines = target.readLines()
        if (lines.size <= MAX_LINES) return
        target.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
    }
}
