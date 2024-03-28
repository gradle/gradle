package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.prettyPrintLanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.intellij.lang.annotations.Language
import kotlin.test.assertEquals


class ParseTestUtil {

    companion object Parser {
        fun parse(@Language("kts") code: String): LanguageTreeResult {
            val (tree, sourceCode, sourceOffset) = org.gradle.internal.declarativedsl.parsing.parse(code)
            return DefaultLanguageTreeBuilder().build(tree, sourceCode, sourceOffset, SourceIdentifier("test"))
        }
    }
}


fun LanguageTreeResult.assert(
    expectedPrettyPrintedForm: String,
    prettyPrinter: (LanguageTreeResult) -> String = ::prettyPrintLanguageTreeResult
) = assertEquals(expectedPrettyPrintedForm, prettyPrinter(this))
