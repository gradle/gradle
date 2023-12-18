package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.example.com.h0tk3y.kotlin.staticObjectNotation.prettyPrintLanguageTree
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

abstract class AbstractBasicDataTest {

    abstract fun parse(@Language("kts") code: String): List<ElementResult<*>> // TODO: remove

    @Test
    fun `parses literals`() {
        val results = parse(
            """
            a = 1
            b = "test"
            c = ${'"'}""test${'"'}""
            e = true
            d = false
            """.trimIndent()
        )

        val expected = """
                Assignment [indexes: 0..5, file: test] (
                    lhs = PropertyAccess [indexes: 0..1, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 4..5, file: test] (1)
                )
                Assignment [indexes: 6..16, file: test] (
                    lhs = PropertyAccess [indexes: 6..7, file: test] (
                        name = b
                    )
                    rhs = StringLiteral [indexes: 10..16, file: test] (test)
                )
                Assignment [indexes: 17..31, file: test] (
                    lhs = PropertyAccess [indexes: 17..18, file: test] (
                        name = c
                    )
                    rhs = StringLiteral [indexes: 21..31, file: test] (test)
                )
                Assignment [indexes: 32..40, file: test] (
                    lhs = PropertyAccess [indexes: 32..33, file: test] (
                        name = e
                    )
                    rhs = BooleanLiteral [indexes: 36..40, file: test] (true)
                )
                Assignment [indexes: 41..50, file: test] (
                    lhs = PropertyAccess [indexes: 41..42, file: test] (
                        name = d
                    )
                    rhs = BooleanLiteral [indexes: 45..50, file: test] (false)
                )
            """.trimIndent()
        assertResult(expected, results)
    }

    @Test
    fun `parses imports`() {
        val results = parse(
            """
            import a.b.c
            import a.b.MyData
            import MyOtherData
            """.trimIndent()
        )

        val expected = """
                Import [indexes: 0..13, file: test (
                    name parts = [a, b, c]
                )
                Import [indexes: 13..31, file: test (
                    name parts = [a, b, MyData]
                )
                Import [indexes: 31..49, file: test (
                    name parts = [MyOtherData]
                )
            """.trimIndent()
        assertResult(expected, results)
    }

    @Test
    fun `parses function invocations without access chains`() {
        val results = parse(
            """
            f(x = y)
            f(1)
            """.trimIndent()
        )

        val expected = """
                FunctionCall [indexes: 1..8, file: test] (
                    name = f
                    args = [
                        FunctionArgument.Named [indexes: 2..7, file: test] (
                            name = x,
                            expr = PropertyAccess [indexes: 6..7, file: test] (
                                name = y
                            )
                        )
                    ]
                )
                FunctionCall [indexes: 10..13, file: test] (
                    name = f
                    args = [
                        FunctionArgument.Positional [indexes: 11..12, file: test] (
                            expr = IntLiteral [indexes: 11..12, file: test] (1)
                        )
                    ]
                )
            """.trimIndent()
        assertResult(expected, results)
    }

    @Test
    fun `parses function invocation after an access chain`() {
        val results = parse(
            """
            f.g.h.i.j.k(test)
            """.trimIndent())

        val expected = """
            FunctionCall [indexes: 11..17, file: test] (
                name = k
                receiver = PropertyAccess [indexes: 7..9, file: test] (
                    receiver = PropertyAccess [indexes: 5..7, file: test] (
                        receiver = PropertyAccess [indexes: 3..5, file: test] (
                            receiver = PropertyAccess [indexes: 1..3, file: test] (
                                receiver = PropertyAccess [indexes: 0..1, file: test] (
                                    name = f
                                )
                                name = g
                            )
                            name = h
                        )
                        name = i
                    )
                    name = j
                )
                args = [
                    FunctionArgument.Positional [indexes: 12..16, file: test] (
                        expr = PropertyAccess [indexes: 12..16, file: test] (
                            name = test
                        )
                    )
                ]
            )
            """.trimIndent()
        assertResult(expected, results)
    }

    @Test
    fun `parses positional parameters`() {
        val results = parse(
            """
            f(1, x, g())
            """.trimIndent()
        )

        val expected = """
            FunctionCall [indexes: 1..12, file: test] (
                name = f
                args = [
                    FunctionArgument.Positional [indexes: 2..3, file: test] (
                        expr = IntLiteral [indexes: 2..3, file: test] (1)
                    )
                    FunctionArgument.Positional [indexes: 5..6, file: test] (
                        expr = PropertyAccess [indexes: 5..6, file: test] (
                            name = x
                        )
                    )
                    FunctionArgument.Positional [indexes: 8..11, file: test] (
                        expr = FunctionCall [indexes: 9..11, file: test] (
                            name = g
                            args = []
                        )
                    )
                ]
            )
            """.trimIndent()
        assertResult(expected, results)
    }

