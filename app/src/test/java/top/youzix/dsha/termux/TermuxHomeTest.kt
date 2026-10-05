/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页's button list, which is the one place both UI engines agree by construction.
 *
 * The engines draw whatever [homeFrom] returns, so if this list is wrong the bug shows up twice.
 * That also makes it the cheapest thing to pin down in a test: it is a pure function of the
 * snapshot, with no Compose and no Android in it.
 */
class TermuxHomeTest {

    private fun snapshot(
        setup: TermuxSetup,
        installed: Boolean = false,
        running: Boolean = false,
        url: String = "",
        phase: TermuxPhase = TermuxPhase.IDLE,
        lastError: String? = null,
    ) = TermuxSnapshot(
        setup = setup,
        phase = phase,
        termuxVersion = if (setup == TermuxSetup.NOT_INSTALLED) null else "0.118.3",
        dshVersion = if (installed) "0.2.0-rc.2" else "",
        dshInstalled = installed,
        running = running,
        port = 3080,
        url = url,
        busyLabel = null,
        lastError = lastError,
        lastOutput = "",
    )

    private fun home(snapshot: TermuxSnapshot) = homeFrom(snapshot, onOpenWeb = {})

    @Test
    fun `without Termux the page offers detection and a way to get it`() {
        val home = home(snapshot(TermuxSetup.NOT_INSTALLED))
        assertEquals(StatusTone.WARN, home.status.tone)
        assertEquals(listOf("probe", "openTermux"), home.actions.map { it.id })
        // The accent button is the one that moves the situation forward, not the one that re-reads it.
        assertEquals(listOf("openTermux"), home.actions.filter { it.primary }.map { it.id })
    }

    @Test
    fun `without the permission the runtime dialog is the accent button`() {
        val home = home(snapshot(TermuxSetup.PERMISSION))
        assertEquals(StatusTone.WARN, home.status.tone)
        // Both routes exist: re-check after granting, and the dialog itself. The dialog is the
        // accent because it is the only one that can change the answer.
        assertEquals(listOf("probe", "requestPermission"), home.actions.map { it.id })
        assertEquals(listOf("requestPermission"), home.actions.filter { it.primary }.map { it.id })
    }

    @Test
    fun `a Termux that refuses external calls gets the exact command to fix it`() {
        // Termux's own error text carries this string; it is the one failure the app cannot fix
        // from here, because the file lives in Termux's private storage.
        val home = home(
            snapshot(
                TermuxSetup.READY,
                installed = true,
                lastError = "allow-external-apps property is not set to \"true\" in termux.properties",
            ),
        )
        assertEquals(StatusTone.BAD, home.status.tone)
        assertEquals(ALLOW_EXTERNAL_APPS_COMMAND, home.remedy)
        assertTrue(home.status.headline.contains("外部调用"))
    }

    @Test
    fun `no remedy is offered when nothing is blocked on Termux's own settings`() {
        assertNull(home(snapshot(TermuxSetup.READY, installed = true)).remedy)
        assertNull(home(snapshot(TermuxSetup.SCRIPTS_MISSING)).remedy)
        assertNull(home(snapshot(TermuxSetup.READY, installed = true, lastError = "没有收到 Termux 的回复（超时）")).remedy)
    }

    @Test
    fun `without the scripts the only useful thing is writing them`() {
        val home = home(snapshot(TermuxSetup.SCRIPTS_MISSING))
        assertEquals(StatusTone.IDLE, home.status.tone)
        assertEquals(listOf("probe", "setup"), home.actions.map { it.id })
        assertEquals(listOf("setup"), home.actions.filter { it.primary }.map { it.id })
    }

    @Test
    fun `with everything ready but no dsh, install leads`() {
        val home = home(snapshot(TermuxSetup.READY))
        assertEquals(listOf("probe", "install"), home.actions.map { it.id })
        assertEquals(listOf("install"), home.actions.filter { it.primary }.map { it.id })
        assertTrue(home.status.detail.contains("2～10 分钟"))
    }

    @Test
    fun `with dsh installed but stopped, start leads`() {
        val home = home(snapshot(TermuxSetup.READY, installed = true))
        assertEquals(listOf("probe", "log", "install", "start"), home.actions.map { it.id })
        assertEquals(listOf("start"), home.actions.filter { it.primary }.map { it.id })
    }

    @Test
    fun `with dsh running, stop is offered and the web UI becomes the accent`() {
        val home = home(
            snapshot(
                TermuxSetup.READY,
                installed = true,
                running = true,
                url = "http://127.0.0.1:3080/?token=abc-123",
            ),
        )
        assertEquals(listOf("probe", "log", "install", "stop", "openWeb"), home.actions.map { it.id })
        assertEquals(listOf("openWeb"), home.actions.filter { it.primary }.map { it.id })
        assertTrue(home.canOpenWeb)
        assertEquals(StatusTone.OK, home.status.tone)
    }

    @Test
    fun `running with an unread token is still running, and says so`() {
        val home = home(snapshot(TermuxSetup.READY, installed = true, running = true, url = ""))
        assertEquals(StatusTone.OK, home.status.tone)
        assertFalse(home.canOpenWeb)
        assertTrue(home.status.detail.contains("查看日志"))
        assertTrue(home.actions.none { it.id == "openWeb" })
    }

    @Test
    fun `a revoked permission outranks a stale probe that remembers a running dsh`() {
        // The point of the ordering: whatever the last probe saw, if commands cannot be sent now,
        // that is the headline.
        val home = home(snapshot(TermuxSetup.PERMISSION, installed = true, running = true))
        assertEquals(StatusTone.WARN, home.status.tone)
        assertTrue(home.actions.none { it.id == "stop" })
    }

    @Test
    fun `every button is disabled while a command is in flight`() {
        val home = home(snapshot(TermuxSetup.READY, installed = true, phase = TermuxPhase.BUSY))
        assertTrue(home.actions.isNotEmpty())
        assertTrue(home.actions.all { !it.enabled })
    }

    @Test
    fun `the welcome text admits there is no interactive terminal`() {
        val text = terminalWelcome(snapshot(TermuxSetup.READY, installed = true))
        assertTrue(text.contains("没有交互式 TTY"))
        assertFalse(text.contains("PTY 已就绪"))
    }
}
