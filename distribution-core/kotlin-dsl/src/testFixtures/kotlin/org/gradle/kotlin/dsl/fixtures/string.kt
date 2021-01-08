package org.gradle.kotlin.dsl.fixtures


fun <T> Iterable<T>.joinLines(transform: (T) -> String) =
    joinToString(separator = "\n", transform = transform)
