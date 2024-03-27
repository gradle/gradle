package org.gradle.client.ui.composables

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlainTextTooltip(
    text: String,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        state = rememberTooltipState(),
        tooltip = { PlainTooltip { Text(text, textAlign = TextAlign.Center) } }
    ) {
        content()
    }
}
