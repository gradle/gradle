package org.gradle.client.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import org.gradle.client.ui.theme.spacing

internal data class SourceFileViewInput(
    val fileIdentifier: String,
    val fileContent: String,
    val relevantIndicesRange: IntRange?,
    val errorRanges: List<IntRange>,
    val highlightedSourceRange: IntRange?
)

@Composable
internal fun SourcesColumn(
    sources: List<SourceFileViewInput>,
    onClick: (String, Int) -> Unit
) {
    /**
     * Make everything that wants max width in the column as wide as the widest child composable.
     *
     * This is a workaround for the horizontally scrollable column not making all its
     * children that require max width equally wide automatically, potentially resulting in unequal widths.
     */
    Column(Modifier.width(IntrinsicSize.Max)) {
        val sourceFileData by derivedStateOf {
            sources.map { sourceFileViewInput ->
                val identifier = sourceFileViewInput.fileIdentifier
                val highlightedRangeOrNull = sourceFileViewInput.highlightedSourceRange
                val relevantIndices = sourceFileViewInput.relevantIndicesRange

                val highlightedString = sourceFileAnnotatedString(
                    highlightedRangeOrNull,
                    sourceFileViewInput.errorRanges,
                    sourceFileViewInput.fileContent
                )

                val relevantHighlightedString = relevantIndices?.let { range ->
                    val textWithTrimmedLines = omitIrrelevantLines(highlightedString, relevantIndices, range)
                    trimIndentationWhitespaces(textWithTrimmedLines)
                }
                SourceFileData(
                    identifier,
                    highlightedString,
                    relevantHighlightedString
                )
            }
        }

        sourceFileData.forEach { data ->
            SourceFileTitleAndText(
                data.relativePath,
                data.annotatedSource,
                data.trimmedSource,
                onClick
            )
            MaterialTheme.spacing.VerticalLevel4()
        }
    }
}


private data class SourceFileData(
    val relativePath: String,
    val annotatedSource: AnnotatedString,
    val trimmedSource: TrimmedText?
)

private fun sourceFileAnnotatedString(
    highlightedSourceRange: IntRange?,
    errorRanges: List<IntRange>,
    fileContent: String
) = buildAnnotatedString {
    append(fileContent)

    if (highlightedSourceRange != null) {
        addStyle(SpanStyle(background = Color.Yellow), highlightedSourceRange.first, highlightedSourceRange.last + 1)
    }

    for (errorRange in errorRanges) {
        addStyle(
            SpanStyle(color = Color.Red).plus(SpanStyle(textDecoration = TextDecoration.LineThrough)),
            errorRange.first, errorRange.last + 1
        )
    }
}

@Composable
private fun SourceFileTitleAndText(
    fileRelativePath: String,
    highlightedSource: AnnotatedString,
    trimmedSource: TrimmedText?,
    onClick: (String, Int) -> Unit
) {
    if (trimmedSource != null) {
        var isTrimmed by remember { mutableStateOf(true) }

        TitleMedium(fileRelativePath)

        val linesTrimmed = highlightedSource.text.lines().count() - trimmedSource.annotatedString.text.lines().count()
        val text = if (isTrimmed)
            "($linesTrimmed irrelevant lines omitted, click to show)"
        else "(hide irrelevant lines)"

        TextButton(onClick = {
            isTrimmed = !isTrimmed
        }) {
            Text(text, modifier = Modifier.alpha(0.5f))
        }

        MaterialTheme.spacing.VerticalLevel2()
        CodeBlock(
            Modifier.fillMaxWidth(),
            if (isTrimmed) trimmedSource.annotatedString else highlightedSource
        ) { clickOffset ->
            val originalOffset = if (isTrimmed)
                trimmedSource.mapIndexToIndexInOriginalText(clickOffset)
            else clickOffset

            onClick(fileRelativePath, originalOffset)
        }
    } else {
        TitleMedium(fileRelativePath)
        MaterialTheme.spacing.VerticalLevel4()
        CodeBlock(Modifier.fillMaxWidth(), highlightedSource) { clickOffset ->
            onClick(fileRelativePath, clickOffset)
        }
    }
}

private fun omitIrrelevantLines(
    highlightedString: AnnotatedString,
    relevantIndices: IntRange,
    range: IntRange
): TrimmedText {
    val lineBreakBeforeFocus =
        highlightedString.text.take(relevantIndices.first).indexOfLast { it == '\n' } + 1

    val linesDropped =
        highlightedString.text.lineSequence()
            .runningFold(0) { acc, it -> acc + it.length + 1 }
            .takeWhile { it < lineBreakBeforeFocus }
            .count()

    return TrimmedText(
        highlightedString,
        highlightedString.subSequence(TextRange(lineBreakBeforeFocus, range.last + 1)),
        droppedInitialLines = linesDropped,
        droppedPrefixWhitespaces = 0
    )
}

private fun trimIndentationWhitespaces(text: TrimmedText): TrimmedText {
    val lines = text.annotatedString.text.lines()
    val indentation = lines.filter { it.isNotBlank() }.minOf { it.takeWhile(Char::isWhitespace).length }
    return text.copy(
        annotatedString = buildAnnotatedString {
            var nextLineStart = 0
            for ((index, line) in lines.withIndex()) {
                val lineStartToTake = nextLineStart + if (line.isBlank()) 0 else indentation
                val endIndex = nextLineStart + line.length + if (index != lines.lastIndex) 1 else 0
                val annotatedLine = text.annotatedString.subSequence(lineStartToTake, endIndex)
                append(annotatedLine)
                nextLineStart = endIndex
            }
        },
        droppedPrefixWhitespaces = indentation
    )
}

private
data class TrimmedText(
    val originalText: AnnotatedString,
    val annotatedString: AnnotatedString,
    val droppedInitialLines: Int,
    val droppedPrefixWhitespaces: Int
) {
    fun mapIndexToIndexInOriginalText(index: Int): Int {
        val (lineNumber, lineOffset) = annotatedString.text.lineSequence()
            .runningFold(0) { acc, it -> acc + it.length + 1 }
            .takeWhile { it < index }.withIndex()
            .lastOrNull()
            ?: IndexedValue(0, index)
        val originalLineNumber = lineNumber + droppedInitialLines
        val originalOffset = index - lineOffset + droppedPrefixWhitespaces
        return originalText.text.lineSequence().take(originalLineNumber).sumOf { it.length + 1 } + originalOffset
    }
}