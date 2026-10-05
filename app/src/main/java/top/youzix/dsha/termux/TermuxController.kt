/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.termux

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    /**
     * Commands can be sent.
     *
     * The runner script travels inside every command, so there is nothing to install first; the one
     * file that does live on disk — the installer — is written by [prepare] without the user having
     * to ask for it.
     */
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

    /** `start` waits up to 40s inside the script for the token URL. */
    private const val START_TIMEOUT_MS = 60_000L

    /** A hand-typed command in the 终端 tab. */
    private const val SHELL_TIMEOUT_MS = 120_000L

    /** Identifies which round trip a broadcast belongs to. */
    private const val EXTRA_REQUEST_CODE = "request_code"

    /**
     * `FLAG_MUTABLE`, and this is not a preference.
     *
     * Termux answers by calling `PendingIntent.send(context, RESULT_OK, resultIntent)` with the
     * bundle attached to **that** intent. The platform merges it into the pending intent's own only
     * through `Intent.fillIn`, and AOSP's `PendingIntentRecord.sendInner` skips that merge entirely
     * when `FLAG_IMMUTABLE` is set — so an immutable pending intent never receives stdout, stderr or
     * the exit code, and every command times out with nothing to show for it. The request code rides
     * along as an extra, which `fillIn` keeps because the base intent's extras win on collision.
     */
    private const val PENDING_INTENT_FLAGS =
        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requestCounter = AtomicInteger(0)

    private var app: Application? = null

    /** Live state. Compose reads it; nothing writes it except this object. */
    var snapshot by mutableStateOf(TermuxSnapshot.Initial)
        private set

    /** The one command being waited on, if any. */
    private var inFlight: InFlight? = null

    /** Whether the installer has been written this session. */
    private var installingPrepared = false

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
        // The one file that has to live on disk. It is a 18 KB copy, so doing it here rather than
        // asking the user for a separate "准备" step costs nothing and removes a whole state the UI
        // would otherwise have to explain.
        if (snapshot.setup == TermuxSetup.READY) prepare()
    }

    /**
     * Writes the installer into `~/.dsha` if it is not there yet.
     *
     * Silent on purpose: it is a prerequisite of one button (安装 dsh), and every other command works
     * without it. Failure is remembered in [TermuxSnapshot.lastError] and surfaces only when the user
     * actually asks for the thing that needs it.
     */
    fun prepare() {
        val context = app ?: return
        if (snapshot.setup != TermuxSetup.READY) return
        if (installingPrepared) return
        installingPrepared = true
        val command = TermuxBridge.setupCommand(context) ?: run {
            installingPrepared = false
            snapshot = snapshot.copy(lastError = "APK 里没有 install-dsh.sh 资源")
            return
        }
        send(context, command, silent = true, afterProbe = true, timeoutMs = TermuxBridge.PROBE_TIMEOUT_MS)
    }

    /**
     * Reports the facts that need no round trip: whether Termux is installed, whether this app
     * holds its permission, and whether the scripts were ever written.
     *
     * These three are answered locally on purpose. Asking Termux "are you installed" is a package
     * query, and asking it "did the setup run" would be a command — which is exactly the thing
     * that cannot be sent yet in the two states where the answer matters most.
     */
    private fun refreshEnvironment() {
        val context = app ?: return
        val installed = TermuxBridge.isInstalled(context)
        val permitted = installed && TermuxBridge.hasPermission(context)
        val setup = when {
            !installed -> TermuxSetup.NOT_INSTALLED
            !permitted -> TermuxSetup.PERMISSION
            else -> TermuxSetup.READY
        }
        snapshot = snapshot.copy(
            setup = setup,
            termuxVersion = if (installed) TermuxBridge.installedVersion(context) else null,
        )
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
        dispatch(context, { TermuxBridge.probeCommand(context) }, silent = true, afterProbe = false, timeoutMs = TermuxBridge.PROBE_TIMEOUT_MS)
    }

    /** Starts `dsh web` and remembers the token URL it prints. */
    fun start() {
        val context = app ?: return
        dispatch(context, { TermuxBridge.startCommand(context, snapshot.port) }, silent = false, afterProbe = true, timeoutMs = START_TIMEOUT_MS)
    }

    /** Stops `dsh web`. */
    fun stop() {
        val context = app ?: return
        dispatch(context, { TermuxBridge.stopCommand(context) }, silent = false, afterProbe = true, timeoutMs = TermuxBridge.PROBE_TIMEOUT_MS)
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
        val command = TermuxBridge.installCommand(context, version)
        if (command == null) {
            snapshot = snapshot.copy(lastError = "读不到内置的 run.sh（APK 资源缺失），无法下发命令")
            return
        }
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
        dispatch(context, { TermuxBridge.shellCommand(context, command) }, silent = false, afterProbe = false, timeoutMs = SHELL_TIMEOUT_MS)
    }

    /** The tail of `~/.dsha/web.log`. */
    fun readLog() {
        val context = app ?: return
        dispatch(context, { TermuxBridge.logCommand(context) }, silent = false, afterProbe = false, timeoutMs = TermuxBridge.PROBE_TIMEOUT_MS)
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

    /**
     * Builds one command and sends it.
     *
     * Every builder returns `null` for the same reason — `assets/run.sh` could not be read — and that
     * is a broken install rather than a user-visible state, so it is reported once here instead of
     * being null-checked at seven call sites.
     */
    private fun dispatch(
        context: Context,
        build: () -> BridgeCommand?,
        silent: Boolean,
        afterProbe: Boolean,
        timeoutMs: Long,
    ) {
        val command = build()
        if (command == null) {
            snapshot = snapshot.copy(
                phase = TermuxPhase.IDLE,
                busyLabel = null,
                lastError = "读不到内置的 run.sh（APK 资源缺失），无法下发命令",
            )
            return
        }
        send(context, command, silent, afterProbe, timeoutMs)
    }

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
                requestCode,
                Intent(context, TermuxResultReceiver::class.java).putExtra(EXTRA_REQUEST_CODE, requestCode),
                PENDING_INTENT_FLAGS,
            )
        } else {
            null
        }
        return runCatching { context.startService(TermuxBridge.intentFor(command, resultIntent)) }.isSuccess
    }

    /**
     * Entry point for [TermuxResultReceiver]; it runs on the main thread like everything else here.
     *
     * Only the round trip currently in flight is accepted, so a late reply from a command whose
     * deadline already passed cannot overwrite a newer state.
     */
    fun deliverFromReceiver(intent: Intent) {
        val code = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        val current = inFlight ?: return
        if (code != current.requestCode) return
        finish(code, TermuxBridge.parseResult(intent))
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

        val command = current.command
        var next = snapshot.copy(phase = TermuxPhase.IDLE, busyLabel = null)

        if (result == null) {
            next = next.copy(
                lastError = "没有收到 Termux 的回复。检查 Termux 的 ~/.termux/termux.properties 里 " +
                    "allow-external-apps=true（改完要在 Termux 里执行 termux-reload-settings）。",
            )
            if (command.id == "setup") {
                // It never ran, so the installer is not on disk; allow the next attempt to try again
                // instead of remembering the failure as final.
                installingPrepared = false
            }
        } else {
            if (!current.silent) next = next.copy(lastOutput = result.display)
            if (result.ok) {
                when (command.id) {
                    "setup" -> next = next.copy(lastError = null)

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
