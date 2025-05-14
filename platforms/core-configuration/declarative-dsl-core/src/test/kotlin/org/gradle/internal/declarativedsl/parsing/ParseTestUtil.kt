package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.prettyPrintLanguageTreeResult
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals

fun LanguageTreeResult.assert(
    expectedPrettyPrintedForm: String,
    prettyPrinter: (LanguageTreeResult) -> String = ::prettyPrintLanguageTreeResult
) = assertEquals(expectedPrettyPrintedForm, prettyPrinter(this))


@Suppress("unused")
fun addCommentsWithIndexes(@Language("kts") code: String): String {
    return buildString {
        val lines = code.lines()
        var index = 0
        lines.forEach { line ->
            val startIndex = index
            index += line.length
            append("($startIndex .. $index): ")
            append(line)
            appendLine()
            index++  // account for the new-line character
        }
    }
}


fun removeCommentAndEmptyLines(code: String): String {
    return buildString {
        val lines = code.lines()
        lines.forEach { line ->
            if (!line.trim().startsWith("//") && line.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(line)
            }
        }
    }
}
