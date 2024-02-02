package org.gradle.internal.declarativedsl.astToLanguageTree


import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.parsing.assert
import org.junit.jupiter.api.Test


class ErrorParsingTest {

    @Test
    fun `single unparsable expression`() {
        val code = "."

        val expected = """
            ErroneousStatement (
                ParsingError(
                    message = Expecting an element,
                    potentialElementSource = indexes: 0..1, line/column: 1/1..1/2, file: test,
                    erroneousSource = indexes: 0..1, line/column: 1/1..1/2, file: test
                )
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `unexpected statement separator inside function call`() {
        val code = "id(\"plugin-id-1\";)"

        val expected = """
            ErroneousStatement (
                ParsingError(
                    message = Unparsable value argument: "("plugin-id-1"". Expecting ')',
                    potentialElementSource = indexes: 2..16, line/column: 1/3..1/17, file: test,
                    erroneousSource = indexes: 16..16, line/column: 1/17..1/17, file: test
                )
            )
            ErroneousStatement (
                ParsingError(
                    message = Expecting an element,
                    potentialElementSource = indexes: 17..18, line/column: 1/18..1/19, file: test,
                    erroneousSource = indexes: 17..18, line/column: 1/18..1/19, file: test
                )
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `missing closing parenthesis in function argument`() {
        val code = "kotlin(\"plugin-id-1) ; kotlin(\"plugin-id-2\")"

        val expected = """
            ErroneousStatement (
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
                )
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `missing assignment in one of a series of assignments`() {
        val code = """
            val a = 1
            val b = 2
            val c 3
            val d = 4
            val e = 5
        """.trimIndent()

        val expected = """
            LocalValue [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                name = a
                rhs = IntLiteral [indexes: 8..9, line/column: 1/9..1/10, file: test] (1)
            )
            LocalValue [indexes: 10..19, line/column: 2/1..2/10, file: test] (
                name = b
                rhs = IntLiteral [indexes: 18..19, line/column: 2/9..2/10, file: test] (2)
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UninitializedProperty,
                    potentialElementSource = indexes: 20..25, line/column: 3/1..3/6, file: test,
                    erroneousSource = indexes: 20..25, line/column: 3/1..3/6, file: test
                )
            )
            ErroneousStatement (
                ParsingError(
                    message = Unexpected tokens (use ';' to separate expressions on the same line),
                    potentialElementSource = indexes: 26..27, line/column: 3/7..3/8, file: test,
                    erroneousSource = indexes: 26..27, line/column: 3/7..3/8, file: test
                )
            )
            LocalValue [indexes: 28..37, line/column: 4/1..4/10, file: test] (
                name = d
                rhs = IntLiteral [indexes: 36..37, line/column: 4/9..4/10, file: test] (4)
            )
            LocalValue [indexes: 38..47, line/column: 5/1..5/10, file: test] (
                name = e
                rhs = IntLiteral [indexes: 46..47, line/column: 5/9..5/10, file: test] (5)
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `missing parenthesis in one of a series of assignments`() {
        val code = """
            val a = 1
            val b = (2
            val c = 9
        """.trimIndent()

        val expected = """
            LocalValue [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                name = a
                rhs = IntLiteral [indexes: 8..9, line/column: 1/9..1/10, file: test] (1)
            )
            ErroneousStatement (
                ParsingError(
                    message = Expecting ')',
                    potentialElementSource = indexes: 18..20, line/column: 2/9..2/11, file: test,
                    erroneousSource = indexes: 20..20, line/column: 2/11..2/11, file: test
                )
            )
            LocalValue [indexes: 21..30, line/column: 3/1..3/10, file: test] (
                name = c
                rhs = IntLiteral [indexes: 29..30, line/column: 3/9..3/10, file: test] (9)
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `accidentally concatenated lines in a series of assignments`() {
        val code = """
            val a = 1
            val b = 2 val c = 3
            val d = 4
        """.trimIndent()

        val expected = """
            LocalValue [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                name = a
                rhs = IntLiteral [indexes: 8..9, line/column: 1/9..1/10, file: test] (1)
            )
            LocalValue [indexes: 10..19, line/column: 2/1..2/10, file: test] (
                name = b
                rhs = IntLiteral [indexes: 18..19, line/column: 2/9..2/10, file: test] (2)
            )
            ErroneousStatement (
                ParsingError(
                    message = Unexpected tokens (use ';' to separate expressions on the same line),
                    potentialElementSource = indexes: 19..19, line/column: 2/10..2/10, file: test,
                    erroneousSource = indexes: 19..19, line/column: 2/10..2/10, file: test
                )
            )
            LocalValue [indexes: 20..29, line/column: 2/11..2/20, file: test] (
                name = c
                rhs = IntLiteral [indexes: 28..29, line/column: 2/19..2/20, file: test] (3)
            )
            LocalValue [indexes: 30..39, line/column: 3/1..3/10, file: test] (
                name = d
                rhs = IntLiteral [indexes: 38..39, line/column: 3/9..3/10, file: test] (4)
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `internal error in a block`() {
        val code = """
            block {
                val a = 1
                b = 2
                val c 3
                d = 4
            }
        """.trimIndent()

        val expected = """
            FunctionCall [indexes: 0..55, line/column: 1/1..6/2, file: test] (
                name = block
                args = [
                    FunctionArgument.Lambda [indexes: 6..55, line/column: 1/7..6/2, file: test] (
                        block = Block [indexes: 12..53, line/column: 2/5..5/10, file: test] (
                            LocalValue [indexes: 12..21, line/column: 2/5..2/14, file: test] (
                                name = a
                                rhs = IntLiteral [indexes: 20..21, line/column: 2/13..2/14, file: test] (1)
                            )
                            Assignment [indexes: 26..31, line/column: 3/5..3/10, file: test] (
                                lhs = PropertyAccess [indexes: 26..27, line/column: 3/5..3/6, file: test] (
                                    name = b
                                )
                                rhs = IntLiteral [indexes: 30..31, line/column: 3/9..3/10, file: test] (2)
                            )
                            ErroneousStatement (
                                UnsupportedConstruct(
                                    languageFeature = UninitializedProperty,
                                    potentialElementSource = indexes: 36..41, line/column: 4/5..4/10, file: test,
                                    erroneousSource = indexes: 36..41, line/column: 4/5..4/10, file: test
                                )
                            )
                            ErroneousStatement (
                                ParsingError(
                                    message = Unexpected tokens (use ';' to separate expressions on the same line),
                                    potentialElementSource = indexes: 42..43, line/column: 4/11..4/12, file: test,
                                    erroneousSource = indexes: 42..43, line/column: 4/11..4/12, file: test
                                )
                            )
                            Assignment [indexes: 48..53, line/column: 5/5..5/10, file: test] (
                                lhs = PropertyAccess [indexes: 48..49, line/column: 5/5..5/6, file: test] (
                                    name = d
                                )
                                rhs = IntLiteral [indexes: 52..53, line/column: 5/9..5/10, file: test] (4)
                            )
                        )
                    )
                ]
            )""".trimIndent()
        parse(code).assert(expected)
    }

    private
    fun parse(code: String): LanguageTreeResult = ParseTestUtil.parse(code)
}
