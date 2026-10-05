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
 * 不需要设备的那部分。
 *
 * 这里测的都是纯字符串函数：DSHA 的 status.sh 输出的解析、命令结果的可读化、以及那条写安装脚本的
 * 命令载荷。发送 intent、等回包、等终端回显这些必须真机验证；能在这儿钉住的就这些，而它们正是
 * 改起来最容易改坏的。
 */
class TermuxBridgeTest {

    @Test
    fun `status output is read the way DSHA reads it`() {
        val facts = TermuxFacts.from(
            """
            home=/data/data/com.termux/files/home
            repo=yes
            dsh_bin=yes
            dsh_version=0.2.0-rc.2
            install_dir=yes
            node=v26.4.0
            wake_lock=yes
            server=running
            """.trimIndent(),
        )

        assertTrue(facts.repoCloned)
        assertTrue(facts.dshBinAvailable)
        assertTrue(facts.installDirReady)
        assertEquals("0.2.0-rc.2", facts.dshVersion)
        assertEquals("v26.4.0", facts.nodeVersion)
        assertTrue(facts.serverRunning)
    }

    @Test
    fun `a missing dsh is not mistaken for an installed one`() {
        val facts = TermuxFacts.from(
            """
            repo=no
            dsh_bin=no
            install_dir=no
            server=stopped
            """.trimIndent(),
        )

        assertFalse(facts.dshBinAvailable)
        assertFalse(facts.serverRunning)
        // 空值要给一个能直接显示的占位符，而不是空字符串 —— DSHA 的 status.sh 也是这么期望的。
        assertEquals("-", facts.dshVersion)
        assertEquals("-", facts.nodeVersion)
    }

    @Test
    fun `garbage in the status output does not break the parse`() {
        val facts = TermuxFacts.from("warning: something\nnonsense\nserver=stopped\n=empty-key\n")
        assertFalse(facts.serverRunning)
        assertEquals("-", facts.dshVersion)
    }

    @Test
    fun `an empty status output is the unknown state`() {
        assertEquals(TermuxFacts.Unknown, TermuxFacts.from(""))
    }

    @Test
    fun `result display joins stdout and stderr and keeps an empty one readable`() {
        val both = TermuxBridge.Result("out\n", "err\n", 0, null)
        assertEquals("out\nerr", both.combined)

        val silent = TermuxBridge.Result("", "", 0, null)
        assertEquals("", silent.combined)
        assertTrue(silent.ok)

        val failed = TermuxBridge.Result("", "boom", 1, null)
        assertEquals("boom", failed.errorText)
        assertFalse(failed.ok)
    }

    @Test
    fun `ok requires the exit code to be zero and no error message`() {
        assertTrue(TermuxBridge.Result("", "", 0, null).ok)
        assertFalse(TermuxBridge.Result("", "", 1, null).ok)
        assertFalse(TermuxBridge.Result("", "", 0, "refused").ok)
        // 超时那条路径没有退出码，只有 errmsg。
        val timedOut = TermuxBridge.Result("", "", null, "执行超时（20 秒未返回结果）")
        assertFalse(timedOut.ok)
        assertEquals("执行超时（20 秒未返回结果）", timedOut.errorText)
    }

    @Test
    fun `the allow-external-apps refusal is recognised by its own text`() {
        // 首页靠这个判断把「那行命令」显示出来，所以它是契约的一部分，不是随手写的判断。
        assertTrue(
            TermuxBridge.isAllowExternalAppsError(
                "allow-external-apps property is not set to \"true\" in termux.properties",
            ),
        )
        assertFalse(TermuxBridge.isAllowExternalAppsError("执行超时（20 秒未返回结果）"))
        assertFalse(TermuxBridge.isAllowExternalAppsError(null))
    }

    @Test
    fun `commands go through Termux's own bash, not the system shell`() {
        // `-lc` 由 TermuxBridge 加上，登录 profile 才有 $PREFIX/bin 的 PATH —— dsh 就是这么找到的。
        assertEquals("/data/data/com.termux/files/usr/bin/bash", TermuxBridge.TERMUX_BASH)
        assertEquals("/data/data/com.termux/files/usr", TermuxBridge.TERMUX_PREFIX)
        assertEquals("/data/data/com.termux/files/home", TermuxBridge.TERMUX_HOME)
    }

    @Test
    fun `the bridge reads Termux's own permission and package names`() {
        // 这两个串是从 termux-app 的 TermuxConstants.java 抄来的，写错一个字就是一整轮 CI 也查不出。
        assertEquals("com.termux", TermuxBridge.TERMUX_PACKAGE)
        assertEquals("com.termux.permission.RUN_COMMAND", TermuxBridge.PERMISSION_RUN_COMMAND)
    }
}
