package com.example.com.h0tk3y.kotlin.staticObjectNotation.parsing

import com.example.com.h0tk3y.kotlin.staticObjectNotation.prettyPrintLanguageTreeResult
import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import org.intellij.lang.annotations.Language
import kotlin.test.assertEquals

class ParseTestUtil {

    companion object Parser {
        fun parse(@Language("kts") code: String): LanguageTreeResult {
            val (tree, sourceCode, sourceOffset) = com.h0tk3y.kotlin.staticObjectNotation.parsing.parse(code)
            return DefaultLanguageTreeBuilder().build(tree, sourceCode, sourceOffset, SourceIdentifier("test"))
        }
    }

}

fun LanguageTreeResult.assert(
    expectedPrettyPrintedForm: String,
    prettyPrinter: (LanguageTreeResult) -> String = ::prettyPrintLanguageTreeResult
) = assertEquals(expectedPrettyPrintedForm, prettyPrinter(this))
