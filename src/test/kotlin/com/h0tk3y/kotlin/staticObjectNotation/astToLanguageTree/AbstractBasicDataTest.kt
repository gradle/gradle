package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.example.com.h0tk3y.kotlin.staticObjectNotation.prettyPrintLanguageTree
import org.intellij.lang.annotations.Language
import kotlin.test.assertEquals

abstract class AbstractBasicDataTest {

    abstract fun parse(@Language("kts") code: String): List<ElementResult<*>>

    protected fun assertResult(expected: String, results: List<ElementResult<*>>) {
        assertEquals(
            expected,
            results.joinToString(separator = "\n") {
                val element = it as Element<*>
                prettyPrintLanguageTree(element.element)
            }
        )
    }
}