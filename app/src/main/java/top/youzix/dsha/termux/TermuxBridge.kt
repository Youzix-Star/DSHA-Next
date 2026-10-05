/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.termux

import android.content.ComponentName
import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.security.MessageDigest

/**
 * The wire contract between this app and Termux's `RUN_COMMAND` intent service.
 *
 * Every constant below is read out of termux-app's own sources rather than from memory: the
 * extras are the literal values of `TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE` and
 * `TERMUX_SERVICE`, and the component is the service termux-app declares in its manifest
 * (`com.termux/.app.RunCommandService`, guarded by `com.termux.permission.RUN_COMMAND`).
 *
 * Two facts shape every decision in this package:
 *
 * 1. **Nothing but Termux writes into Termux's storage.** Its home is
 *    `/data/data/com.termux/files/home`, another app's private directory. The one thing this app
 *    can do is ask Termux to run `sh -c <script>`; the script writes the files itself. That is
 *    what 准备 does.
 * 2. **A result only comes back when a `PendingIntent` is attached.** Termux sends the plugin
 *    result bundle to it when the command finishes — and sends nothing at all when the user has
 *    not set `allow-external-apps=true`, which is what makes a missing reply a diagnosable
 *    answer instead of a hang.
 *
 * @see <a href="https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent">RUN_COMMAND intent</a>
 */
object TermuxBridge {

    /** The Termux application. The F-Droid and GitHub builds share this package name. */
    const val PACKAGE = "com.termux"

    /** Termux's home: the only place a launcher script can live and still be executable. */
    const val HOME = "/data/data/com.termux/files/home"

    /** Termux's `$PREFIX`; its `bin` is what `dsh` and friends are reached through. */
    const val PREFIX = "/data/data/com.termux/files/usr"

    /**
     * The interpreter every command goes through.
     *
     * `bash` rather than `/system/bin/sh`: the scripts use `$(...)`, `local`-free functions and
     * Termux's own `pgrep`/`setsid`, and Termux ships bash. `-l` is added per command so the login
     * profile puts `$PREFIX/bin` on `PATH`, which is how `dsh` is found.
     */
    const val TERMUX_BASH = "$PREFIX/bin/bash"

    /**
     * Directory this app owns inside that home. Everything the bridge installs lives here, so a
     * user can read it, edit it, or delete the whole directory to start over.
     */
    const val DIR = "$HOME/.dsha"

    /** The launcher script, written by [setupCommand]. */
    const val RUNNER = "$DIR/run.sh"

    /** The copy of DSHA-Next-Shell's installer that ships inside this APK. */
    const val INSTALLER = "$DIR/install-dsh.sh"

    /** Where a backgrounded `dsh web` writes its stdout — the token URL is in there. */
    const val WEB_LOG = "$DIR/web.log"

    /** `RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND`. */
    private const val ACTION_RUN = "$PACKAGE.RUN_COMMAND"

    /** `RUN_COMMAND_SERVICE`'s class, as a manifest component name. */
    private const val SERVICE = "$PACKAGE.app.RunCommandService"

    /** `RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH` — absolute path of an executable file. */
    private const val EXTRA_COMMAND_PATH = "$PACKAGE.RUN_COMMAND_PATH"

    /** `RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS` — that executable's argv. */
    private const val EXTRA_ARGUMENTS = "$PACKAGE.RUN_COMMAND_ARGUMENTS"

    /** `RUN_COMMAND_SERVICE.EXTRA_RUNNER` — `app-shell` or `terminal-session`. */
    private const val EXTRA_RUNNER = "$PACKAGE.RUN_COMMAND_RUNNER"

    /** `RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT` — carries the result bundle back. */
    private const val EXTRA_PENDING_INTENT = "$PACKAGE.RUN_COMMAND_PENDING_INTENT"

    /** `EXTRA_COMMAND_LABEL`, so Termux's own notification names the job. */
    private const val EXTRA_COMMAND_LABEL = "$PACKAGE.RUN_COMMAND_COMMAND_LABEL"

    /**
     * `EXTRA_PLUGIN_RESULT_BUNDLE` and its keys. Termux 0.118 and later prefix these with
     * `plugin_`; older builds do not. [parseResult] reads both spellings, so either build answers.
     */
    private const val RESULT_BUNDLE = "result"
    private const val RESULT_STDOUT = "stdout"
    private const val RESULT_STDERR = "stderr"
    private const val RESULT_EXIT_CODE = "exitCode"
    private const val RESULT_ERR = "err"
    private const val RESULT_ERRMSG = "errmsg"

