package org.gradle.client.core.gradle.dcl

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument

fun DeclarativeDocument.relevantRange(): IntRange {
    val first = content.firstOrNull() ?: return IntRange.EMPTY
    val last = content.last()
    return IntRange(first.sourceData.indexRange.first, last.sourceData.indexRange.last)
}