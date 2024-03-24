package org.gradle.client.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun GradleClientTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}
