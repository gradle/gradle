package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.example.com.h0tk3y.kotlin.staticObjectNotation.prettyPrintFailingResult
import org.intellij.lang.annotations.Language
import kotlin.test.assertEquals

abstract class AbstractRejectedLanguageFeaturesTest {

    abstract fun parse(@Language("kts") code: String): List<ElementResult<*>>

    protected fun assertResult(expected: String, @Language("kts") code: String) {
        val results = parse(code)
        assertEquals(
            expected,
            results.joinToString(separator = "\n") {
                val failingResult = it as? FailingResult
                failingResult?.let { prettyPrintFailingResult(it) }
                    ?: error("Unhandled result type: ${it.javaClass.simpleName}")
            }
        )
    }
}