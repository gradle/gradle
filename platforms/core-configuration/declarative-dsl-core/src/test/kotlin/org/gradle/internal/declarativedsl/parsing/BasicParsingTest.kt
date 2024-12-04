package org.gradle.internal.declarativedsl.parsing

import org.junit.Test


class BasicParsingTest {

    @Test
    fun `parses imports`() {
        val results = ParseTestUtil.parse(
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
        val results = ParseTestUtil.parse(
            """
            f(x = y)
            f(1)
            f()
            """.trimIndent()
        )

        val expected = """
                FunctionCall [indexes: 0..8, line/column: 1/1..1/9, file: test] (
                    name = f
                    args = [
                        FunctionArgument.Named [indexes: 2..7, line/column: 1/3..1/8, file: test] (
                            name = x,
                            expr = NamedReference [indexes: 6..7, line/column: 1/7..1/8, file: test] (
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
                )
                FunctionCall [indexes: 14..17, line/column: 3/1..3/4, file: test] (
                    name = f
                    args = []
                )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses function invocation after an access chain`() {
        val results = ParseTestUtil.parse(
            """
            f.g.h.i.j.k(test)
            """.trimIndent())

        val expected = """
            FunctionCall [indexes: 10..17, line/column: 1/11..1/18, file: test] (
                name = k
                receiver = NamedReference [indexes: 8..9, line/column: 1/9..1/10, file: test] (
                    receiver = NamedReference [indexes: 6..7, line/column: 1/7..1/8, file: test] (
                        receiver = NamedReference [indexes: 4..5, line/column: 1/5..1/6, file: test] (
                            receiver = NamedReference [indexes: 2..3, line/column: 1/3..1/4, file: test] (
                                receiver = NamedReference [indexes: 0..1, line/column: 1/1..1/2, file: test] (
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
                        expr = NamedReference [indexes: 12..16, line/column: 1/13..1/17, file: test] (
                            name = test
                        )
                    )
                ]
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses positional parameters`() {
        val results = ParseTestUtil.parse(
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
                        expr = NamedReference [indexes: 5..6, line/column: 1/6..1/7, file: test] (
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
        val results = ParseTestUtil.parse(
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
                        expr = NamedReference [indexes: 6..7, line/column: 1/7..1/8, file: test] (
                            name = b
                        )
                    )
                    FunctionArgument.Named [indexes: 9..14, line/column: 1/10..1/15, file: test] (
                        name = c,
                        expr = NamedReference [indexes: 13..14, line/column: 1/14..1/15, file: test] (
                            name = d
                        )
                    )
                ]
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses an assignment chain`() {
        val results = ParseTestUtil.parse(
            """
            a.b.c = 1
            """.trimIndent()
        )

        val expected = """
            Assignment [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                lhs = NamedReference [indexes: 4..5, line/column: 1/5..1/6, file: test] (
                    receiver = NamedReference [indexes: 2..3, line/column: 1/3..1/4, file: test] (
                        receiver = NamedReference [indexes: 0..1, line/column: 1/1..1/2, file: test] (
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
        val results = ParseTestUtil.parse(
            """
            a = this
            """.trimIndent()
        )

        val expected = """
            Assignment [indexes: 0..8, line/column: 1/1..1/9, file: test] (
                lhs = NamedReference [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = This
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses assigning 'null'`() {
        val results = ParseTestUtil.parse(
            """
            a = null
            """.trimIndent()
        )

        val expected = """
            Assignment [indexes: 0..8, line/column: 1/1..1/9, file: test] (
                lhs = NamedReference [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = Null
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses assigning value factory`() {
        val results = ParseTestUtil.parse(
            """
            a = f(1)
            """.trimIndent()
        )

        val expected = """
            Assignment [indexes: 0..8, line/column: 1/1..1/9, file: test] (
                lhs = NamedReference [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = FunctionCall [indexes: 4..8, line/column: 1/5..1/9, file: test] (
                    name = f
                    args = [
                        FunctionArgument.Positional [indexes: 6..7, line/column: 1/7..1/8, file: test] (
                            expr = IntLiteral [indexes: 6..7, line/column: 1/7..1/8, file: test] (1)
                        )
                    ]
                )
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses assigning function invocation after an access chain`() {
        val results = ParseTestUtil.parse(
            """
            a = f.g.h(7)
            """.trimIndent()
        )

        val expected = """
            Assignment [indexes: 0..12, line/column: 1/1..1/13, file: test] (
                lhs = NamedReference [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = FunctionCall [indexes: 8..12, line/column: 1/9..1/13, file: test] (
                    name = h
                    receiver = NamedReference [indexes: 6..7, line/column: 1/7..1/8, file: test] (
                        receiver = NamedReference [indexes: 4..5, line/column: 1/5..1/6, file: test] (
                            name = f
                        )
                        name = g
                    )
                    args = [
                        FunctionArgument.Positional [indexes: 10..11, line/column: 1/11..1/12, file: test] (
                            expr = IntLiteral [indexes: 10..11, line/column: 1/11..1/12, file: test] (7)
                        )
                    ]
                )
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses a local val`() {
        val results = ParseTestUtil.parse("val a = 1")

        val expected = """
            LocalValue [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                name = a
                rhs = IntLiteral [indexes: 8..9, line/column: 1/9..1/10, file: test] (1)
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses access chain in rhs`() {
        val results = ParseTestUtil.parse("a = b.c.d")

        val expected = """
            Assignment [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                lhs = NamedReference [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = NamedReference [indexes: 8..9, line/column: 1/9..1/10, file: test] (
                    receiver = NamedReference [indexes: 6..7, line/column: 1/7..1/8, file: test] (
                        receiver = NamedReference [indexes: 4..5, line/column: 1/5..1/6, file: test] (
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
        val results = ParseTestUtil.parse(
            """
            a { b = 1 }
            block("param") {
                a = 1
            }
            `backtick block` { a = 1 }
            `backtick block with param` ("param") {
                b = 2
            }
            """.trimIndent())

        val expected = """
            FunctionCall [indexes: 0..11, line/column: 1/1..1/12, file: test] (
                name = a
                args = [
                    FunctionArgument.Lambda [indexes: 2..11, line/column: 1/3..1/12, file: test] (
                        block = Block [indexes: 4..9, line/column: 1/5..1/10, file: test] (
                            Assignment [indexes: 4..9, line/column: 1/5..1/10, file: test] (
                                lhs = NamedReference [indexes: 4..5, line/column: 1/5..1/6, file: test] (
                                    name = b
                                )
                                rhs = IntLiteral [indexes: 8..9, line/column: 1/9..1/10, file: test] (1)
                            )
                        )
                    )
                ]
            )
            FunctionCall [indexes: 12..40, line/column: 2/1..4/2, file: test] (
                name = block
                args = [
                    FunctionArgument.Positional [indexes: 18..25, line/column: 2/7..2/14, file: test] (
                        expr = StringLiteral [indexes: 18..25, line/column: 2/7..2/14, file: test] (param)
                    )
                    FunctionArgument.Lambda [indexes: 27..40, line/column: 2/16..4/2, file: test] (
                        block = Block [indexes: 33..38, line/column: 3/5..3/10, file: test] (
                            Assignment [indexes: 33..38, line/column: 3/5..3/10, file: test] (
                                lhs = NamedReference [indexes: 33..34, line/column: 3/5..3/6, file: test] (
                                    name = a
                                )
                                rhs = IntLiteral [indexes: 37..38, line/column: 3/9..3/10, file: test] (1)
                            )
                        )
                    )
                ]
            )
            FunctionCall [indexes: 41..67, line/column: 5/1..5/27, file: test] (
                name = `backtick block`
                args = [
                    FunctionArgument.Lambda [indexes: 58..67, line/column: 5/18..5/27, file: test] (
                        block = Block [indexes: 60..65, line/column: 5/20..5/25, file: test] (
                            Assignment [indexes: 60..65, line/column: 5/20..5/25, file: test] (
                                lhs = NamedReference [indexes: 60..61, line/column: 5/20..5/21, file: test] (
                                    name = a
                                )
                                rhs = IntLiteral [indexes: 64..65, line/column: 5/24..5/25, file: test] (1)
                            )
                        )
                    )
                ]
            )
            FunctionCall [indexes: 68..119, line/column: 6/1..8/2, file: test] (
                name = `backtick block with param`
                args = [
                    FunctionArgument.Positional [indexes: 97..104, line/column: 6/30..6/37, file: test] (
                        expr = StringLiteral [indexes: 97..104, line/column: 6/30..6/37, file: test] (param)
                    )
                    FunctionArgument.Lambda [indexes: 106..119, line/column: 6/39..8/2, file: test] (
                        block = Block [indexes: 112..117, line/column: 7/5..7/10, file: test] (
                            Assignment [indexes: 112..117, line/column: 7/5..7/10, file: test] (
                                lhs = NamedReference [indexes: 112..113, line/column: 7/5..7/6, file: test] (
                                    name = b
                                )
                                rhs = IntLiteral [indexes: 116..117, line/column: 7/9..7/10, file: test] (2)
                            )
                        )
                    )
                ]
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `parses call chain`() {
        val results = ParseTestUtil.parse("f(1).g(2).h(3)")

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
    fun `keeps empty lines in line number counting`() {
        val results = ParseTestUtil.parse(
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
                        expr = NamedReference [indexes: 106..107, line/column: 3/3..3/4, file: test] (
                            name = x
                        )
                    )
                ]
            )
            Assignment [indexes: 111..116, line/column: 6/1..6/6, file: test] (
                lhs = NamedReference [indexes: 111..112, line/column: 6/1..6/2, file: test] (
                    name = a
                )
                rhs = IntLiteral [indexes: 115..116, line/column: 6/5..6/6, file: test] (1)
            )
        """.trimIndent()

        results.assert(expected)
    }

}
