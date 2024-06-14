package org.gradle.client.core.gradle.dcl

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.data.collectToMap
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import java.util.*

fun DeclarativeDocument.relevantRange(): IntRange {
    val first = content.firstOrNull() ?: return IntRange.EMPTY
    val last = content.last()
    return IntRange(first.sourceData.indexRange.first, last.sourceData.indexRange.last)
}

fun DocumentWithResolution.errorRanges(): List<IntRange> =
    resolutionContainer.collectToMap(document).entries
        .filter { it.value is DocumentResolution.UnsuccessfulResolution }
        .map { it.key.sourceData.indexRange }

fun DeclarativeDocument.nodeAt(fileIdentifier: String, offset: Int): DeclarativeDocument.DocumentNode? {
    var node: DeclarativeDocument.DocumentNode? = null
    val stack: Deque<DeclarativeDocument.DocumentNode> = LinkedList()

    fun List<DeclarativeDocument.DocumentNode>.matchingContent(): List<DeclarativeDocument.DocumentNode> =
        filter {
            it.sourceData.sourceIdentifier.fileIdentifier == fileIdentifier &&
                    it.sourceData.indexRange.contains(offset)
        }

    stack.addAll(content.matchingContent())
    while (stack.isNotEmpty()) {
        when (val current = stack.pop()) {
            is DeclarativeDocument.DocumentNode.ElementNode -> {
                node = current
                stack.addAll(current.content.matchingContent())
            }

            else -> {
                node = current
            }
        }
    }
    return node
}
