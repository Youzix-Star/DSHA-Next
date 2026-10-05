/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the Termux bridge that do not need a device.
 *
 * Everything under test here is a pure function of strings — the probe reply Termux sends, the
 * output a command produced, and the button list a state turns into. The Android half (sending the
 * intent, reading the result bundle) is exercised by hand on a phone; these are the pieces CI can
 * hold still, and they are the ones that broke while this was written.
 */
class TermuxBridgeTest {

    @Test
    fun `probe reply is read as installed and running`() {
        val report = TermuxBridge.parseProbe(
            """
            status=installed
            version=0.2.0-rc.2
            running=yes
            port=3080
            url=http://127.0.0.1:3080/?token=abc-123
            """.trimIndent(),
        )

        assertEquals("installed", report.status)
        assertEquals("0.2.0-rc.2", report.dshVersion)
        assertTrue(report.running)
        assertEquals(3080, report.port)
        assertEquals("http://127.0.0.1:3080/?token=abc-123", report.url)
        assertTrue(report.answered)
        assertTrue(report.dshInstalled)
    }

    @Test
    fun `probe reply with no dsh is not mistaken for a running one`() {
        val report = TermuxBridge.parseProbe(
            """
            status=missing-dsh
            version=
            running=no
            port=3080
            url=
            """.trimIndent(),
        )

        assertTrue(report.answered)
        assertFalse(report.dshInstalled)
        assertFalse(report.running)
        // An empty url must still leave the caller something to open once dsh comes up.
        assertEquals("http://127.0.0.1:3080/", report.webUrl)
    }

    @Test
    fun `an empty reply is an unanswered probe, not a status`() {
        val report = TermuxBridge.parseProbe("")
        assertFalse(report.answered)
        assertFalse(report.dshInstalled)
        assertFalse(report.running)
        assertEquals(TermuxBridge.DEFAULT_PORT, report.port)
    }

    @Test
    fun `garbage between the probe lines does not break the parse`() {
        val report = TermuxBridge.parseProbe(
            "warning: something\nstatus=installed\nnonsense\nrunning=no\nport=not-a-number\n",
        )
        assertEquals("installed", report.status)
        assertEquals(TermuxBridge.DEFAULT_PORT, report.port)
    }

    @Test
    fun `result display joins stdout and stderr and keeps an empty one readable`() {
        val both = CommandResult("out\n", "err\n", 0, 0, "")
        assertEquals("out\nerr\n", both.display)

        val failed = CommandResult("", "boom", 1, 0, "")
        assertEquals("boom", failed.display)

        val silent = CommandResult("", "", 0, 0, "")
        assertEquals("(无输出)", silent.display)

        val refused = CommandResult("", "", -1, -1, "Termux 没有返回结果体")
        assertEquals("Termux 没有返回结果体", refused.display)
        assertFalse(refused.ok)
    }

    @Test
    fun `ok requires both Termux's errno and the command's own exit code to be zero`() {
        assertTrue(CommandResult("", "", 0, 0, "").ok)
        assertFalse(CommandResult("", "", 1, 0, "").ok)
        assertFalse(CommandResult("", "", 0, 1, "refused").ok)
    }

    @Test
    fun `the script version reply is read even when wrapped in other lines`() {
        assertEquals(1, TermuxBridge.parseScriptVersion("\n1\n"))
        assertEquals(7, TermuxBridge.parseScriptVersion("7"))
        assertEquals(null, TermuxBridge.parseScriptVersion("sh: run.sh: not found"))
    }

    @Test
    fun `the command line a bridge command reports is the argv it will send`() {
        val command = TermuxBridge.stopCommand()
        assertEquals("/system/bin/sh", command.executable)
        assertEquals(listOf("-c", "sh \"${TermuxBridge.RUNNER}\" stop"), command.arguments)
        assertEquals(TermuxBridge.RUNNER_APP_SHELL, command.runner)
        assertTrue(command.commandLine.startsWith("/system/bin/sh -c "))
    }

    @Test
    fun `install is the one command handed to a terminal session`() {
        assertEquals(TermuxBridge.RUNNER_TERMINAL, TermuxBridge.installCommand(null).runner)
        assertEquals(TermuxBridge.RUNNER_APP_SHELL, TermuxBridge.probeCommand().runner)
        assertEquals(TermuxBridge.RUNNER_APP_SHELL, TermuxBridge.startCommand(3080).runner)
    }

    @Test
    fun `a start command clamps an impossible port instead of sending it`() {
        assertTrue(TermuxBridge.startCommand(0).arguments.last().endsWith("start 1"))
        assertTrue(TermuxBridge.startCommand(99999).arguments.last().endsWith("start 65535"))
        assertTrue(TermuxBridge.startCommand(3080).arguments.last().endsWith("start 3080"))
    }

    @Test
    fun `a typed command is handed to the shell as one argument`() {
        // The app must not re-quote or split what the user typed: the runner does `exec sh -c "$*"`
        // and that is the only parse. A command with quotes and a pipe has to survive untouched.
        val typed = "ls -a ~/.dsha | head -5 && echo \"it's fine\""
        val command = TermuxBridge.shellCommand(typed)
        assertEquals(listOf("-c", "sh \"${TermuxBridge.RUNNER}\" shell $typed"), command.arguments)
    }

    @Test
    fun `the runner script carries every action the app can send`() {
        listOf("version", "probe", "start", "stop", "log", "install", "shell").forEach { action ->
            assertTrue(
                "runner script has no $action action",
                Regex("(?m)^\\s*$action\\)").containsMatchIn(TermuxBridge.runnerScript),
            )
        }
    }

    @Test
    fun `the runner script is a Termux shebang script and not a template`() {
        assertTrue(TermuxBridge.runnerScript.startsWith("#!/data/data/com.termux/files/usr/bin/sh\n"))
        assertTrue(TermuxBridge.runnerScript.contains("SCRIPT_VERSION=1"))
        // The hash is what the setup step and a bug report compare against; an accidental edit
        // that leaves it stale would silently stop matching the script on the device.
        assertEquals(64, TermuxBridge.runnerHash.length)
    }

    @Test
    fun `every dollar survives the trip out of the Kotlin string`() {
        // The script is written with a placeholder because Kotlin expands `$name` even in a raw
        // string. If the replacement ever stops running, the device gets a script full of
        // placeholders — a failure with no compiler error anywhere near it.
        assertFalse(TermuxBridge.runnerScript.contains("DOLLAR"))
        assertTrue(TermuxBridge.runnerScript.contains("export PATH=\"\u0024PREFIX_DIR/bin:\u0024PATH\""))
        assertTrue(TermuxBridge.runnerScript.contains("\u0024{HOME:-/data/data/com.termux/files/home}"))
        assertTrue(TermuxBridge.runnerScript.contains("case \"\u0024{1:-probe}\" in"))
    }
}
