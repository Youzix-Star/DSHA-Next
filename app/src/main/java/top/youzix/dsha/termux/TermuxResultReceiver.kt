/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the result of a Termux `RUN_COMMAND` execution.
 *
 * Declared in the manifest and **not exported**: the `PendingIntent` that reaches it names this
 * class explicitly, so only Termux — holding that one-shot pending intent — can deliver to it, and
 * no other app can forge a result by broadcasting here.
 *
 * It is a manifest receiver rather than one registered in `onCreate` for a reason that is not
 * theoretical: a command may be answered while the Activity is gone (a long install, a rotation, a
 * trip to Termux). A registered receiver would have been unregistered with the Activity and the
 * reply would land nowhere, leaving the UI waiting for a timeout that had already happened. The
 * state it feeds lives on [TermuxController], which [top.youzix.dsha.DshaApp] attaches at
 * process start, so `onReceive` works even when it cold-starts the process.
 */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TermuxController.deliverFromReceiver(intent)
    }
}
