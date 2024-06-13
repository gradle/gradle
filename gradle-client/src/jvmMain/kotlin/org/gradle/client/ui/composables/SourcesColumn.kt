package org.gradle.client.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import org.gradle.client.ui.theme.spacing

internal data class SourceFileViewInput(
    val fileIdentifier: String,
    val fileContent: String,
    val relevantIndicesRange: IntRange?
)

@Composable
internal fun SourcesColumn(
    sources: List<SourceFileViewInput>,
    highlightedSourceRangeByFileId: MutableState<Map<String, IntRange>>
) {
    Column {
        val sourceFileData by derivedStateOf {
            sources.map { (identifier, content, relevantIndices) ->
                val highlightedRangeOrNull = highlightedSourceRangeByFileId.value[identifier]
                val highlightedString = sourceFileAnnotatedString(highlightedRangeOrNull, content)
                val relevantHighlightedString = relevantIndices?.let { range ->
                    val lineBreakBeforeFocus =
                        highlightedString.text.take(relevantIndices.first).indexOfLast { it == '\n' } + 1

                    trimIndentationWhitespaces(
                        highlightedString.subSequence(TextRange(lineBreakBeforeFocus, range.last + 1))
                    )
                }
                SourceFileData(
                    identifier,
                    highlightedString,
                    relevantHighlightedString
                )
            }
        }

        sourceFileData.forEach { data ->
            SourceFileTitleAndText(data.relativePath, data.annotatedSource, data.trimmedSource)
            MaterialTheme.spacing.VerticalLevel4()
        }
    }
}



private data class SourceFileData(
    val relativePath: String,
    val annotatedSource: AnnotatedString,
    val trimmedSource: AnnotatedString?
)

private fun sourceFileAnnotatedString(
    highlightedSourceRange: IntRange?,
    fileContent: String
) = buildAnnotatedString {
    when {
        highlightedSourceRange == null -> append(fileContent)

        else -> {
            append(fileContent.substring(0, highlightedSourceRange.first))
            withStyle(style = SpanStyle(background = Color.Yellow)) {
                append(fileContent.substring(highlightedSourceRange))
            }
            append(fileContent.substring(highlightedSourceRange.last + 1))
        }
    }
}

@Composable
private fun SourceFileTitleAndText(
    fileRelativePath: String,
    highlightedSource: AnnotatedString,
    trimmedSource: AnnotatedString?
) {
    if (trimmedSource != null) {
        var isTrimmed by remember { mutableStateOf(true) }

        TitleMedium(fileRelativePath)

        val linesTrimmed = highlightedSource.text.lines().count() - trimmedSource.text.lines().count()
        val text = if (isTrimmed)
            "($linesTrimmed irrelevant lines omitted, click to show)"
        else "(hide irrelevant lines)"

        TextButton(onClick = {
            isTrimmed = !isTrimmed
        }) {
            Text(text, modifier = Modifier.alpha(0.5f))
        }

        MaterialTheme.spacing.VerticalLevel2()
        CodeBlock(Modifier.fillMaxWidth(), if (isTrimmed) trimmedSource else highlightedSource)
    } else {
        TitleMedium(fileRelativePath)
        MaterialTheme.spacing.VerticalLevel4()
        CodeBlock(Modifier.fillMaxWidth(), highlightedSource)
    }
}

private
fun trimIndentationWhitespaces(annotatedString: AnnotatedString): AnnotatedString {
    val lines = annotatedString.lines()
    val indentation = lines.filter { it.isNotBlank() }.minOf { it.takeWhile(Char::isWhitespace).length }
    return buildAnnotatedString {
        var nextLineStart = 0
        for ((index, line) in lines.withIndex()) {
            val lineStartToTake = nextLineStart + if (line.isBlank()) 0 else indentation
            val endIndex =  nextLineStart + line.length + if (index != lines.lastIndex) 1 else 0
            val annotatedLine = annotatedString.subSequence(lineStartToTake, endIndex)
            append(annotatedLine)
            nextLineStart = endIndex
        }
    }
}
