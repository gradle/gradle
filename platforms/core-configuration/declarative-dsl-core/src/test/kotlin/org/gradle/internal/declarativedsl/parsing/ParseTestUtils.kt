package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.prettyPrintLanguageTreeResult
import org.intellij.lang.annotations.Language
import kotlin.test.assertEquals


class ParseTestUtil {

    companion object Parser {
        fun parse(@Language("kts") code: String): LanguageTreeResult {
            val parsedTree = org.gradle.internal.declarativedsl.parsing.parse(code)
            return DefaultLanguageTreeBuilder().build(parsedTree, SourceIdentifier("test"))
        }

        fun parseAsTopLevelBlock(code: String): Block {
            val parsedTree = org.gradle.internal.declarativedsl.parsing.parse(code)
            return DefaultLanguageTreeBuilder().build(parsedTree, SourceIdentifier("test")).topLevelBlock
        }
    }
}


fun LanguageTreeResult.assert(
    expectedPrettyPrintedForm: String,
    prettyPrinter: (LanguageTreeResult) -> String = ::prettyPrintLanguageTreeResult
) = assertEquals(expectedPrettyPrintedForm, prettyPrinter(this))
