package org.gradle.internal.declarativedsl.astToLanguageTree

import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.parsing.assert
import org.junit.jupiter.api.Test


class BasicParsingTest {

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
                Assignment [indexes: 0..5, line/column: 1/1..1/6, file: test] (
                    lhs = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 4..5, line/column: 1/5..1/6, file: test] (1)
                )
                Assignment [indexes: 6..16, line/column: 2/1..2/11, file: test] (
                    lhs = PropertyAccess [indexes: 6..7, line/column: 2/1..2/2, file: test] (
                        name = b
                    )
                    rhs = StringLiteral [indexes: 10..16, line/column: 2/5..2/11, file: test] (test)
                )
                Assignment [indexes: 17..31, line/column: 3/1..3/15, file: test] (
                    lhs = PropertyAccess [indexes: 17..18, line/column: 3/1..3/2, file: test] (
                        name = c
                    )
                    rhs = StringLiteral [indexes: 21..31, line/column: 3/5..3/15, file: test] (test)
                )
                Assignment [indexes: 32..40, line/column: 4/1..4/9, file: test] (
                    lhs = PropertyAccess [indexes: 32..33, line/column: 4/1..4/2, file: test] (
                        name = e
                    )
                    rhs = BooleanLiteral [indexes: 36..40, line/column: 4/5..4/9, file: test] (true)
                )
                Assignment [indexes: 41..50, line/column: 5/1..5/10, file: test] (
                    lhs = PropertyAccess [indexes: 41..42, line/column: 5/1..5/2, file: test] (
                        name = d
                    )
                    rhs = BooleanLiteral [indexes: 45..50, line/column: 5/5..5/10, file: test] (false)
                )""".trimIndent()
        results.assert(expected)
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
                Import [indexes: 0..12, line/column: 1/1..1/13, file: test (
                    name parts = [a, b, c]
                )
                Import [indexes: 13..30, line/column: 2/1..2/18, file: test (
                    name parts = [a, b, MyData]
                )
                Import [indexes: 31..49, line/column: 3/1..3/19, file: test (
                    name parts = [MyOtherData]
                )""".trimIndent()
        results.assert(expected)
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
                FunctionCall [indexes: 0..8, line/column: 1/1..1/9, file: test] (
                    name = f
                    args = [
                        FunctionArgument.Named [indexes: 2..7, line/column: 1/3..1/8, file: test] (
                            name = x,
                            expr = PropertyAccess [indexes: 6..7, line/column: 1/7..1/8, file: test] (
                                name = y
                            )
                        )
                    ]
                )
                FunctionCall [indexes: 9..13, line/column: 2/1..2/5, file: test] (
                    name = f
                    args = [
                        FunctionArgument.Positional [indexes: 11..12, line/column: 2/3..2/4, file: test] (
                            expr = IntLiteral [indexes: 11..12, line/column: 2/3..2/4, file: test] (1)
                        )
                    ]
                )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses function invocation after an access chain`() {
        val results = parse(
            """
            f.g.h.i.j.k(test)
            """.trimIndent())

        val expected = """
            FunctionCall [indexes: 10..17, line/column: 1/11..1/18, file: test] (
                name = k
                receiver = PropertyAccess [indexes: 8..9, line/column: 1/9..1/10, file: test] (
                    receiver = PropertyAccess [indexes: 6..7, line/column: 1/7..1/8, file: test] (
                        receiver = PropertyAccess [indexes: 4..5, line/column: 1/5..1/6, file: test] (
                            receiver = PropertyAccess [indexes: 2..3, line/column: 1/3..1/4, file: test] (
                                receiver = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
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
                    FunctionArgument.Positional [indexes: 12..16, line/column: 1/13..1/17, file: test] (
                        expr = PropertyAccess [indexes: 12..16, line/column: 1/13..1/17, file: test] (
                            name = test
                        )
                    )
                ]
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses positional parameters`() {
        val results = parse(
            """
            f(1, x, "s", g())
            """.trimIndent()
        )

        val expected = """
            FunctionCall [indexes: 0..17, line/column: 1/1..1/18, file: test] (
                name = f
                args = [
                    FunctionArgument.Positional [indexes: 2..3, line/column: 1/3..1/4, file: test] (
                        expr = IntLiteral [indexes: 2..3, line/column: 1/3..1/4, file: test] (1)
                    )
                    FunctionArgument.Positional [indexes: 5..6, line/column: 1/6..1/7, file: test] (
                        expr = PropertyAccess [indexes: 5..6, line/column: 1/6..1/7, file: test] (
                            name = x
                        )
                    )
                    FunctionArgument.Positional [indexes: 8..11, line/column: 1/9..1/12, file: test] (
                        expr = StringLiteral [indexes: 8..11, line/column: 1/9..1/12, file: test] (s)
                    )
                    FunctionArgument.Positional [indexes: 13..16, line/column: 1/14..1/17, file: test] (
                        expr = FunctionCall [indexes: 13..16, line/column: 1/14..1/17, file: test] (
                            name = g
                            args = []
                        )
                    )
                ]
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses named arguments`() {
        val results = parse(
            """
            f(a = b, c = d)
            """.trimIndent()
        )

        val expected = """
            FunctionCall [indexes: 0..15, line/column: 1/1..1/16, file: test] (
                name = f
                args = [
                    FunctionArgument.Named [indexes: 2..7, line/column: 1/3..1/8, file: test] (
                        name = a,
                        expr = PropertyAccess [indexes: 6..7, line/column: 1/7..1/8, file: test] (
                            name = b
                        )
                    )
                    FunctionArgument.Named [indexes: 9..14, line/column: 1/10..1/15, file: test] (
                        name = c,
                        expr = PropertyAccess [indexes: 13..14, line/column: 1/14..1/15, file: test] (
                            name = d
                        )
                    )
                ]
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses an assignment chain`() {
        val results = parse(
            """
            a.b.c = 1
            """.trimIndent()
        )

        val expected = """
            Assignment [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                lhs = PropertyAccess [indexes: 4..5, line/column: 1/5..1/6, file: test] (
                    receiver = PropertyAccess [indexes: 2..3, line/column: 1/3..1/4, file: test] (
                        receiver = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                            name = a
                        )
                        name = b
                    )
                    name = c
                )
                rhs = IntLiteral [indexes: 8..9, line/column: 1/9..1/10, file: test] (1)
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses assigning 'this' keyword`() {
        val results = parse(
            """
            a = this
            """.trimIndent()
        )

        val expected = """
            Assignment [indexes: 0..8, line/column: 1/1..1/9, file: test] (
                lhs = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = This
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses assigning 'null'`() {
        val results = parse(
            """
            a = null
            """.trimIndent()
        )

        val expected = """
            Assignment [indexes: 0..8, line/column: 1/1..1/9, file: test] (
                lhs = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = Null
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses a local val`() {
        val results = parse("val a = 1")

        val expected = """
            LocalValue [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                name = a
                rhs = IntLiteral [indexes: 8..9, line/column: 1/9..1/10, file: test] (1)
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses access chain in rhs`() {
        val results = parse("a = b.c.d")

        val expected = """
            Assignment [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                lhs = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = PropertyAccess [indexes: 8..9, line/column: 1/9..1/10, file: test] (
                    receiver = PropertyAccess [indexes: 6..7, line/column: 1/7..1/8, file: test] (
                        receiver = PropertyAccess [indexes: 4..5, line/column: 1/5..1/6, file: test] (
                            name = b
                        )
                        name = c
                    )
                    name = d
                )
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses lambdas`() {
        val results = parse(
            """
            a { b = 1 }
            """.trimIndent())

        val expected = """
            FunctionCall [indexes: 0..11, line/column: 1/1..1/12, file: test] (
                name = a
                args = [
                    FunctionArgument.Lambda [indexes: 2..11, line/column: 1/3..1/12, file: test] (
                        block = Block [indexes: 4..9, line/column: 1/5..1/10, file: test] (
                            Assignment [indexes: 4..9, line/column: 1/5..1/10, file: test] (
                                lhs = PropertyAccess [indexes: 4..5, line/column: 1/5..1/6, file: test] (
                                    name = b
                                )
                                rhs = IntLiteral [indexes: 8..9, line/column: 1/9..1/10, file: test] (1)
                            )
                        )
                    )
                ]
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses call chain`() {
        val results = parse("f(1).g(2).h(3)")

        val expected = """
            FunctionCall [indexes: 10..14, line/column: 1/11..1/15, file: test] (
                name = h
                receiver = FunctionCall [indexes: 5..9, line/column: 1/6..1/10, file: test] (
                    name = g
                    receiver = FunctionCall [indexes: 0..4, line/column: 1/1..1/5, file: test] (
                        name = f
                        args = [
                            FunctionArgument.Positional [indexes: 2..3, line/column: 1/3..1/4, file: test] (
                                expr = IntLiteral [indexes: 2..3, line/column: 1/3..1/4, file: test] (1)
                            )
                        ]
                    )
                    args = [
                        FunctionArgument.Positional [indexes: 7..8, line/column: 1/8..1/9, file: test] (
                            expr = IntLiteral [indexes: 7..8, line/column: 1/8..1/9, file: test] (2)
                        )
                    ]
                )
                args = [
                    FunctionArgument.Positional [indexes: 12..13, line/column: 1/13..1/14, file: test] (
                        expr = IntLiteral [indexes: 12..13, line/column: 1/13..1/14, file: test] (3)
                    )
                ]
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses infix call chain`() {
        val results = parse(
            """
            f(1) g "string" h true i 2L j 3.apply(4)
            """.trimIndent()
        )

        val expected = """
            FunctionCall [indexes: 0..40, line/column: 1/1..1/41, file: test] (
                name = j
                receiver = FunctionCall [indexes: 0..27, line/column: 1/1..1/28, file: test] (
                    name = i
                    receiver = FunctionCall [indexes: 0..22, line/column: 1/1..1/23, file: test] (
                        name = h
                        receiver = FunctionCall [indexes: 0..15, line/column: 1/1..1/16, file: test] (
                            name = g
                            receiver = FunctionCall [indexes: 0..4, line/column: 1/1..1/5, file: test] (
                                name = f
                                args = [
                                    FunctionArgument.Positional [indexes: 2..3, line/column: 1/3..1/4, file: test] (
                                        expr = IntLiteral [indexes: 2..3, line/column: 1/3..1/4, file: test] (1)
                                    )
                                ]
                            )
                            args = [
                                FunctionArgument.Positional [indexes: 7..15, line/column: 1/8..1/16, file: test] (
                                    expr = StringLiteral [indexes: 7..15, line/column: 1/8..1/16, file: test] (string)
                                )
                            ]
                        )
                        args = [
                            FunctionArgument.Positional [indexes: 18..22, line/column: 1/19..1/23, file: test] (
                                expr = BooleanLiteral [indexes: 18..22, line/column: 1/19..1/23, file: test] (true)
                            )
                        ]
                    )
                    args = [
                        FunctionArgument.Positional [indexes: 25..27, line/column: 1/26..1/28, file: test] (
                            expr = LongLiteral [indexes: 25..27, line/column: 1/26..1/28, file: test] (2)
                        )
                    ]
                )
                args = [
                    FunctionArgument.Positional [indexes: 30..40, line/column: 1/31..1/41, file: test] (
                        expr = FunctionCall [indexes: 32..40, line/column: 1/33..1/41, file: test] (
                            name = apply
                            receiver = IntLiteral [indexes: 30..31, line/column: 1/31..1/32, file: test] (3)
                            args = [
                                FunctionArgument.Positional [indexes: 38..39, line/column: 1/39..1/40, file: test] (
                                    expr = IntLiteral [indexes: 38..39, line/column: 1/39..1/40, file: test] (4)
                                )
                            ]
                        )
                    )
                ]
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `keeps empty lines in line number counting`() {
        val results = parse(
            """
            import a.b.c

            // start of actual script content is here -- imports are counted separately because of the workarounds

            f(x)


            a = 1
            """.trimIndent()
        )

        val expected = """
            Import [indexes: 0..12, line/column: 1/1..1/13, file: test (
                name parts = [a, b, c]
            )
            FunctionCall [indexes: 104..108, line/column: 3/1..3/5, file: test] (
                name = f
                args = [
                    FunctionArgument.Positional [indexes: 106..107, line/column: 3/3..3/4, file: test] (
                        expr = PropertyAccess [indexes: 106..107, line/column: 3/3..3/4, file: test] (
                            name = x
                        )
                    )
                ]
            )
            Assignment [indexes: 111..116, line/column: 6/1..6/6, file: test] (
                lhs = PropertyAccess [indexes: 111..112, line/column: 6/1..6/2, file: test] (
                    name = a
                )
                rhs = IntLiteral [indexes: 115..116, line/column: 6/5..6/6, file: test] (1)
            )
        """.trimIndent()

        results.assert(expected)
    }

    @Test
    fun `parse infix function call with regular arguments`() {
        val results = parse(
            """
            f("a") g("b")
            """.trimIndent()
        )

        val expected = """
            FunctionCall [indexes: 0..13, line/column: 1/1..1/14, file: test] (
                name = g
                receiver = FunctionCall [indexes: 0..6, line/column: 1/1..1/7, file: test] (
                    name = f
                    args = [
                        FunctionArgument.Positional [indexes: 2..5, line/column: 1/3..1/6, file: test] (
                            expr = StringLiteral [indexes: 2..5, line/column: 1/3..1/6, file: test] (a)
                        )
                    ]
                )
                args = [
                    FunctionArgument.Positional [indexes: 9..12, line/column: 1/10..1/13, file: test] (
                        expr = StringLiteral [indexes: 9..12, line/column: 1/10..1/13, file: test] (b)
                    )
                ]
            )""".trimIndent()
        results.assert(expected)
    }

    private
    fun parse(code: String): LanguageTreeResult = ParseTestUtil.parse(code)
}
