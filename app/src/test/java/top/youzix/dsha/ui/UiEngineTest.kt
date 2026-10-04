/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The engine id is what gets persisted, so an id that no longer exists has to resolve to something
 * drawable rather than crash the first frame after an upgrade.
 */
class UiEngineTest {
    @Test
    fun knownIdsResolveToTheirEngine() {
        for (engine in UiEngine.entries) {
            assertEquals(engine, UiEngine.from(engine.id))
        }
    }

    @Test
    fun unknownOrMissingIdFallsBackToMiuix() {
        assertEquals(UiEngine.Miuix, UiEngine.from(null))
        assertEquals(UiEngine.Miuix, UiEngine.from(""))
        assertEquals(UiEngine.Miuix, UiEngine.from("nope"))
    }
}
