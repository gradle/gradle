package org.gradle.client.ui.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import org.gradle.client.ui.theme.spacing

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

@Composable
fun HeadlineSmall(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.headlineSmall,
    )
}

@Composable
fun CodeBlock(
    modifier: Modifier = Modifier,
    code: AnnotatedString,
    onClick: (Int) -> Unit = {},
) {
    Surface(
        tonalElevation = MaterialTheme.spacing.level1,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
    ) {
        ClickableText(
            text = code,
            modifier = Modifier.padding(MaterialTheme.spacing.level2),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            onClick = onClick,
        )
    }
}
