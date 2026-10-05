/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.termux

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the Termux side stands, as far as this app can tell. */
enum class TermuxSetup {
    /** Termux itself is not installed, so nothing can be sent. */
    NOT_INSTALLED,

    /** Termux is installed, but this app does not hold its RUN_COMMAND permission yet. */
    PERMISSION,

    /** The permission is held; the launcher script has not been written into Termux's home. */
    SCRIPTS_MISSING,

    /** Scripts are in place and Termux answers commands. */
    READY,
}

/** What the controller is doing right now. */
enum class TermuxPhase { IDLE, BUSY }

/**
 * One observed state of the Termux side, rendered by both UI engines.
 *
 * [setup] and [dshInstalled]/[running] answer different questions — "may I send commands at all"
 * and "what is dsh doing" — and both are needed: a phone with dsh running but the permission
 * revoked still has to show the permission row.
 */
data class TermuxSnapshot(
    val setup: TermuxSetup,
    val phase: TermuxPhase,
    val termuxVersion: String?,
    val dshVersion: String,
    val dshInstalled: Boolean,
    val running: Boolean,
    val port: Int,
    val url: String,
    val busyLabel: String?,
    val lastError: String?,
    val lastOutput: String,
) {
    /** Whether dsh's web UI is up at a known URL. */
    val hasUrl: Boolean get() = running && url.isNotEmpty()

    companion object {
        val Initial = TermuxSnapshot(
            setup = TermuxSetup.NOT_INSTALLED,
            phase = TermuxPhase.IDLE,
            termuxVersion = null,
            dshVersion = "",
            dshInstalled = false,
            running = false,
            port = TermuxBridge.DEFAULT_PORT,
            url = "",
            busyLabel = null,
            lastError = null,
            lastOutput = "",
        )
    }
}

/**
 * The app's entire Termux conversation, in one place.
 *
 * The UI holds no Termux knowledge of its own: it reads [snapshot] and calls [probe], [setup],
 * [start], [stop], [install] and [run]. Both engines therefore behave identically, and the
 * protocol lives in exactly two files — [TermuxBridge] on the wire, this one on the state.
 *
 * **The state lives here, not in the Activity.** A `RUN_COMMAND` reply arrives as a broadcast and
 * the install it may belong to takes minutes, so keeping the state on something that outlives the
 * Activity is what stops a rotation from losing it. It is only ever touched from the main thread:
 * `Application.onCreate`, Compose callbacks and the broadcast receiver all run there.
 */
object TermuxController {

    private const val PREFS = "termux_prefs"
    private const val KEY_PORT = "web_port"
    private const val KEY_SETUP_DONE = "setup_done"

    /** `start` waits up to 40s inside the script for the token URL. */
    private const val START_TIMEOUT_MS = 60_000L

    /** A hand-typed command in the 终端 tab. */
    private const val SHELL_TIMEOUT_MS = 120_000L

    /** Private action: the result broadcast is addressed to this app alone. */
    private const val ACTION_RESULT = "top.youzix.dsha.TERMUX_RESULT"

    /** Identifies which round trip a broadcast belongs to. */
    private const val EXTRA_REQUEST_CODE = "request_code"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requestCounter = AtomicInteger(0)

    private var app: Application? = null

    /** Live state. Compose reads it; nothing writes it except this object. */
    var snapshot by mutableStateOf(TermuxSnapshot.Initial)
        private set

    /** The reply receiver, registered only while a command is in flight. */
    private var receiver: BroadcastReceiver? = null

    /** The one command being waited on, if any. */
    private var inFlight: InFlight? = null

    /** Fires the pending deadline for whatever is in flight. */
    private var timeoutJob: Job? = null

    private class InFlight(
        val requestCode: Int,
        val command: BridgeCommand,
        val silent: Boolean,
        val afterProbe: Boolean,
    )

    /** Called once from [top.youzix.dsha.DshaApp], so state survives the Activity. */
    fun attach(application: Application) {
        if (app != null) return
        app = application
        val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        snapshot = snapshot.copy(port = prefs.getInt(KEY_PORT, TermuxBridge.DEFAULT_PORT))
        refreshEnvironment()
    }

    // ------------------------------------------------------------ public API

    /**
     * Asks Termux what it knows and folds the answer into [snapshot].
     *
     * This is also how the two silent failure modes are detected: a launcher script that was never
     * written, and a Termux that refuses this app's commands (`allow-external-apps` off). Both
     * look like "no reply at all", which the timeout path names.
     */
    fun probe() {
        val context = app ?: return
        refreshEnvironment()
        if (snapshot.setup == TermuxSetup.NOT_INSTALLED || snapshot.setup == TermuxSetup.PERMISSION) {
            return // Nothing to ask: no Termux, or no permission to talk to it.
        }
        send(context, TermuxBridge.probeCommand(), silent = true, afterProbe = false, timeoutMs = TermuxBridge.PROBE_TIMEOUT_MS)
    }

