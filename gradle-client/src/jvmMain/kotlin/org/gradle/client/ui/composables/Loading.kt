package org.gradle.client.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.gradle.client.ui.theme.spacing

@Composable
fun Loading() {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = MaterialTheme.spacing.level6),
        contentAlignment = Alignment.Center
    ) {
        Column {
            CircularProgressIndicator()
        }
    }
}
