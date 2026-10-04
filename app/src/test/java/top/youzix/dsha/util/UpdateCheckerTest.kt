/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package top.youzix.dsha.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparison is what decides whether the user is offered anything at all, and it is the part
 * of the checker that a device cannot check: a wrong answer here looks exactly like "no update".
 */
class UpdateCheckerTest {
    @Test
    fun newerNumbersWin() {
        assertTrue(isNewer("0.1.2", "0.1.1"))
        assertTrue(isNewer("0.2.0", "0.1.9"))
        assertTrue(isNewer("1.0.0", "0.9.9"))
        assertFalse(isNewer("0.1.1", "0.1.1"))
        assertFalse(isNewer("0.1.0", "0.1.1"))
    }

    @Test
    fun aReleaseBeatsItsOwnPreReleases() {
        assertTrue(isNewer("2.0.0", "2.0.0-alpha.2"))
        assertTrue(isNewer("2.0.0", "2.0.0-rc.1"))
        assertFalse(isNewer("2.0.0-alpha.2", "2.0.0"))
    }

    @Test
    fun preReleaseOrderingIsNumeric() {
        assertTrue(isNewer("2.0.0-alpha.10", "2.0.0-alpha.2"))
        assertTrue(isNewer("2.0.0-beta.1", "2.0.0-alpha.9"))
        assertTrue(isNewer("2.0.0-rc.1", "2.0.0-beta.3"))
        assertFalse(isNewer("2.0.0-alpha.1", "2.0.0-beta.1"))
    }

    @Test
    fun versionsPeopleActuallyWrite() {
        // `v` prefix, spaces, and a version name with no separators at all.
        assertTrue(isNewer("v0.2.0", "0.1.0"))
        assertTrue(isNewer("2.0.1 Beta 1", "2.0.0"))
        assertTrue(isNewer("2.0.2Beta1", "2.0.1"))
        assertFalse(isNewer("0.1.1", "0.1.1"))
    }
}