    /**
     * Writes the launcher script and the bundled installer into `~/.dsha`.
     *
     * Success puts the app at [TermuxSetup.READY] — Termux answered, which is the only proof that
     * counts. Silence means the command never ran, so the setup stays at
     * [TermuxSetup.SCRIPTS_MISSING] and the error explains what to check.
     */
    fun setup() {
        val context = app ?: return
        val command = TermuxBridge.setupCommand(context) ?: run {
            snapshot = snapshot.copy(lastError = "APK 里没有 install-dsh.sh 资源")
            return
        }
        send(context, command, silent = false, afterProbe = true, timeoutMs = TermuxBridge.PROBE_TIMEOUT_MS)
    }

    /** Starts `dsh web` and remembers the token URL it prints. */
    fun start() {
        val context = app ?: return
        send(context, TermuxBridge.startCommand(snapshot.port), silent = false, afterProbe = true, timeoutMs = START_TIMEOUT_MS)
    }

    /** Stops `dsh web`. */
    fun stop() {
        val context = app ?: return
        send(context, TermuxBridge.stopCommand(), silent = false, afterProbe = true, timeoutMs = TermuxBridge.PROBE_TIMEOUT_MS)
    }

    /**
     * Hands the bundled DSHA-Next-Shell installer to a Termux session.
     *
     * A terminal session has no result to hand back, so this is the one call that is *sent and
     * forgotten*: the user watches it in Termux, and the next probe reads what it produced. That
     * is the honest shape of it — the alternative (an app shell held open for ten minutes with no
     * output) is worse for exactly the moment the user wants to see something.
     */
    fun install(version: String? = null) {
        val context = app ?: return
        val command = TermuxBridge.installCommand(version)
        val handedOver = sendIntent(context, command, requestResult = false)
        snapshot = snapshot.copy(
            phase = TermuxPhase.IDLE,
            busyLabel = null,
            lastError = if (handedOver) null else "无法把安装指令交给 Termux",
            lastOutput = if (handedOver) {
                "已在 Termux 里打开安装会话：\n  ${command.label}\n" +
                    "装完回到这里点「重新检测」即可。\n" +
                    "（安装要编译原生模块，2～10 分钟，进度在 Termux 窗口里。）"
            } else {
                snapshot.lastOutput
            },
        )
    }

    /** One arbitrary command from the 终端 tab. Its output lands in `lastOutput`. */
    fun run(command: String) {
        val context = app ?: return
        if (command.isBlank()) return
        send(context, TermuxBridge.shellCommand(command), silent = false, afterProbe = false, timeoutMs = SHELL_TIMEOUT_MS)
    }

    /** The tail of `~/.dsha/web.log`. */
    fun readLog() {
        val context = app ?: return
        send(context, TermuxBridge.logCommand(), silent = false, afterProbe = false, timeoutMs = TermuxBridge.PROBE_TIMEOUT_MS)
    }

