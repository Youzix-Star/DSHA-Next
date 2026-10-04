/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's mark, as text: a face assembled out of letters that already look like one, for the
 * places that want the mark without shipping artwork (the start state of the web tab).
 *
 * The launcher icon is separate art under `res/mipmap-*` and does not contain this string, so the
 * two no longer have to move together.
 *
 * Codepoints: U+1526, U+00B0, U+A4B3, U+00B0, U+1528, U+0341, U+0316, U+002D.
 */
const val AppIconText = "ᔦ ° ꒳ ° ᔨ ̖́-"

/** Material icons used by the app. */
object AppIcons {
    // The four tabs.
    val Home: ImageVector = Icons.Rounded.Home
    val Web: ImageVector = Icons.Rounded.Public
    val Terminal: ImageVector = Icons.Rounded.Terminal
    val About: ImageVector = Icons.Rounded.Info

    val SourceCode: ImageVector = Icons.Rounded.Code
    val License: ImageVector = Icons.Rounded.Description
    val Developer: ImageVector = Icons.Rounded.Person
    val Phones: ImageVector = Icons.Rounded.PhoneAndroid
    val Log: ImageVector = Icons.AutoMirrored.Rounded.Article
    val Debug: ImageVector = Icons.Rounded.BugReport

    val Refresh: ImageVector = Icons.Rounded.Refresh
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val Forward: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward
}
