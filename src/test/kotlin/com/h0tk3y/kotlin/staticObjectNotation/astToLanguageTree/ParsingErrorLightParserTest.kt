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
                potentialElementSource = indexes: 0..1, line/column: 1/1..1/2, file: test, 
                erroneousSource = indexes: 0..1, line/column: 1/1..1/2, file: test
            )
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `unexpected statement separator inside function call`() {
        val code = "id(\"plugin-id-1\";)"

        val expected = """
            ParsingError(
                message = Unparsable value argument: "("plugin-id-1"". Expecting ')', 
                potentialElementSource = indexes: 2..16, line/column: 1/3..1/17, file: test, 
                erroneousSource = indexes: 16..16, line/column: 1/17..1/17, file: test
            )
            ParsingError(
                message = Unparsable expression: ")", 
                potentialElementSource = indexes: 17..18, line/column: 1/18..1/19, file: test, 
                erroneousSource = indexes: 17..18, line/column: 1/18..1/19, file: test
            )
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `missing closing parenthesis in function argument`() {
        val code = "kotlin(\"plugin-id-1) ; kotlin(\"plugin-id-2\")"

        val expected = """
            MultipleFailures(
                MultipleFailures(
                    UnsupportedConstruct(
                        languageFeature = PrefixExpression, 
                        potentialElementSource = indexes: 37..40, line/column: 1/38..1/41, file: test, 
                        erroneousSource = indexes: 37..40, line/column: 1/38..1/41, file: test
                    )
                    UnsupportedConstruct(
                        languageFeature = UnsupportedOperator, 
                        potentialElementSource = indexes: 40..41, line/column: 1/41..1/42, file: test, 
                        erroneousSource = indexes: 40..41, line/column: 1/41..1/42, file: test
                    )
                )
                ParsingError(
                    message = Unparsable value argument: "("plugin-id-1) ; kotlin("plugin-id-2")". Expecting ',', 
                    potentialElementSource = indexes: 6..44, line/column: 1/7..1/45, file: test, 
                    erroneousSource = indexes: 42..42, line/column: 1/43..1/43, file: test
                )
                ParsingError(
                    message = Unparsable value argument: "("plugin-id-1) ; kotlin("plugin-id-2")". Expecting ')', 
                    potentialElementSource = indexes: 6..44, line/column: 1/7..1/45, file: test, 
                    erroneousSource = indexes: 44..44, line/column: 1/45..1/45, file: test
                )
            )""".trimIndent()
        assertResult(expected, code)
    }
}