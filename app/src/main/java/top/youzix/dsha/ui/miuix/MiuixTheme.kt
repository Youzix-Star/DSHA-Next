/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.miuix

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Dark flag of the colour scheme Miuix currently resolves to.
 *
 * The vendored liquid-glass components need it, and reading it from the composition keeps every
 * layer in agreement with [ThemeController].
 */
private val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun isInDarkTheme(): Boolean = LocalIsDarkTheme.current

/** Theme modes offered in Settings, in display order. */
val ThemeModeOptions: List<Pair<ColorSchemeMode, String>> = listOf(
    ColorSchemeMode.System to "跟随系统",
    ColorSchemeMode.Light to "浅色",
    ColorSchemeMode.Dark to "深色",
    ColorSchemeMode.MonetSystem to "动态取色 · 跟随系统",
    ColorSchemeMode.MonetLight to "动态取色 · 浅色",
    ColorSchemeMode.MonetDark to "动态取色 · 深色",
)

@Composable
fun MiuixAppTheme(
    colorSchemeMode: ColorSchemeMode,
    content: @Composable () -> Unit,
) {
    val controller = remember(colorSchemeMode) { ThemeController(colorSchemeMode = colorSchemeMode) }
    val isDark = when (colorSchemeMode) {
        ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
        ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
        else -> isSystemInDarkTheme()
    }
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalIsDarkTheme provides isDark, content = content)
    }
}
