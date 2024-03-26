package org.gradle.client.ui.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

@Composable
fun BackIcon(onClick: () -> Unit) {
    IconButton(onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
    }
}
