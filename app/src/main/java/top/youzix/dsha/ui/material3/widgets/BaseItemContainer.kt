// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 NekoPlus contributors
//
// Ported from InstallerX-Revived (https://github.com/wxxsfxyzm/InstallerX-Revived),
// ui/page/main/widget/setting/BaseItemContainer.kt, with only the package renamed.
// Original: GPL-3.0-only, Copyright (C) 2026 InstallerX Revived contributors.

package top.youzix.dsha.ui.material3.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Gives a [SegmentedColumn] item the same `surfaceBright` card and dynamic corner shape that
 * the list-style widgets draw for themselves.
 *
 * [BaseWidget] and friends render their own background, so they do not need this. Widgets that
 * are just a bare layout — [IntNumberPickerWidget], for example — must be wrapped in it,
 * otherwise they float directly on the page background instead of sitting in a card.
 */
@Composable
fun BaseItemContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val baseShape = LocalSegmentedItemShape.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceBright

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = baseShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
