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
 * 首页那点只读状态。
 *
 * 这一版首页不再提供任何会执行命令的入口（终端页是 Termux 的画面，不是 shell），所以这里测的是
 * 「看到什么就说什么」：分支顺序、语气、以及两行前置条件与三格统计。
 * 它是纯函数，两个引擎画的都是它的输出，所以错一次会错两遍。
 */
class TermuxHomeTest {

    private fun snapshot(
        setup: TermuxSetup,
        installed: Boolean = false,
        running: Boolean = false,
        url: String = "",
        lastError: String? = null,
    ) = TermuxSnapshot(
        setup = setup,
        termuxVersion = if (setup == TermuxSetup.NOT_INSTALLED) null else "0.118.3",
        dshVersion = if (installed) "0.2.0-rc.2" else "",
        dshInstalled = installed,
        running = running,
        port = 3080,
        url = url,
        lastError = lastError,
        lastOutput = "",
    )

    private fun home(snapshot: TermuxSnapshot) = homeFrom(snapshot)

    @Test
    fun `without Termux the page says so and points at the F-Droid build`() {
        val status = home(snapshot(TermuxSetup.NOT_INSTALLED)).status
        assertEquals(StatusTone.WARN, status.tone)
        assertTrue(status.headline.contains("Termux"))
        assertTrue(status.detail.contains("F-Droid"))
    }

    @Test
    fun `without the permission the page names the permission`() {
        val status = home(snapshot(TermuxSetup.PERMISSION)).status
        assertEquals(StatusTone.WARN, status.tone)
        assertTrue(status.detail.contains("RUN_COMMAND"))
    }

    @Test
    fun `a revoked permission outranks a stale probe that remembers a running dsh`() {
        // 分支顺序的意义：上一次探测记得「在跑」也不能算数，当前的问题才是要说的那句。
        val status = home(snapshot(TermuxSetup.PERMISSION, installed = true, running = true)).status
        assertEquals(StatusTone.WARN, status.tone)
        assertTrue(status.detail.contains("RUN_COMMAND"))
    }

    @Test
    fun `with a bridge but no dsh the page says the runtime is missing`() {
        val status = home(snapshot(TermuxSetup.READY)).status
        assertEquals(StatusTone.WARN, status.tone)
        assertTrue(status.detail.contains("dsh"))
        assertFalse(status.detail.contains("2～10 分钟"))
    }

    @Test
    fun `with dsh installed but stopped the page says it is not running`() {
        val status = home(snapshot(TermuxSetup.READY, installed = true)).status
        assertEquals(StatusTone.IDLE, status.tone)
        assertTrue(status.detail.contains("没在运行"))
    }

    @Test
    fun `with dsh running the page shows the address`() {
        val home = home(
            snapshot(
                TermuxSetup.READY,
                installed = true,
                running = true,
                url = "http://127.0.0.1:3080/?token=abc-123",
            ),
        )
        assertEquals(StatusTone.OK, home.status.tone)
        assertEquals("网页界面：http://127.0.0.1:3080", home.status.detail)
        assertTrue(home.canOpenWeb)
    }

    @Test
    fun `running with an unread token is still running`() {
        val home = home(snapshot(TermuxSetup.READY, installed = true, running = true, url = ""))
        assertEquals(StatusTone.OK, home.status.tone)
        assertFalse(home.canOpenWeb)
    }

    @Test
    fun `the two prerequisite rows follow the same state`() {
        val rows = home(snapshot(TermuxSetup.READY, installed = true)).rows
        assertEquals(listOf("bridge", "runtime"), rows.map { it.id })
        assertTrue(rows[0].active)
        assertTrue(rows[1].active)
        assertTrue(rows[0].summary.contains("命令"))

        val missing = home(snapshot(TermuxSetup.NOT_INSTALLED)).rows
        assertFalse(missing[0].active)
        assertFalse(missing[1].active)
        assertTrue(missing[0].summary.contains("com.termux"))
    }

    @Test
    fun `the three statistics read from the same snapshot`() {
        val stats = home(snapshot(TermuxSetup.READY, installed = true, running = true)).stats
        assertEquals(listOf("服务", "桥接", "环境"), stats.map { it.title })
        assertEquals(listOf("运行中", "已连通", "已就绪"), stats.map { it.value })

        val stopped = home(snapshot(TermuxSetup.NOT_INSTALLED)).stats
        assertEquals(listOf("已停止", "未连通", "未安装"), stopped.map { it.value })
    }
}
