package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.example.com.h0tk3y.kotlin.staticObjectNotation.prettyPrintLanguageTreeResult
import org.intellij.lang.annotations.Language
import kotlin.test.assertEquals

abstract class AbstractBasicDataTest {

    abstract fun parse(@Language("kts") code: String): LanguageTreeResult

    protected fun assertResult(expected: String, results: LanguageTreeResult) {
        assertEquals(
            expected,
            prettyPrintLanguageTreeResult(results)
        )
    }
}
