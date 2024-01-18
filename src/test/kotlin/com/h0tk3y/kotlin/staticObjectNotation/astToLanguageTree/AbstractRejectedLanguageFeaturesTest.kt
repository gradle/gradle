package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.example.com.h0tk3y.kotlin.staticObjectNotation.prettyPrintLanguageTreeResult
import org.intellij.lang.annotations.Language
import kotlin.test.assertEquals

abstract class AbstractRejectedLanguageFeaturesTest {

    abstract fun parse(@Language("kts") code: String): LanguageTreeResult

    protected fun assertResult(
        expected: String,
        @Language("kts") code: String,
        prettyPrintResult: (LanguageTreeResult) -> String = ::prettyPrintLanguageTreeResult
    ) {
        assertEquals(
            expected.lines().joinToString("\n") { it.trimEnd() },
            prettyPrintResult(parse(code)).lines().joinToString("\n") { it.trimEnd() }
        )
    }
}
