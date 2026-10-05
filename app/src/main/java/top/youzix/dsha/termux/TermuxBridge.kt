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

    /** Whether an intent can be sent at all: Termux installed *and* the permission held. */
    fun isUsable(context: Context): Boolean = isInstalled(context) && hasPermission(context)

    // -------------------------------------------------------------- the script

    /**
     * The launcher script this app installs into Termux's home.
     *
     * It is deliberately a readable file with a usage block rather than an opaque blob: the user
     * owns `~/.dsha`, and every action the app can take is spelled out here. It is written as a
     * Kotlin raw string with no interpolation at all — the shell's own `$` is left alone on
     * purpose — and `version` exists so the app can tell an installed copy from an outdated one.
     */
    val runnerScript: String = """
#!/data/data/com.termux/files/usr/bin/sh
# DSHA-Next —— Termux 侧启动脚本，由 App 写入 ~/.dsha/run.sh
#
# 用法: run.sh <动作> [参数…]
#   version              打印脚本版本（App 用它判断要不要覆盖）
#   probe                输出 status= / version= / running= / port= / url=
#   start [端口]          后台启动 dsh web，日志写到 ~/.dsha/web.log
#   stop                 停止 dsh web
#   log [行数]            打印 web.log 的尾部
#   install [版本]        运行 ~/.dsha/install-dsh.sh
#   shell <命令>          在 $PREFIX/bin 的 PATH 下执行一条命令
#
# 这个文件属于用户：可以自己改、自己加动作，App 只是调用它。

DSHA_DIR="${'$'}{HOME:-/data/data/com.termux/files/home}/.dsha"
LOG="$DSHA_DIR/web.log"
PORT_FILE="$DSHA_DIR/web.port"
PREFIX_DIR="${'$'}{PREFIX:-/data/data/com.termux/files/usr}"
export PATH="$PREFIX_DIR/bin:$PATH"
export HOME="${'$'}{HOME:-/data/data/com.termux/files/home}"

# The app creates this before its first write, but the script is also meant to be runnable by
# hand, so it makes sure of the directory itself.
mkdir -p "$DSHA_DIR" 2>/dev/null

SCRIPT_VERSION=1

web_pids() {
    pgrep -f "lib/bin.js web" 2>/dev/null
}

read_port() {
    if [ -f "$PORT_FILE" ]; then
        cat "$PORT_FILE"
    else
        echo 3080
    fi
}

# The token URL, once dsh has printed it.
read_url() {
    [ -f "$LOG" ] || return 0
    grep -a -o 'http://[0-9A-Za-z._:-]*/?token=[A-Za-z0-9._~-]*' "$LOG" | tail -n 1
}

case "${'$'}{1:-probe}" in
  version)
    echo "$SCRIPT_VERSION"
    ;;
  probe)
    if command -v dsh >/dev/null 2>&1; then
        echo "status=installed"
    else
        echo "status=missing-dsh"
    fi
    echo "version=$(dsh --version 2>/dev/null | tail -n 1)"
    echo "running=$([ -n "$(web_pids)" ] && echo yes || echo no)"
    echo "port=$(read_port)"
    echo "url=$(read_url)"
    ;;
  start)
    if [ "${'$'}{2:-}" != "" ]; then
        printf '%s' "$2" > "$PORT_FILE"
    fi
    PORT="$(read_port)"
    # One instance only: a second `dsh web` on the same port would just fail to bind.
    if [ -n "$(web_pids)" ]; then
        echo "already running"
        read_url
        exit 0
    fi
    # The project's own advice: lock the wake lock first, or Android reclaims the instance.
    if command -v termux-wake-lock >/dev/null 2>&1; then
        termux-wake-lock >/dev/null 2>&1
    fi
    : > "$LOG"
    # Detach: this call has to return while dsh keeps running.
    if command -v setsid >/dev/null 2>&1; then
        setsid dsh web --no-open --port "$PORT" >> "$LOG" 2>&1 &
    else
        nohup dsh web --no-open --port "$PORT" >> "$LOG" 2>&1 &
    fi
    # Wait for the token URL to appear (up to 40s) and hand it back with the reply.
    i=0
    while [ "$i" -lt 80 ]; do
        U="$(read_url)"
        if [ -n "$U" ]; then
            echo "$U"
            exit 0
        fi
        if [ -z "$(web_pids)" ]; then
            echo "dsh web 退出了，日志尾部：" >&2
            tail -n 20 "$LOG" >&2
            exit 1
        fi
        sleep 0.5
        i=$((i + 1))
    done
    echo "已启动，但 40 秒内没有拿到 token URL；日志尾部：" >&2
    tail -n 20 "$LOG" >&2
    exit 1
    ;;
  stop)
    PIDS="$(web_pids)"
    if [ -z "$PIDS" ]; then
        echo "没有在运行"
        exit 0
    fi
    kill $PIDS 2>/dev/null
    i=0
    while [ "$i" -lt 20 ] && [ -n "$(web_pids)" ]; do
        sleep 0.25
        i=$((i + 1))
    done
    if [ -n "$(web_pids)" ]; then
        kill -9 $(web_pids) 2>/dev/null
    fi
    echo "已停止"
    ;;
  log)
    if [ -f "$LOG" ]; then
        tail -n "${'$'}{2:-40}" "$LOG"
    else
        echo "还没有 $LOG"
    fi
    ;;
  install)
    if [ ! -x "$DSHA_DIR/install-dsh.sh" ]; then
        echo "缺少 $DSHA_DIR/install-dsh.sh，请先在 App 里点「准备」" >&2
        exit 1
    fi
    if [ "${'$'}{2:-}" != "" ]; then
        "$DSHA_DIR/install-dsh.sh" "$2"
    else
        "$DSHA_DIR/install-dsh.sh"
    fi
    ;;
  shell)
    shift
    exec sh -c "$*"
    ;;
  *)
    echo "未知动作: $1" >&2
    exit 2
    ;;
esac
""".trimIndent() + "\n"

    /** SHA-256 of [runnerScript], so the app can spot an out-of-date copy already in Termux. */
    val runnerHash: String = sha256(runnerScript)

    /** SHA-256 of the bundled installer, shown in the diagnostics rows. */
    fun bundledInstallerHash(context: Context): String = sha256(readInstaller(context).orEmpty())

    /** Reads `assets/install-dsh.sh`, the DSHA-Next-Shell installer as shipped in this APK. */
    fun readInstaller(context: Context): String? =
        runCatching { context.assets.open("install-dsh.sh").bufferedReader().use { it.readText() } }.getOrNull()

    /**
     * The first command the app ever sends: write [runnerScript] and the bundled installer into
     * `~/.dsha`, then run `run.sh version` as proof that it worked.
     *
     * The payload is base64, so nothing inside either script can be mangled by a shell on the way
     * in. Running it again simply overwrites the copies, which is how an updated APK replaces an
     * older script.
     */
    fun setupCommand(context: Context): BridgeCommand? {
        val installer = readInstaller(context) ?: return null
        val runner = base64(runnerScript.toByteArray(Charsets.UTF_8))
        val install = base64(installer.toByteArray(Charsets.UTF_8))
        return BridgeCommand(
            id = "setup",
            label = "准备 Termux 侧脚本",
            executable = "/system/bin/sh",
            arguments = listOf(
                "-c",
                "set -e; d=\"\$HOME/.dsha\"; mkdir -p \"\$d\"; " +
                    "echo $runner | base64 -d > \"\$d/run.sh\"; " +
                    "echo $install | base64 -d > \"\$d/install-dsh.sh\"; " +
                    "chmod 700 \"\$d/run.sh\" \"\$d/install-dsh.sh\"; " +
                    "sh \"\$d/run.sh\" version",
            ),
        )
    }

    /**
     * The probe: one app-shell call answering "is dsh installed, which version, is it running, on
     * which port, and what is the token URL". Every screen reads its state from here rather than
     * keeping a parallel opinion.
     */
    fun probeCommand(): BridgeCommand = BridgeCommand(
        id = "probe",
        label = "检测 Termux 状态",
        executable = "/system/bin/sh",
        arguments = listOf("-c", "sh \"$RUNNER\" probe"),
    )

    /** `start`: brings `dsh web` up in the background and returns the token URL. */
    fun startCommand(port: Int): BridgeCommand = BridgeCommand(
        id = "start",
        label = "启动 dsh web",
        executable = "/system/bin/sh",
        arguments = listOf("-c", "sh \"$RUNNER\" start ${port.coerceIn(1, 65535)}"),
    )

    /** `stop`: stops the `dsh web` process. */
    fun stopCommand(): BridgeCommand = BridgeCommand(
        id = "stop",
        label = "停止 dsh web",
        executable = "/system/bin/sh",
        arguments = listOf("-c", "sh \"$RUNNER\" stop"),
    )

    /**
     * `install`: runs DSHA-Next-Shell's installer inside Termux.
     *
     * Sent as a `terminal-session`, not an app shell: it builds native modules for two to ten
     * minutes, and a window the user can watch beats a silent spinner here. The app watches for it
     * to finish by re-probing, since a terminal session has no result to hand back.
     */
    fun installCommand(version: String?): BridgeCommand = BridgeCommand(
        id = "install",
        label = if (version.isNullOrBlank()) "安装 dsh" else "安装 dsh $version",
        executable = "/system/bin/sh",
        arguments = listOf("-c", "sh \"$RUNNER\" install ${version?.trim().orEmpty()}"),
        runner = RUNNER_TERMINAL,
    )

    /** `log`: the tail of `~/.dsha/web.log`, so the app can show what `dsh web` printed. */
    fun logCommand(lines: Int = 40): BridgeCommand = BridgeCommand(
        id = "log",
        label = "读取 dsh 日志",
        executable = "/system/bin/sh",
        arguments = listOf("-c", "sh \"$RUNNER\" log ${lines.coerceIn(1, 400)}"),
    )

    /**
     * `shell <命令>`: one arbitrary command, run with `$PREFIX/bin` on `PATH`.
     *
     * This is what the 终端 tab sends. The command is a single string the runner hands to
     * `sh -c`, and the app never re-quotes it — one parse, in the shell that has to do the
     * parsing anyway.
     */
    fun shellCommand(command: String): BridgeCommand = BridgeCommand(
        id = "shell",
        label = command,
        executable = "/system/bin/sh",
        arguments = listOf("-c", "sh \"$RUNNER\" shell " + command),
    )

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
     * The installed script reports its own version through the `version` action, which
     * [setupCommand] runs as its last step: a reply carrying [SCRIPT_VERSION_EXPECTED] is both an
     * install that worked and proof that Termux is willing to run this app's commands at all.
     */
    fun parseScriptVersion(stdout: String): Int? =
        stdout.lineSequence().mapNotNull { it.trim().toIntOrNull() }.firstOrNull()

    /** The script revision this build of the app installs. */
    const val SCRIPT_VERSION_EXPECTED = 1

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
