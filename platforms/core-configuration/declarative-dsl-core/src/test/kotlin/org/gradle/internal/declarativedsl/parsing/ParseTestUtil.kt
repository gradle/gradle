package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.prettyPrintLanguageTreeResult
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals



object ParseTestUtil {

    fun parse(@Language("dcl") code: String): LanguageTreeResult {
        val parsedTree = org.gradle.internal.declarativedsl.parsing.parse(code)
        return DefaultLanguageTreeBuilder().build(parsedTree, SourceIdentifier("test"))
    }

    fun parseAsTopLevelBlock(@Language("dcl") code: String): Block {
        val parsedTree = org.gradle.internal.declarativedsl.parsing.parse(code)
        return DefaultLanguageTreeBuilder().build(parsedTree, SourceIdentifier("test")).topLevelBlock
    }

}


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
