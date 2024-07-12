package org.gradle.client.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
fun FailureContent(exception: Exception) {
    Column {
        horizontalScrollContent {
            SelectionContainer {
                Text(
                    text = exception.stackTraceToString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
