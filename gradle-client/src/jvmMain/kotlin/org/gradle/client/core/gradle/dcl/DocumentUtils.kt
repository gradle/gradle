package org.gradle.client.core.gradle.dcl

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.data.collectToMap
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution

fun DeclarativeDocument.relevantRange(): IntRange {
    val first = content.firstOrNull() ?: return IntRange.EMPTY
    val last = content.last()
    return IntRange(first.sourceData.indexRange.first, last.sourceData.indexRange.last)
}

fun DocumentWithResolution.errorRanges(): List<IntRange> =
    resolutionContainer.collectToMap(document).entries
        .filter { it.value is DocumentResolution.UnsuccessfulResolution }
        .map { it.key.sourceData.indexRange }