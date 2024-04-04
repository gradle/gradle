package org.gradle.client.ui.connected

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.gradle.client.ui.theme.spacing

private const val LEFT_WEIGHT = 0.3f
private const val RIGHT_WEIGHT = 0.7f

@Composable
fun TwoPanes(
    modifier: Modifier = Modifier,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier) {
        Column(
            modifier = Modifier.padding(end = MaterialTheme.spacing.level2)
                .weight(LEFT_WEIGHT)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.level2),
        ) {
            left()
        }
        Column(
            modifier = Modifier.weight(RIGHT_WEIGHT)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.level2),
        ) {
            right()
        }
    }
}
