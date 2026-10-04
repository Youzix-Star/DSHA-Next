/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.youzix.dsha.ui.UiEngine
import top.youzix.dsha.ui.predictiveback.PredictiveBackStyle

/**
 * Process-wide UI state for the shell.
 *
 * This build is the interface shell only: the four tabs draw themselves and remember how the user
 * wants them drawn, and nothing behind them drives Termux yet.
 */
object AppState {
    /** Which engine draws the app; persisted on change via [top.youzix.dsha.ui.UiEnginePrefs]. */
    var engine by mutableStateOf(UiEngine.Miuix)

    /**
     * Translucent surfaces: the miuix bottom bar's liquid glass, and the Material 3 top bar's
     * blur. Persisted alongside the engine. Switching it off also avoids the RenderEffect cost
     * on devices that can technically render it.
     */
    var useBlur by mutableStateOf(true)

    /** Which predictive-back animation plays on second-level pages. */
    var predictiveBackStyle by mutableStateOf(PredictiveBackStyle.Miuix)

    /**
     * Whether the diagnostic rows are on show.
     *
     * Kept in memory and on disk: the About page unlocks it by tapping the mark seven times.
     */
    var debugMode by mutableStateOf(false)
}
