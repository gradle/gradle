package org.gradle.client.ui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TitleLarge(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
fun TitleMedium(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
fun TitleSmall(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
fun BodyMedium(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun LabelSmall(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun LabelMedium(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.labelMedium,
    )
}
