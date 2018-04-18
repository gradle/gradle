package org.gradle.kotlin.dsl.fixtures

import org.gradle.util.TextUtil


fun String.toPlatformLineSeparators() =
    TextUtil.toPlatformLineSeparators(this)


fun <T> Iterable<T>.joinLines(transform: (T) -> String) =
    joinToString(separator = "\n", transform = transform)
