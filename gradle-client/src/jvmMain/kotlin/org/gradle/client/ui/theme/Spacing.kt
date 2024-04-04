package org.gradle.client.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Suppress("MagicNumber")
data object Spacing {
    val level0 = 0.dp
    val level1 = 4.dp
    val level2 = 8.dp
    val level4 = 16.dp
    val level6 = 24.dp
    val paneSpacing = level6
    val topBarHeight = 56.dp
}

val MaterialTheme.spacing: Spacing
    get() = Spacing

@Composable
fun PaddingValues.plusPaneSpacing() =
    map(
        start = { it + Spacing.paneSpacing },
        end = { it + Spacing.paneSpacing },
    )

@Composable
private fun PaddingValues.copy(
    start: Dp? = null,
    top: Dp? = null,
    end: Dp? = null,
    bottom: Dp? = null
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = start ?: calculateStartPadding(layoutDirection),
        top = top ?: calculateTopPadding(),
        end = end ?: calculateEndPadding(layoutDirection),
        bottom = bottom ?: calculateBottomPadding(),
    )
}

@Composable
private fun PaddingValues.map(
    start: @Composable ((Dp) -> Dp)? = null,
    end: @Composable ((Dp) -> Dp)? = null,
    top: @Composable ((Dp) -> Dp)? = null,
    bottom: @Composable ((Dp) -> Dp)? = null
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return copy(
        start = start?.invoke(calculateStartPadding(layoutDirection)),
        top = top?.invoke(calculateTopPadding()),
        end = end?.invoke(calculateEndPadding(layoutDirection)),
        bottom = bottom?.invoke(calculateBottomPadding()),
    )
}
