package com.example.com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.AbstractRejectedLanguageFeaturesTest
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.ElementResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.ParseTestUtil
import org.antlr.v4.kotlinruntime.misc.ParseCancellationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ParsingErrorAstTest: AbstractRejectedLanguageFeaturesTest() {

    override fun parse(code: String): List<ElementResult<*>> = ParseTestUtil.parseWithAst(code)

    @Test
    fun `single unparsable expression`() {
        val code = "."

        assertThrows<ParseCancellationException> {
            parse(code)
        }
    }

    @Test
    fun `unexpected statement separator inside function call`() {
        val code = "id(\"plugin-id-1\";)"

        assertThrows<ParseCancellationException> {
            parse(code)
        }
    }

}