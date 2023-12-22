package com.example.com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.AbstractRejectedLanguageFeaturesTest
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.ElementResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.ParseTestUtil
import org.junit.jupiter.api.Test

class ParsingErrorLightParserTest: AbstractRejectedLanguageFeaturesTest() {

    override fun parse(code: String): List<ElementResult<*>> = ParseTestUtil.parseWithLightParser(code)

    @Test
    fun `single unparsable expression`() {
        val code = "."

        val expected = """
            ParsingError(
                message = Unparsable expression: ".", 
                source = indexes: 0..1, line/column: 1/1..1/2, file: test, 
            )
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `unexpected statement separator inside function call`() {
        val code = "id(\"plugin-id-1\";)"

        val expected = """
            ParsingError(
                message = Unparsable value argument: "("plugin-id-1"", 
                source = indexes: 2..16, line/column: 1/3..1/17, file: test, 
            )
            ParsingError(
                message = Unparsable expression: ")", 
                source = indexes: 17..18, line/column: 1/18..1/19, file: test, 
            )
            """.trimIndent()
        assertResult(expected, code)
    }
}