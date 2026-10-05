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

    // There is deliberately no test of the command builders here. Every one of them now needs a
    // `Context` (the run script is an asset), so a JVM test could only assert against a stand-in —
    // which would be a test of the stand-in. What is worth checking about them is checked where it
    // can be real: `scripts/runscript.py` runs the actual asset through `sh -n` and asserts every
    // action is handled, and CI compiles the builders against the real Android SDK.

    // The run script itself is no longer a Kotlin string: it ships as `assets/run.sh` and is handed
    // to Termux verbatim, so its syntax and its action table are checked by `scripts/runscript.py`
    // (which runs it through `sh -n`) rather than from here. What is left to pin down on the JVM is
    // the argv this class builds around it.
    @Test
    fun `every command goes through bash with the script and the action`() {
        // `-lc` and Termux's own bash: the login profile is what puts $PREFIX/bin on PATH, which is
        // how `dsh` is found at all.
        assertEquals(TermuxBridge.TERMUX_BASH, TermuxBridge.installCommand(null).executable)
        assertTrue(TermuxBridge.TERMUX_BASH.startsWith("/data/data/com.termux/files/usr/bin/"))
    }
}
