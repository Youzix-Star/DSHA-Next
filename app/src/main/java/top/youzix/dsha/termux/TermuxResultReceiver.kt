/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * 直接取自 DSHA（github.com/Youzix-Star/DSHA）的同名文件，只换了包名。
 */

package top.youzix.dsha.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接收 Termux RUN_COMMAND 的执行结果。
 *
 * 通过显式组件的 PendingIntent 投递，因此无需 exported，避免结果被其它应用伪造。
 */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parsed = TermuxBridge.parseResult(intent) ?: return
        TermuxBridge.deliver(parsed.first, parsed.second)
    }
}
