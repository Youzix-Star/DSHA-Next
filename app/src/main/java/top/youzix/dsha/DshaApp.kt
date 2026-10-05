/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha

import android.app.Application
import android.os.Build
import top.youzix.dsha.termux.TermuxController
import top.youzix.dsha.util.CrashHandler

/** Installs the crash recorder before anything else in the app has a chance to fail. */
class DshaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The crash screen has its own process (`:crash`); installing the handler there would let a
        // failure on that screen open another crash screen, and so on.
        if (!isCrashProcess()) CrashHandler.install(this)
        // Termux state belongs to the process, not to a screen: a RUN_COMMAND result arrives as a
        // broadcast, and the install it may belong to takes minutes. Attaching here is what keeps
        // a rotation, or a trip to Termux and back, from losing it.
        if (!isCrashProcess()) TermuxController.attach(this)
    }

    private fun isCrashProcess(): Boolean {
        val name = currentProcessName() ?: return false
        return name.endsWith(CRASH_PROCESS_SUFFIX)
    }

    private fun currentProcessName(): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            // Before API 28 the process name had to be read out of /proc.
            java.io.File("/proc/self/cmdline").readText().trim().trimEnd('\u0000')
        }
    }.getOrNull()

    private companion object {
        const val CRASH_PROCESS_SUFFIX = ":crash"
    }
}
