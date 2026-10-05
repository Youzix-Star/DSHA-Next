/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.dsha.ui.material3.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.youzix.dsha.ui.material3.CornerRadius
import top.youzix.dsha.ui.material3.material3AppBarColor
import top.youzix.dsha.ui.material3.material3BlurEffect
import top.youzix.dsha.ui.material3.rememberMaterial3BlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 终端.
 *
 * A placeholder with the shape of a terminal and no terminal behind it: nothing here runs a
 * command, and nothing is wired to a shell yet.
 */
@Composable
fun MaterialTerminalScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
) {
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("终端", modifier = Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backdrop.material3AppBarColor(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = backdrop.material3AppBarColor(),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(PaddingValues(16.dp) + paddingValues + outerPadding),
        ) {
            // The panel is the scrollable's content, not its container: that is what makes the
            // whole panel the thing that stretches on overscroll instead of only the lines inside.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Taken out of the scope on purpose: inside the Column below there are two
                // implicit receivers, and `maxHeight` only exists on one of them.
                val paneHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(paneHeight)
                        .overScrollVertical()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Drawn on the inverse surface so it stays a dark pane in light mode and a
                    // light one in dark mode, with the matching inverse ink on top of it.
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(paneHeight),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(CornerRadius),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                        ) {
                            Text(
                                text = "\$ ▌",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "终端尚未接入",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}