    @Test
    fun `parses named arguments`() {
        val results = parse(
            """
            f(a = b, c = d)            
            """.trimIndent()
        )

        val expected = """
            FunctionCall [indexes: 1..15, file: test] (
                name = f
                args = [
                    FunctionArgument.Named [indexes: 2..7, file: test] (
                        name = a,
                        expr = PropertyAccess [indexes: 6..7, file: test] (
                            name = b
                        )
                    )
                    FunctionArgument.Named [indexes: 9..14, file: test] (
                        name = c,
                        expr = PropertyAccess [indexes: 13..14, file: test] (
                            name = d
                        )
                    )
                ]
            )
            """.trimIndent()
        assertResult(expected, results)
    }

    @Test
    fun `parses an assignment chain`() {
        val results = parse(
            """
            a.b.c = 1
            """.trimIndent()
        )

        val expected = """
            Assignment [indexes: 0..9, file: test] (
                lhs = PropertyAccess [indexes: 0..5, file: test] (
                    receiver = PropertyAccess [indexes: 1..3, file: test] (
                        receiver = PropertyAccess [indexes: 0..1, file: test] (
                            name = a
                        )
                        name = b
                    )
                    name = c
                )
                rhs = IntLiteral [indexes: 8..9, file: test] (1)
            )
            """.trimIndent()
        assertResult(expected, results)
    }

    @Test
    fun `parses a local val`() {
        val results = parse("val a = 1")

        val expected = """
            LocalValue [indexes: 0..9, file: test] (
                name = a
                rhs = IntLiteral [indexes: 8..9, file: test] (1)
            )
            """.trimIndent()
        assertResult(expected, results)
    }

    @Test
    fun `parses access chain in rhs`() {
        val results = parse("a = b.c.d")

        val expected = """
            Assignment [indexes: 0..9, file: test] (
                lhs = PropertyAccess [indexes: 0..1, file: test] (
                    name = a
                )
                rhs = PropertyAccess [indexes: 7..9, file: test] (
                    receiver = PropertyAccess [indexes: 5..7, file: test] (
                        receiver = PropertyAccess [indexes: 4..5, file: test] (
                            name = b
                        )
                        name = c
                    )
                    name = d
                )
            )
            """.trimIndent()
        assertResult(expected, results)
    }

    @Test
    fun `parses lambdas`() {
        val results = parse(
            """
            a { b = 1 }
            """.trimIndent())

        val expected = """
            FunctionCall [indexes: 2..11, file: test] (
                name = a
                args = [
                    FunctionArgument.Lambda [indexes: 2..11, file: test] (
                        block = Block [indexes: 2..11, file: test] (
                            Assignment [indexes: 4..9, file: test] (
                                lhs = PropertyAccess [indexes: 4..5, file: test] (
                                    name = b
                                )
                                rhs = IntLiteral [indexes: 8..9, file: test] (1)
                            )
                        )
                    )
                ]
            )
            """.trimIndent()
        assertResult(expected, results)
    }

    @Test
    fun `parses call chain`() {
        val results = parse("f(1).g(2).h(3)")

        val expected = """
            FunctionCall [indexes: 11..14, file: test] (
                name = h
                receiver = FunctionCall [indexes: 6..9, file: test] (
                    name = g
                    receiver = FunctionCall [indexes: 1..4, file: test] (
                        name = f
                        args = [
                            FunctionArgument.Positional [indexes: 2..3, file: test] (
                                expr = IntLiteral [indexes: 2..3, file: test] (1)
                            )
                        ]
                    )
                    args = [
                        FunctionArgument.Positional [indexes: 7..8, file: test] (
                            expr = IntLiteral [indexes: 7..8, file: test] (2)
                        )
                    ]
                )
                args = [
                    FunctionArgument.Positional [indexes: 12..13, file: test] (
                        expr = IntLiteral [indexes: 12..13, file: test] (3)
                    )
                ]
            )
            """.trimIndent()
        assertResult(expected, results)
    }

    private fun assertResult(expected: String, results: List<ElementResult<*>>) {
        assertEquals(
            expected,
            results.joinToString(separator = "\n") {
                val element = it as Element<*>
                prettyPrintLanguageTree(element.element)
            }
        )
    }
}