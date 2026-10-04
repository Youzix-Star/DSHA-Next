// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 NekoPlus contributors
//
// Ported from InstallerX-Revived (https://github.com/wxxsfxyzm/InstallerX-Revived),
// ui/page/main/widget/snackbar/SwipeableSnackbarHost.kt. Original: GPL-3.0-only,
// Copyright (C) 2026 InstallerX Revived contributors.

package top.youzix.dsha.ui.material3.widgets

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/**
 * A [SnackbarHost] whose message can be swiped away, matching the reference project.
 *
 * The dismiss state is shared across successive messages, so it is snapped back to
 * [SwipeToDismissBoxValue.Settled] whenever a new one arrives — otherwise the second snackbar
 * would appear already half-thrown-off-screen, or never show at all.
 */
@Composable
fun SwipeableSnackbarHost(
    modifier: Modifier = Modifier,
    hostState: SnackbarHostState,
    snackbar: @Composable (SnackbarData) -> Unit = { Snackbar(it) },
) {
    val state = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = state,
        backgroundContent = {},
        onDismiss = {
            hostState.currentSnackbarData?.dismiss()
        },
    ) {
        SnackbarHost(
            modifier = modifier,
            hostState = hostState,
            snackbar = snackbar,
        )
    }

    LaunchedEffect(hostState.currentSnackbarData) {
        if (hostState.currentSnackbarData == null) return@LaunchedEffect

        state.snapTo(SwipeToDismissBoxValue.Settled)
    }
}
