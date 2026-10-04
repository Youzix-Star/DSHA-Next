/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui

import android.content.Context
import top.youzix.dsha.ui.predictiveback.PredictiveBackStyle

/**
 * Which UI engine draws the app.
 *
 * The two engines are complete, independent implementations of the same four tabs — one built on
 * miuix, one on Material Design — and the user picks between them on the About page.
 */
enum class UiEngine(val id: String, val label: String) {
    Miuix("miuix", "Miuix"),
    Material3("material3", "Material Design"),
    ;

    companion object {
        fun from(id: String?): UiEngine = entries.firstOrNull { it.id == id } ?: Miuix
    }
}

/** Persists the selected engine, the glass-effect switch and the diagnostic switch. */
object UiEnginePrefs {
    private const val PREFS = "ui_prefs"
    private const val KEY_ENGINE = "ui_engine"
    private const val KEY_USE_BLUR = "use_blur"
    private const val KEY_PREDICTIVE_BACK = "predictive_back"
    private const val KEY_DEBUG_MODE = "debug_mode"
    private const val KEY_SOFTWARE_RENDERING = "web_software_rendering"
    private const val KEY_USER_AGENT = "web_user_agent"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): UiEngine =
        UiEngine.from(prefs(context).getString(KEY_ENGINE, null))

    fun save(context: Context, engine: UiEngine) {
        prefs(context).edit().putString(KEY_ENGINE, engine.id).apply()
    }

    /**
     * Whether translucency is on: the miuix bottom bar's liquid glass and the Material 3 top
     * bar's blur. Defaults to `true`, matching the reference project.
     */
    fun loadUseBlur(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_BLUR, true)

    fun saveUseBlur(context: Context, useBlur: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_BLUR, useBlur).apply()
    }

    /** Which predictive-back animation second-level pages use. */
    fun loadPredictiveBackStyle(context: Context): PredictiveBackStyle =
        PredictiveBackStyle.from(prefs(context).getString(KEY_PREDICTIVE_BACK, null))

    fun savePredictiveBackStyle(context: Context, style: PredictiveBackStyle) {
        prefs(context).edit().putString(KEY_PREDICTIVE_BACK, style.id).apply()
    }

    /** Whether the diagnostic rows are on show. */
    fun loadDebugMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEBUG_MODE, false)

    fun saveDebugMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }

    /**
     * Draw the WebView in software instead of on the GPU.
     *
     * Off by default — it costs scroll performance. It exists because hardware acceleration has a
     * documented history of breaking WebView rendering on individual devices (Lightning Browser
     * ships the same escape hatch, with a comment saying exactly that).
     */
    fun loadSoftwareRendering(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOFTWARE_RENDERING, false)

    fun saveSoftwareRendering(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOFTWARE_RENDERING, enabled).apply()
    }

    /** Which User-Agent the browser claims to be; see [top.youzix.dsha.ui.web.UserAgent]. */
    fun loadUserAgent(context: Context) =
        top.youzix.dsha.ui.web.UserAgent.from(prefs(context).getString(KEY_USER_AGENT, null))

    fun saveUserAgent(context: Context, id: String) {
        prefs(context).edit().putString(KEY_USER_AGENT, id).apply()
    }
}