    /** Remembers the port the next `start` should use. */
    fun setPort(context: Context, port: Int) {
        val safe = port.coerceIn(1, 65535)
        snapshot = snapshot.copy(port = safe)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PORT, safe).apply()
    }

    /** The URL to open in the 网页 tab, or an empty string while dsh is down. */
    fun webUrl(): String = if (snapshot.hasUrl) snapshot.url else ""

    // ------------------------------------------------------------ the round trip

    private fun send(
        context: Context,
        command: BridgeCommand,
        silent: Boolean,
        afterProbe: Boolean,
        timeoutMs: Long,
    ) {
        if (inFlight != null) return // One command at a time: simpler protocol, simpler UI.
        val requestCode = requestCounter.incrementAndGet()
        if (!sendIntent(context, command, requestResult = true, requestCode = requestCode)) {
            snapshot = snapshot.copy(lastError = "无法发送指令给 Termux")
            return
        }
        inFlight = InFlight(requestCode, command, silent, afterProbe)
        snapshot = snapshot.copy(
            phase = TermuxPhase.BUSY,
            busyLabel = command.label,
            lastError = null,
            lastOutput = if (silent) snapshot.lastOutput else "",
        )
        registerReceiver(context, requestCode)
        timeoutJob = scope.launch {
            delay(timeoutMs)
            val current = inFlight
            if (current != null && current.requestCode == requestCode) finish(requestCode, null)
        }
    }

    /**
     * Hands one intent to Termux.
     *
     * With [requestResult] the command carries a one-shot broadcast [PendingIntent] that Termux
     * sends the result bundle to; without it the command is fire-and-forget, which is what a
     * terminal session needs.
     */
    private fun sendIntent(
        context: Context,
        command: BridgeCommand,
        requestResult: Boolean,
        requestCode: Int = 0,
    ): Boolean {
        val resultIntent = if (requestResult) {
            PendingIntent.getBroadcast(
                context,
                // FLAG_IMMUTABLE is not optional from API 31 on: without either immutability flag
                // the platform throws, and a mutable one would let whoever holds it rewrite the
                // intent. Termux only ever sends this intent back, so immutable is also correct.
                // The request code travels as an extra because some Termux builds answer with a
                // bare `Intent()` instead of filling this one in, and the code is what says which
                // round trip the reply belongs to.
                requestCode,
                Intent(ACTION_RESULT).setPackage(context.packageName)
                    .putExtra(EXTRA_REQUEST_CODE, requestCode),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            null
        }
        return runCatching { context.startService(TermuxBridge.intentFor(command, resultIntent)) }.isSuccess
    }

    private fun registerReceiver(context: Context, requestCode: Int) {
        unregisterReceiver(context)
        val next = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent == null) return
                if (intent.getIntExtra(EXTRA_REQUEST_CODE, -1) != requestCode) return
                finish(requestCode, TermuxBridge.parseResult(intent))
            }
        }
        receiver = next
        val filter = IntentFilter(ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(next, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(next, filter)
        }
    }

    private fun unregisterReceiver(context: Context) {
        val current = receiver ?: return
        receiver = null
        runCatching { context.unregisterReceiver(current) }
    }

    /**
     * Closes one round trip.
     *
     * [result] is null when nothing arrived before the deadline. Termux refusing the command
     * (`allow-external-apps` off) is the common cause, so it is named in the message instead of
     * being reported as a bare timeout.
     */
    private fun finish(requestCode: Int, result: CommandResult?) {
        val current = inFlight ?: return
        if (current.requestCode != requestCode) return
        timeoutJob?.cancel()
        timeoutJob = null
        inFlight = null
        app?.let { unregisterReceiver(it) }

        val command = current.command
        var next = snapshot.copy(phase = TermuxPhase.IDLE, busyLabel = null)

        if (result == null) {
            next = next.copy(
                lastError = "没有收到 Termux 的回复。检查 Termux 的 ~/.termux/termux.properties 里 " +
                    "allow-external-apps=true（改完要在 Termux 里执行 termux-reload-settings）。",
            )
            if (command.id == "setup" || command.id == "probe") {
                // The command never ran, so whatever was written before still stands.
                if (next.setup == TermuxSetup.READY) next = next.copy(setup = TermuxSetup.SCRIPTS_MISSING)
            }
        } else {
            if (!current.silent) next = next.copy(lastOutput = result.display)
            if (result.ok) {
                when (command.id) {
                    "setup" -> {
                        val version = TermuxBridge.parseScriptVersion(result.stdout)
                        val ok = version == TermuxBridge.SCRIPT_VERSION_EXPECTED
                        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            ?.edit()?.putBoolean(KEY_SETUP_DONE, ok)?.apply()
                        next = next.copy(
                            setup = if (ok) TermuxSetup.READY else TermuxSetup.SCRIPTS_MISSING,
                            lastError = if (ok) null else "脚本版本不是 ${TermuxBridge.SCRIPT_VERSION_EXPECTED}",
                        )
                    }

                    "probe" -> next = next.probeFrom(result.stdout)
                    "start" -> next = next.copy(running = true, url = firstUrl(result.stdout).ifEmpty { next.url })
                    "stop" -> next = next.copy(running = false, url = "")
                }
            } else {
                next = next.copy(lastError = result.display.lineSequence().firstOrNull { it.isNotBlank() })
            }
        }

        snapshot = next
        // Probing refreshes the facts the reply could not carry (a new token URL, whether the
        // process really came up); after an install it is the only way to notice the result.
        if (current.afterProbe) probe()
    }

    private fun firstUrl(text: String): String = text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        .orEmpty()
}

/** Folds a probe reply into the snapshot. An unparsable reply means "no dsh yet". */
private fun TermuxSnapshot.probeFrom(stdout: String): TermuxSnapshot {
    val report = TermuxBridge.parseProbe(stdout)
    if (!report.answered) return copy(dshInstalled = false, running = false, url = "")
    return copy(
        dshInstalled = report.dshInstalled,
        dshVersion = report.dshVersion,
        running = report.running,
        port = report.port,
        url = if (report.running) report.webUrl else "",
    )
}