    /** `Runner.APP_SHELL`: run and collect, no terminal UI. */
    const val RUNNER_APP_SHELL = "app-shell"

    /**
     * `Runner.TERMINAL_SESSION`: opens a Termux session the user watches. Used for the one long,
     * interactive job — `install-dsh.sh` — where a silent ten-minute wait is worse than a window
     * the user can look at.
     */
    const val RUNNER_TERMINAL = "terminal-session"

    /** The permission Termux requires of any app that sends [ACTION_RUN]. */
    const val PERMISSION = "$PACKAGE.permission.RUN_COMMAND"

    /** Default listen port, matching dsh's own default and the installer's documented URL. */
    const val DEFAULT_PORT = 3080

    /**
     * How long the UI waits for a reply before calling it dead.
     *
     * A probe is two `sh` calls; 20s is generous for a phone under load, and short enough that a
     * refused command (the `allow-external-apps` case) reads as "no answer" quickly.
     */
    const val PROBE_TIMEOUT_MS = 20_000L

    /** Whether Termux is installed at all. */
    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** The version name Termux reports, for the diagnostics row. */
    fun installedVersion(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0).versionName
    }.getOrNull()

    /**
     * Whether this app holds `com.termux.permission.RUN_COMMAND`.
     *
     * It is a `dangerous` permission declared by *Termux*, so the manifest entry is only half of
     * it — the user has to grant it, and [permissionIntent] is where they do that.
     */
    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * The system screen for granting that permission.
     *
     * This app cannot raise a permission dialog for itself: the permission belongs to another
     * package's manifest, and Android only offers the per-app screen for it. The dedicated
     * permission-detail screen is used when the OEM ships one, with the app's own settings page as
     * the fallback.
     */
    fun permissionIntent(context: Context): Intent {
        val detail = Intent("android.intent.action.MANAGE_APP_PERMISSION").apply {
            putExtra("android.intent.extra.PACKAGE_NAME", PACKAGE)
            putExtra("android.intent.extra.PERMISSION_NAME", PERMISSION)
        }
        if (detail.resolveActivity(context.packageManager) != null) {
            return detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val appDetails = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", PACKAGE, null)
        }
        if (appDetails.resolveActivity(context.packageManager) != null) {
            return appDetails.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Whether the permission can be asked for with a runtime dialog.
     *
     * `com.termux.permission.RUN_COMMAND` is a permission *Termux* declares, not this app, so the
     * question is whether its declaring package is installed and resolvable: that is what decides
     * if `requestPermissions` has anything to grant. Without Termux installed there is nothing to
     * request, and [permissionIntent] is the only remaining route.
     */
    fun canRequestPermission(context: Context): Boolean = isInstalled(context)

    /** Whether an intent can be sent at all: Termux installed *and* the permission held. */
    fun isUsable(context: Context): Boolean = isInstalled(context) && hasPermission(context)

    // -------------------------------------------------------------- the script

    /** SHA-256 of the bundled installer, shown in the diagnostics rows. */
    fun bundledInstallerHash(context: Context): String = sha256(readInstaller(context).orEmpty())

    /** Reads `assets/install-dsh.sh`, the DSHA-Next-Shell installer as shipped in this APK. */
    fun readInstaller(context: Context): String? =
        runCatching { context.assets.open("install-dsh.sh").bufferedReader().use { it.readText() } }.getOrNull()

    /**
     * Reads the run script that ships in this APK.
     *
     * It is an asset rather than a Kotlin string for two reasons that both bit us: a shell script
     * inside a Kotlin string is *interpolated* — every `$name` and `${...}` is read by the compiler,
     * so the whole thing had to be written with a placeholder for `$` — and a script this size is
     * easier to read, check (`scripts/runscript.py` runs it through `sh -n`) and change as its own
     * file. DSHA keeps its scripts the same way.
     */
    fun runScript(context: Context): String? =
        runCatching { context.assets.open("run.sh").bufferedReader().use { it.readText() } }.getOrNull()

    /**
     * The one thing that has to be written into Termux: the installer from DSHA-Next-Shell.
     *
     * Everything else is sent inline, so there is nothing to "prepare" before the buttons work.
     * This one is an 18 KB script whose whole job is to survive being run repeatedly, so it lives on
     * disk at `~/.dsha/install-dsh.sh` where the user can read it. The payload is base64 so nothing
     * inside it can be mangled by a shell on the way in.
     */
    fun setupCommand(context: Context): BridgeCommand? {
        val installer = readInstaller(context) ?: return null
        val payload = base64(installer.toByteArray(Charsets.UTF_8))
        return BridgeCommand(
            id = "setup",
            label = "写入安装脚本",
            executable = TERMUX_BASH,
            arguments = listOf(
                "-c",
                "set -e; d=\"\$HOME/.dsha\"; mkdir -p \"\$d\"; " +
                    "echo $payload | base64 -d > \"\$d/install-dsh.sh\"; " +
                    "chmod 700 \"\$d/install-dsh.sh\"; echo \"install-dsh.sh 已就位\"",
            ),
        )
    }

    /** The one command every call goes through: run the bundled script with an action. */
    private fun scriptCommand(context: Context, action: String, label: String, runner: String = RUNNER_APP_SHELL): BridgeCommand? {
        val script = runScript(context) ?: return null
        return BridgeCommand(
            id = action.substringBefore(' '),
            label = label,
            executable = TERMUX_BASH,
            // `-l` because dsh and node are reached through Termux's own profile; the action is a
            // single argv entry, so the script's `case "$1"` sees it whole — no re-quoting anywhere.
            // The script is appended, then invoked with the action as `$1`. Appending and calling in
            // one command keeps it a single `bash -lc` argument, and the action is one argv entry, so
            // the script's `case "$1"` sees it whole.
            arguments = listOf("-lc", script + "\nrun.sh " + action),
            runner = runner,
        )
    }

    /**
     * The probe: one app-shell call answering "is dsh installed, which version, is it running, on
     * which port, and what is the token URL". Every screen reads its state from here rather than
     * keeping a parallel opinion.
     */
    fun probeCommand(context: Context): BridgeCommand? = scriptCommand(context, "probe", "检测 Termux 状态")

    /** `start`: brings `dsh web` up in the background and returns the token URL. */
    fun startCommand(context: Context, port: Int): BridgeCommand? =
        scriptCommand(context, "start ${port.coerceIn(1, 65535)}", "启动 dsh web")

    /** `stop`: stops the `dsh web` process. */
    fun stopCommand(context: Context): BridgeCommand? = scriptCommand(context, "stop", "停止 dsh web")

    /**
     * `install`: runs DSHA-Next-Shell's installer inside Termux.
     *
     * A `terminal-session`, not an app shell: it builds native modules for two to ten minutes, and a
     * window the user can watch beats a silent spinner here. A terminal session has no result to hand
     * back, so the app re-probes instead of waiting.
     */
    fun installCommand(context: Context, version: String?): BridgeCommand? =
        scriptCommand(
            context,
            "install ${version?.trim().orEmpty()}".trim(),
            if (version.isNullOrBlank()) "安装 dsh" else "安装 dsh $version",
            RUNNER_TERMINAL,
        )

    /** `log`: the tail of `~/.dsha/web.log`. */
    fun logCommand(context: Context, lines: Int = 40): BridgeCommand? =
        scriptCommand(context, "log ${lines.coerceIn(1, 400)}", "读取 dsh 日志")

    /**
     * `shell <命令>`: one arbitrary command, run with Termux's profile loaded.
     *
     * This is what the 终端 tab sends, and it works with no preparation: the script travels with the
     * command. The command itself is one argv entry, so the app never re-quotes what the user typed.
     */
    fun shellCommand(context: Context, command: String): BridgeCommand? =
        scriptCommand(context, "shell " + command, command)

    // -------------------------------------------------------------- the intent

    /**
     * Builds the `RUN_COMMAND` intent for one command.
     *
     * The component is set explicitly rather than left to the intent filter, so the action cannot
     * be routed anywhere but Termux's own service.
     */
    fun intentFor(command: BridgeCommand, resultIntent: PendingIntent?): Intent {
        val intent = Intent(ACTION_RUN).apply {
            component = ComponentName(PACKAGE, SERVICE)
            putExtra(EXTRA_COMMAND_PATH, command.executable)
            putStringArrayListExtra(EXTRA_ARGUMENTS, ArrayList(command.arguments))
            putExtra(EXTRA_RUNNER, command.runner)
            putExtra(EXTRA_COMMAND_LABEL, "DSHA-Next: ${command.label}")
            if (resultIntent != null) putExtra(EXTRA_PENDING_INTENT, resultIntent)
        }
        return intent
    }

    /**
     * Reads the result bundle Termux sends back.
     *
     * `err` is Termux's own errno, not the command's: it is present on every reply and is non-zero
     * only when Termux itself refused or failed. The command's own status is `exitCode`.
     */
    fun parseResult(intent: Intent): CommandResult {
        val bundle: Bundle? = intent.getBundleExtra(RESULT_BUNDLE)
            ?: intent.getBundleExtra("plugin_$RESULT_BUNDLE")
        if (bundle == null) {
            return CommandResult("", "", -1, -1, "Termux 没有返回结果体")
        }
        val errno = intOf(bundle, RESULT_ERR, "plugin_$RESULT_ERR")
        val errmsg = stringOf(bundle, RESULT_ERRMSG, "plugin_$RESULT_ERRMSG")
        return CommandResult(
            stdout = stringOf(bundle, RESULT_STDOUT, "plugin_$RESULT_STDOUT"),
            stderr = stringOf(bundle, RESULT_STDERR, "plugin_$RESULT_STDERR"),
            exitCode = intOf(bundle, RESULT_EXIT_CODE, "plugin_$RESULT_EXIT_CODE"),
            errno = errno,
            errmsg = errmsg.ifBlank { if (errno != 0) "Termux errno=$errno" else "" },
        )
    }

    /** Parses the `key=value` lines `run.sh probe` prints. */
    fun parseProbe(stdout: String): ProbeReport {
        val values = mutableMapOf<String, String>()
        stdout.lineSequence().forEach { line ->
            val at = line.indexOf('=')
            if (at > 0) values[line.substring(0, at).trim()] = line.substring(at + 1).trim()
        }
        return ProbeReport(
            status = values["status"].orEmpty(),
            dshVersion = values["version"].orEmpty(),
            running = values["running"] == "yes",
            port = values["port"]?.toIntOrNull() ?: DEFAULT_PORT,
            url = values["url"].orEmpty(),
        )
    }

    /**
     * Reads the `script=<n>` reply.
     *
     * Kept for the reply of a command that prints a bare number, which is how a script version
     * used to be reported; nothing depends on it now that the runner travels inside the command.
     */
    fun parseScriptVersion(stdout: String): Int? =
        stdout.lineSequence().mapNotNull { it.trim().toIntOrNull() }.firstOrNull()

    // -------------------------------------------------------------- helpers

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun base64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun intOf(bundle: Bundle, vararg keys: String): Int {
        for (key in keys) {
            if (bundle.containsKey(key)) return bundle.getInt(key, 0)
        }
        return 0
    }

    private fun stringOf(bundle: Bundle, vararg keys: String): String {
        for (key in keys) {
            val value = bundle.getString(key)
            if (!value.isNullOrEmpty()) return value
        }
        return ""
    }
}

/** One command for Termux: an executable, its argv, and which runner should carry it. */
data class BridgeCommand(
    val id: String,
    val label: String,
    val executable: String,
    val arguments: List<String>,
    val runner: String = TermuxBridge.RUNNER_APP_SHELL,
) {
    /** The argv as one line, for diagnostics and for echoing into the terminal. */
    val commandLine: String get() = (listOf(executable) + arguments).joinToString(" ")
}

/**
 * What Termux answered.
 *
 * [errno] is Termux's own code and is present on every reply; [exitCode] belongs to the command.
 * A command that never reached Termux produces neither — see the timeout path in
 * [TermuxController].
 */
data class CommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val errno: Int,
    val errmsg: String,
) {
    val ok: Boolean get() = errno == 0 && exitCode == 0

    /** The combined text the terminal tab shows. */
    val display: String
        get() = buildString {
            if (stdout.isNotEmpty()) append(stdout)
            if (stderr.isNotEmpty()) {
                if (isNotEmpty() && !endsWith("\n")) append('\n')
                append(stderr)
            }
            if (errmsg.isNotEmpty()) {
                if (isNotEmpty() && !endsWith("\n")) append('\n')
                append(errmsg)
            }
        }.ifEmpty { if (ok) "(无输出)" else "退出码 $exitCode" }
}

/**
 * What `run.sh probe` reported.
 *
 * An empty [status] means the script never answered: either it is not installed yet, or Termux
 * refused the command. The two are told apart by whether [setupCommand] itself succeeded, which is
 * why the controller tracks that separately.
 */
data class ProbeReport(
    val status: String,
    val dshVersion: String,
    val running: Boolean,
    val port: Int,
    val url: String,
) {
    val dshInstalled: Boolean get() = status == "installed"
    val answered: Boolean get() = status.isNotEmpty()
    val webUrl: String get() = url.ifEmpty { "http://127.0.0.1:$port/" }
}
