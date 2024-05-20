package org.gradle.client.ui.connected

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gradle.client.ui.theme.spacing

private const val DEFAULT_LEFT_WEIGHT = 0.2f
private const val DEFAULT_RIGHT_WEIGHT = 0.8f

@Composable
@Suppress("LongParameterList")
fun TwoPanes(
    modifier: Modifier = Modifier,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
    leftWeight: Float = DEFAULT_LEFT_WEIGHT,
    rightWeight: Float = DEFAULT_RIGHT_WEIGHT,
    scrollable: Boolean = true,
) {
    Row(modifier) {
        Column(
            modifier = Modifier.padding(end = MaterialTheme.spacing.level2)
                .weight(leftWeight)
                .run { if (scrollable) verticalScroll(rememberScrollState()) else this },
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.level2),
        ) {
            left()
        }
        Column(
            modifier = Modifier.weight(rightWeight)
                .run { if (scrollable) verticalScroll(rememberScrollState()) else this },
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.level2),
        ) {
            right()
        }
    }
}
