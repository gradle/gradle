package org.gradle.internal.declarativedsl.parsing


import org.junit.Test


class ErrorParsingTest {

    @Test
    fun `constructs we don't support, but our parsing does not fail on`() { // TODO: these should become later stage failures at some point
        val code = """5
            a = (1)
            a = f(1).g(2)
            a = "foo".bar(1)
            a.b.c = 1
            d = a.b.c
            a.b {d = 1}
            a.b.c {d = 1}
            f(block{})
            f(1).x
            x.f(1).y
            f(1).x.f(2)
        """.trimIndent()

        val expected = """
            IntLiteral [indexes: 0..1, line/column: 1/1..1/2, file: test] (5)
            Assignment [indexes: 14..21, line/column: 2/13..2/20, file: test] (
                lhs = NamedReference [indexes: 14..15, line/column: 2/13..2/14, file: test] (
                    name = a
                )
                rhs = IntLiteral [indexes: 19..20, line/column: 2/18..2/19, file: test] (1)
            )
            Assignment [indexes: 34..47, line/column: 3/13..3/26, file: test] (
                lhs = NamedReference [indexes: 34..35, line/column: 3/13..3/14, file: test] (
                    name = a
                )
                rhs = FunctionCall [indexes: 43..47, line/column: 3/22..3/26, file: test] (
                    name = g
                    receiver = FunctionCall [indexes: 38..42, line/column: 3/17..3/21, file: test] (
                        name = f
                        args = [
                            FunctionArgument.Positional [indexes: 40..41, line/column: 3/19..3/20, file: test] (
                                expr = IntLiteral [indexes: 40..41, line/column: 3/19..3/20, file: test] (1)
                            )
                        ]
                    )
                    args = [
                        FunctionArgument.Positional [indexes: 45..46, line/column: 3/24..3/25, file: test] (
                            expr = IntLiteral [indexes: 45..46, line/column: 3/24..3/25, file: test] (2)
                        )
                    ]
                )
            )
            Assignment [indexes: 60..76, line/column: 4/13..4/29, file: test] (
                lhs = NamedReference [indexes: 60..61, line/column: 4/13..4/14, file: test] (
                    name = a
                )
                rhs = FunctionCall [indexes: 70..76, line/column: 4/23..4/29, file: test] (
                    name = bar
                    receiver = StringLiteral [indexes: 64..69, line/column: 4/17..4/22, file: test] (foo)
                    args = [
                        FunctionArgument.Positional [indexes: 74..75, line/column: 4/27..4/28, file: test] (
                            expr = IntLiteral [indexes: 74..75, line/column: 4/27..4/28, file: test] (1)
                        )
                    ]
                )
            )
            Assignment [indexes: 89..98, line/column: 5/13..5/22, file: test] (
                lhs = NamedReference [indexes: 93..94, line/column: 5/17..5/18, file: test] (
                    receiver = NamedReference [indexes: 91..92, line/column: 5/15..5/16, file: test] (
                        receiver = NamedReference [indexes: 89..90, line/column: 5/13..5/14, file: test] (
                            name = a
                        )
                        name = b
                    )
                    name = c
                )
                rhs = IntLiteral [indexes: 97..98, line/column: 5/21..5/22, file: test] (1)
            )
            Assignment [indexes: 111..120, line/column: 6/13..6/22, file: test] (
                lhs = NamedReference [indexes: 111..112, line/column: 6/13..6/14, file: test] (
                    name = d
                )
                rhs = NamedReference [indexes: 119..120, line/column: 6/21..6/22, file: test] (
                    receiver = NamedReference [indexes: 117..118, line/column: 6/19..6/20, file: test] (
                        receiver = NamedReference [indexes: 115..116, line/column: 6/17..6/18, file: test] (
                            name = a
                        )
                        name = b
                    )
                    name = c
                )
            )
            FunctionCall [indexes: 135..144, line/column: 7/15..7/24, file: test] (
                name = b
                receiver = NamedReference [indexes: 133..134, line/column: 7/13..7/14, file: test] (
                    name = a
                )
                args = [
                    FunctionArgument.Lambda [indexes: 137..144, line/column: 7/17..7/24, file: test] (
                        block = Block [indexes: 138..143, line/column: 7/18..7/23, file: test] (
                            Assignment [indexes: 138..143, line/column: 7/18..7/23, file: test] (
                                lhs = NamedReference [indexes: 138..139, line/column: 7/18..7/19, file: test] (
                                    name = d
                                )
                                rhs = IntLiteral [indexes: 142..143, line/column: 7/22..7/23, file: test] (1)
                            )
                        )
                    )
                ]
            )
            FunctionCall [indexes: 161..170, line/column: 8/17..8/26, file: test] (
                name = c
                receiver = NamedReference [indexes: 159..160, line/column: 8/15..8/16, file: test] (
                    receiver = NamedReference [indexes: 157..158, line/column: 8/13..8/14, file: test] (
                        name = a
                    )
                    name = b
                )
                args = [
                    FunctionArgument.Lambda [indexes: 163..170, line/column: 8/19..8/26, file: test] (
                        block = Block [indexes: 164..169, line/column: 8/20..8/25, file: test] (
                            Assignment [indexes: 164..169, line/column: 8/20..8/25, file: test] (
                                lhs = NamedReference [indexes: 164..165, line/column: 8/20..8/21, file: test] (
                                    name = d
                                )
                                rhs = IntLiteral [indexes: 168..169, line/column: 8/24..8/25, file: test] (1)
                            )
                        )
                    )
                ]
            )
            FunctionCall [indexes: 183..193, line/column: 9/13..9/23, file: test] (
                name = f
                args = [
                    FunctionArgument.Positional [indexes: 185..192, line/column: 9/15..9/22, file: test] (
                        expr = FunctionCall [indexes: 185..192, line/column: 9/15..9/22, file: test] (
                            name = block
                            args = [
                                FunctionArgument.Lambda [indexes: 190..192, line/column: 9/20..9/22, file: test] (
                                    block = Block [indexes: 191..191, line/column: 9/21..9/21, file: test] (
                                    )
                                )
                            ]
                        )
                    )
                ]
            )
            NamedReference [indexes: 211..212, line/column: 10/18..10/19, file: test] (
                receiver = FunctionCall [indexes: 206..210, line/column: 10/13..10/17, file: test] (
                    name = f
                    args = [
                        FunctionArgument.Positional [indexes: 208..209, line/column: 10/15..10/16, file: test] (
                            expr = IntLiteral [indexes: 208..209, line/column: 10/15..10/16, file: test] (1)
                        )
                    ]
                )
                name = x
            )
            NamedReference [indexes: 232..233, line/column: 11/20..11/21, file: test] (
                receiver = FunctionCall [indexes: 227..231, line/column: 11/15..11/19, file: test] (
                    name = f
                    receiver = NamedReference [indexes: 225..226, line/column: 11/13..11/14, file: test] (
                        name = x
                    )
                    args = [
                        FunctionArgument.Positional [indexes: 229..230, line/column: 11/17..11/18, file: test] (
                            expr = IntLiteral [indexes: 229..230, line/column: 11/17..11/18, file: test] (1)
                        )
                    ]
                )
                name = y
            )
            FunctionCall [indexes: 253..257, line/column: 12/20..12/24, file: test] (
                name = f
                receiver = NamedReference [indexes: 251..252, line/column: 12/18..12/19, file: test] (
                    receiver = FunctionCall [indexes: 246..250, line/column: 12/13..12/17, file: test] (
                        name = f
                        args = [
                            FunctionArgument.Positional [indexes: 248..249, line/column: 12/15..12/16, file: test] (
                                expr = IntLiteral [indexes: 248..249, line/column: 12/15..12/16, file: test] (1)
                            )
                        ]
                    )
                    name = x
                )
                args = [
                    FunctionArgument.Positional [indexes: 255..256, line/column: 12/22..12/23, file: test] (
                        expr = IntLiteral [indexes: 255..256, line/column: 12/22..12/23, file: test] (2)
                    )
                ]
            )""".trimIndent()
        ParseTestUtil.parse(code).assert(expected)
    }

    @Test
    fun `reserved keywords are categorized as errors by the lexer`() {
        val keyword = "in" // hard keyword in Kotlin
        val code = """
            a.$keyword.b(7);
            $keyword = 1;
        """.trimIndent()

        val expected = """
            ErroneousStatement (
                ParsingError(
                    message = Expecting an element,
                    potentialElementSource = indexes: 0..4, line/column: 1/1..1/5, file: test,
                    erroneousSource = indexes: 2..4, line/column: 1/3..1/5, file: test
                )
            )
            ErroneousStatement (
                ParsingError(
                    message = Expecting a statement,
                    potentialElementSource = indexes: 11..13, line/column: 2/1..2/3, file: test,
                    erroneousSource = indexes: 11..13, line/column: 2/1..2/3, file: test
                )
            )
            ErroneousStatement (
                ParsingError(
                    message = Unexpected tokens (use ';' to separate expressions on the same line),
                    potentialElementSource = indexes: 14..17, line/column: 2/4..2/7, file: test,
                    erroneousSource = indexes: 14..17, line/column: 2/4..2/7, file: test
                )
            )
            ErroneousStatement (
                ParsingError(
                    message = Expecting an element,
                    potentialElementSource = indexes: 17..18, line/column: 2/7..2/8, file: test,
                    erroneousSource = indexes: 17..18, line/column: 2/7..2/8, file: test
                )
            )""".trimIndent()
        ParseTestUtil.parse(code).assert(expected)
    }

    @Test
    fun `illegal simple identifier`() {
        val code = "_=1"

        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedSimpleIdentifier,
                    potentialElementSource = indexes: 0..1, line/column: 1/1..1/2, file: test,
                    erroneousSource = indexes: 0..1, line/column: 1/1..1/2, file: test
                )
            )""".trimIndent()
        ParseTestUtil.parse(code).assert(expected)
    }

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
        ParseTestUtil.parse(code).assert(expected)
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
        ParseTestUtil.parse(code).assert(expected)
    }

    @Test
    fun `missing closing parenthesis in function argument`() {
        val code = "kotlin(\"plugin-id-1) ; kotlin(\"plugin-id-2\")"

        val expected = """
            ErroneousStatement (
                MultipleFailures(
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
        ParseTestUtil.parse(code).assert(expected)
    }

    @Test
    fun `missing assignment in one of a series of assignments`() {
        val code = """
            a = 1
            b = 2
            c 3
            d = 4
            e = 5
        """.trimIndent()

        val expected = """
            Assignment [indexes: 0..5, line/column: 1/1..1/6, file: test] (
                lhs = NamedReference [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = IntLiteral [indexes: 4..5, line/column: 1/5..1/6, file: test] (1)
            )
            Assignment [indexes: 6..11, line/column: 2/1..2/6, file: test] (
                lhs = NamedReference [indexes: 6..7, line/column: 2/1..2/2, file: test] (
                    name = b
                )
                rhs = IntLiteral [indexes: 10..11, line/column: 2/5..2/6, file: test] (2)
            )
            NamedReference [indexes: 12..13, line/column: 3/1..3/2, file: test] (
                name = c
            )
            ErroneousStatement (
                ParsingError(
                    message = Unexpected tokens (use ';' to separate expressions on the same line),
                    potentialElementSource = indexes: 14..15, line/column: 3/3..3/4, file: test,
                    erroneousSource = indexes: 14..15, line/column: 3/3..3/4, file: test
                )
            )
            Assignment [indexes: 16..21, line/column: 4/1..4/6, file: test] (
                lhs = NamedReference [indexes: 16..17, line/column: 4/1..4/2, file: test] (
                    name = d
                )
                rhs = IntLiteral [indexes: 20..21, line/column: 4/5..4/6, file: test] (4)
            )
            Assignment [indexes: 22..27, line/column: 5/1..5/6, file: test] (
                lhs = NamedReference [indexes: 22..23, line/column: 5/1..5/2, file: test] (
                    name = e
                )
                rhs = IntLiteral [indexes: 26..27, line/column: 5/5..5/6, file: test] (5)
            )""".trimIndent()
        ParseTestUtil.parse(code).assert(expected)
    }

    @Test
    fun `infix function calls`() {
        val code = """
            id("plugin") version "7.0"
            id("plugin") version("8.0")
            f(1) g "string" h true i 2L j 3.apply(4)
            f("a") g("b")
        """.trimIndent()

        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = InfixFunctionCall,
                    potentialElementSource = indexes: 0..26, line/column: 1/1..1/27, file: test,
                    erroneousSource = indexes: 13..20, line/column: 1/14..1/21, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = InfixFunctionCall,
                    potentialElementSource = indexes: 27..54, line/column: 2/1..2/28, file: test,
                    erroneousSource = indexes: 40..47, line/column: 2/14..2/21, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = InfixFunctionCall,
                    potentialElementSource = indexes: 55..95, line/column: 3/1..3/41, file: test,
                    erroneousSource = indexes: 83..84, line/column: 3/29..3/30, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = InfixFunctionCall,
                    potentialElementSource = indexes: 96..109, line/column: 4/1..4/14, file: test,
                    erroneousSource = indexes: 103..104, line/column: 4/8..4/9, file: test
                )
            )""".trimIndent()
        ParseTestUtil.parse(code).assert(expected)
    }

    @Test
    fun `missing parenthesis in one of a series of assignments`() {
        val code = """
            a = 1
            b = (2
            c = 9
            d = 10
        """.trimIndent()

        val expected = """
            Assignment [indexes: 0..5, line/column: 1/1..1/6, file: test] (
                lhs = NamedReference [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = IntLiteral [indexes: 4..5, line/column: 1/5..1/6, file: test] (1)
            )
            ErroneousStatement (
                ParsingError(
                    message = Expecting ')',
                    potentialElementSource = indexes: 10..16, line/column: 2/5..3/4, file: test,
                    erroneousSource = indexes: 16..16, line/column: 3/4..3/4, file: test
                )
            )
            ErroneousStatement (
                ParsingError(
                    message = Unexpected tokens (use ';' to separate expressions on the same line),
                    potentialElementSource = indexes: 17..18, line/column: 3/5..3/6, file: test,
                    erroneousSource = indexes: 17..18, line/column: 3/5..3/6, file: test
                )
            )
            Assignment [indexes: 19..25, line/column: 4/1..4/7, file: test] (
                lhs = NamedReference [indexes: 19..20, line/column: 4/1..4/2, file: test] (
                    name = d
                )
                rhs = IntLiteral [indexes: 23..25, line/column: 4/5..4/7, file: test] (10)
            )""".trimIndent()
        ParseTestUtil.parse(code).assert(expected)
    }

    @Test
    fun `accidentally concatenated lines in a series of assignments`() {
        val code = """
            a = 1
            b = 2 c = 3
            d = 4
        """.trimIndent()

        val expected = """
            Assignment [indexes: 0..5, line/column: 1/1..1/6, file: test] (
                lhs = NamedReference [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                    name = a
                )
                rhs = IntLiteral [indexes: 4..5, line/column: 1/5..1/6, file: test] (1)
            )
            ErroneousStatement (
                ParsingError(
                    message = Expecting an element,
                    potentialElementSource = indexes: 10..15, line/column: 2/5..2/10, file: test,
                    erroneousSource = indexes: 14..15, line/column: 2/9..2/10, file: test
                )
            )
            ErroneousStatement (
                ParsingError(
                    message = Unexpected tokens (use ';' to separate expressions on the same line),
                    potentialElementSource = indexes: 16..17, line/column: 2/11..2/12, file: test,
                    erroneousSource = indexes: 16..17, line/column: 2/11..2/12, file: test
                )
            )
            Assignment [indexes: 18..23, line/column: 3/1..3/6, file: test] (
                lhs = NamedReference [indexes: 18..19, line/column: 3/1..3/2, file: test] (
                    name = d
                )
                rhs = IntLiteral [indexes: 22..23, line/column: 3/5..3/6, file: test] (4)
            )""".trimIndent()
        ParseTestUtil.parse(code).assert(expected)
    }

    @Test
    fun `internal error in a block`() {
        val code = """
            block {
                a = 1
                b = 2
                c 3
                d = 4
                e = 5
            }
        """.trimIndent()

        val expected = """
            FunctionCall [indexes: 0..57, line/column: 1/1..7/2, file: test] (
                name = block
                args = [
                    FunctionArgument.Lambda [indexes: 6..57, line/column: 1/7..7/2, file: test] (
                        block = Block [indexes: 12..55, line/column: 2/5..6/10, file: test] (
                            Assignment [indexes: 12..17, line/column: 2/5..2/10, file: test] (
                                lhs = NamedReference [indexes: 12..13, line/column: 2/5..2/6, file: test] (
                                    name = a
                                )
                                rhs = IntLiteral [indexes: 16..17, line/column: 2/9..2/10, file: test] (1)
                            )
                            Assignment [indexes: 22..27, line/column: 3/5..3/10, file: test] (
                                lhs = NamedReference [indexes: 22..23, line/column: 3/5..3/6, file: test] (
                                    name = b
                                )
                                rhs = IntLiteral [indexes: 26..27, line/column: 3/9..3/10, file: test] (2)
                            )
                            NamedReference [indexes: 32..33, line/column: 4/5..4/6, file: test] (
                                name = c
                            )
                            ErroneousStatement (
                                ParsingError(
                                    message = Unexpected tokens (use ';' to separate expressions on the same line),
                                    potentialElementSource = indexes: 34..35, line/column: 4/7..4/8, file: test,
                                    erroneousSource = indexes: 34..35, line/column: 4/7..4/8, file: test
                                )
                            )
                            Assignment [indexes: 40..45, line/column: 5/5..5/10, file: test] (
                                lhs = NamedReference [indexes: 40..41, line/column: 5/5..5/6, file: test] (
                                    name = d
                                )
                                rhs = IntLiteral [indexes: 44..45, line/column: 5/9..5/10, file: test] (4)
                            )
                            Assignment [indexes: 50..55, line/column: 6/5..6/10, file: test] (
                                lhs = NamedReference [indexes: 50..51, line/column: 6/5..6/6, file: test] (
                                    name = e
                                )
                                rhs = IntLiteral [indexes: 54..55, line/column: 6/9..6/10, file: test] (5)
                            )
                        )
                    )
                ]
            )""".trimIndent()
        ParseTestUtil.parse(code).assert(expected)
    }

    @Test
    fun `'this@' unsupported`() {
        val results = ParseTestUtil.parse(
            """
            a = this@kaka
            """.trimIndent()
        )

        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = ThisWithLabelQualifier,
                    potentialElementSource = indexes: 4..13, line/column: 1/5..1/14, file: test,
                    erroneousSource = indexes: 4..13, line/column: 1/5..1/14, file: test
                )
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    //TODO: we might want to support backtick identifiers at parsing level, but not in our official grammar,
    // so they should be rejected at some higher level
    fun `backtick identifiers`() {
        val results = ParseTestUtil.parse(
            """
            `some content with spaces`()
            `more conent with spaces` = 1
            """.trimIndent()
        )

        val expected = """
            FunctionCall [indexes: 0..28, line/column: 1/1..1/29, file: test] (
                name = `some content with spaces`
                args = []
            )
            Assignment [indexes: 29..58, line/column: 2/1..2/30, file: test] (
                lhs = NamedReference [indexes: 29..54, line/column: 2/1..2/26, file: test] (
                    name = `more conent with spaces`
                )
                rhs = IntLiteral [indexes: 57..58, line/column: 2/29..2/30, file: test] (1)
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `literals as function names`() {
        val results = ParseTestUtil.parse(
            """
            "function"(1)
            1(2)
            true(3)
            null(4)
            """.trimIndent()
        )

        val expected = """
            ErroneousStatement (
                MultipleFailures(
                    ParsingError(
                        message = Parsing failure, unexpected token type in call expression: STRING_TEMPLATE,
                        potentialElementSource = indexes: 0..10, line/column: 1/1..1/11, file: test,
                        erroneousSource = indexes: 0..10, line/column: 1/1..1/11, file: test
                    )
                    ParsingError(
                        message = Name missing from function call!,
                        potentialElementSource = indexes: 0..13, line/column: 1/1..1/14, file: test,
                        erroneousSource = indexes: 0..13, line/column: 1/1..1/14, file: test
                    )
                )
            )
            ErroneousStatement (
                MultipleFailures(
                    ParsingError(
                        message = Parsing failure, unexpected token type in call expression: INTEGER_CONSTANT,
                        potentialElementSource = indexes: 14..15, line/column: 2/1..2/2, file: test,
                        erroneousSource = indexes: 14..15, line/column: 2/1..2/2, file: test
                    )
                    ParsingError(
                        message = Name missing from function call!,
                        potentialElementSource = indexes: 14..18, line/column: 2/1..2/5, file: test,
                        erroneousSource = indexes: 14..18, line/column: 2/1..2/5, file: test
                    )
                )
            )
            ErroneousStatement (
                MultipleFailures(
                    ParsingError(
                        message = Parsing failure, unexpected token type in call expression: BOOLEAN_CONSTANT,
                        potentialElementSource = indexes: 19..23, line/column: 3/1..3/5, file: test,
                        erroneousSource = indexes: 19..23, line/column: 3/1..3/5, file: test
                    )
                    ParsingError(
                        message = Name missing from function call!,
                        potentialElementSource = indexes: 19..26, line/column: 3/1..3/8, file: test,
                        erroneousSource = indexes: 19..26, line/column: 3/1..3/8, file: test
                    )
                )
            )
            ErroneousStatement (
                MultipleFailures(
                    ParsingError(
                        message = Parsing failure, unexpected token type in call expression: NULL,
                        potentialElementSource = indexes: 27..31, line/column: 4/1..4/5, file: test,
                        erroneousSource = indexes: 27..31, line/column: 4/1..4/5, file: test
                    )
                    ParsingError(
                        message = Name missing from function call!,
                        potentialElementSource = indexes: 27..34, line/column: 4/1..4/8, file: test,
                        erroneousSource = indexes: 27..34, line/column: 4/1..4/8, file: test
                    )
                )
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `reserved keywords`() {
        val results = ParseTestUtil.parse(
            """
            abstract = 1
            annotation = 1
            by = 1
            catch = 1
            companion = 1
            constructor = 1
            crossinline = 1
            data = 1
            dynamic = 1
            enum = 1
            external = 1
            final = 1
            finally = 1
            get = 1
            import = 1
            infix = 1
            init = 1
            inline = 1
            inner = 1
            internal = 1
            lateinit = 1
            noinline = 1
            open = 1
            operator = 1
            out = 1
            override = 1
            private = 1
            protected = 1
            public = 1
            reified = 1
            sealed = 1
            tailrec = 1
            set = 1
            vararg = 1
            where = 1
            field = 1
            property = 1
            receiver = 1
            param = 1
            setparam = 1
            delegate = 1
            file = 1
            expect = 1
            actual = 1
            const = 1
            suspend = 1
            value = 1
            """.trimIndent()
        )

        val expected = """
            Assignment [indexes: 0..12, line/column: 1/1..1/13, file: test] (
                lhs = NamedReference [indexes: 0..8, line/column: 1/1..1/9, file: test] (
                    name = abstract
                )
                rhs = IntLiteral [indexes: 11..12, line/column: 1/12..1/13, file: test] (1)
            )
            Assignment [indexes: 13..27, line/column: 2/1..2/15, file: test] (
                lhs = NamedReference [indexes: 13..23, line/column: 2/1..2/11, file: test] (
                    name = annotation
                )
                rhs = IntLiteral [indexes: 26..27, line/column: 2/14..2/15, file: test] (1)
            )
            Assignment [indexes: 28..34, line/column: 3/1..3/7, file: test] (
                lhs = NamedReference [indexes: 28..30, line/column: 3/1..3/3, file: test] (
                    name = by
                )
                rhs = IntLiteral [indexes: 33..34, line/column: 3/6..3/7, file: test] (1)
            )
            Assignment [indexes: 35..44, line/column: 4/1..4/10, file: test] (
                lhs = NamedReference [indexes: 35..40, line/column: 4/1..4/6, file: test] (
                    name = catch
                )
                rhs = IntLiteral [indexes: 43..44, line/column: 4/9..4/10, file: test] (1)
            )
            Assignment [indexes: 45..58, line/column: 5/1..5/14, file: test] (
                lhs = NamedReference [indexes: 45..54, line/column: 5/1..5/10, file: test] (
                    name = companion
                )
                rhs = IntLiteral [indexes: 57..58, line/column: 5/13..5/14, file: test] (1)
            )
            Assignment [indexes: 59..74, line/column: 6/1..6/16, file: test] (
                lhs = NamedReference [indexes: 59..70, line/column: 6/1..6/12, file: test] (
                    name = constructor
                )
                rhs = IntLiteral [indexes: 73..74, line/column: 6/15..6/16, file: test] (1)
            )
            Assignment [indexes: 75..90, line/column: 7/1..7/16, file: test] (
                lhs = NamedReference [indexes: 75..86, line/column: 7/1..7/12, file: test] (
                    name = crossinline
                )
                rhs = IntLiteral [indexes: 89..90, line/column: 7/15..7/16, file: test] (1)
            )
            Assignment [indexes: 91..99, line/column: 8/1..8/9, file: test] (
                lhs = NamedReference [indexes: 91..95, line/column: 8/1..8/5, file: test] (
                    name = data
                )
                rhs = IntLiteral [indexes: 98..99, line/column: 8/8..8/9, file: test] (1)
            )
            Assignment [indexes: 100..111, line/column: 9/1..9/12, file: test] (
                lhs = NamedReference [indexes: 100..107, line/column: 9/1..9/8, file: test] (
                    name = dynamic
                )
                rhs = IntLiteral [indexes: 110..111, line/column: 9/11..9/12, file: test] (1)
            )
            Assignment [indexes: 112..120, line/column: 10/1..10/9, file: test] (
                lhs = NamedReference [indexes: 112..116, line/column: 10/1..10/5, file: test] (
                    name = enum
                )
                rhs = IntLiteral [indexes: 119..120, line/column: 10/8..10/9, file: test] (1)
            )
            Assignment [indexes: 121..133, line/column: 11/1..11/13, file: test] (
                lhs = NamedReference [indexes: 121..129, line/column: 11/1..11/9, file: test] (
                    name = external
                )
                rhs = IntLiteral [indexes: 132..133, line/column: 11/12..11/13, file: test] (1)
            )
            Assignment [indexes: 134..143, line/column: 12/1..12/10, file: test] (
                lhs = NamedReference [indexes: 134..139, line/column: 12/1..12/6, file: test] (
                    name = final
                )
                rhs = IntLiteral [indexes: 142..143, line/column: 12/9..12/10, file: test] (1)
            )
            Assignment [indexes: 144..155, line/column: 13/1..13/12, file: test] (
                lhs = NamedReference [indexes: 144..151, line/column: 13/1..13/8, file: test] (
                    name = finally
                )
                rhs = IntLiteral [indexes: 154..155, line/column: 13/11..13/12, file: test] (1)
            )
            Assignment [indexes: 156..163, line/column: 14/1..14/8, file: test] (
                lhs = NamedReference [indexes: 156..159, line/column: 14/1..14/4, file: test] (
                    name = get
                )
                rhs = IntLiteral [indexes: 162..163, line/column: 14/7..14/8, file: test] (1)
            )
            Assignment [indexes: 164..173, line/column: 15/1..15/10, file: test] (
                lhs = NamedReference [indexes: 164..169, line/column: 15/1..15/6, file: test] (
                    name = infix
                )
                rhs = IntLiteral [indexes: 172..173, line/column: 15/9..15/10, file: test] (1)
            )
            Assignment [indexes: 174..182, line/column: 16/1..16/9, file: test] (
                lhs = NamedReference [indexes: 174..178, line/column: 16/1..16/5, file: test] (
                    name = init
                )
                rhs = IntLiteral [indexes: 181..182, line/column: 16/8..16/9, file: test] (1)
            )
            Assignment [indexes: 183..193, line/column: 17/1..17/11, file: test] (
                lhs = NamedReference [indexes: 183..189, line/column: 17/1..17/7, file: test] (
                    name = inline
                )
                rhs = IntLiteral [indexes: 192..193, line/column: 17/10..17/11, file: test] (1)
            )
            Assignment [indexes: 194..203, line/column: 18/1..18/10, file: test] (
                lhs = NamedReference [indexes: 194..199, line/column: 18/1..18/6, file: test] (
                    name = inner
                )
                rhs = IntLiteral [indexes: 202..203, line/column: 18/9..18/10, file: test] (1)
            )
            Assignment [indexes: 204..216, line/column: 19/1..19/13, file: test] (
                lhs = NamedReference [indexes: 204..212, line/column: 19/1..19/9, file: test] (
                    name = internal
                )
                rhs = IntLiteral [indexes: 215..216, line/column: 19/12..19/13, file: test] (1)
            )
            Assignment [indexes: 217..229, line/column: 20/1..20/13, file: test] (
                lhs = NamedReference [indexes: 217..225, line/column: 20/1..20/9, file: test] (
                    name = lateinit
                )
                rhs = IntLiteral [indexes: 228..229, line/column: 20/12..20/13, file: test] (1)
            )
            Assignment [indexes: 230..242, line/column: 21/1..21/13, file: test] (
                lhs = NamedReference [indexes: 230..238, line/column: 21/1..21/9, file: test] (
                    name = noinline
                )
                rhs = IntLiteral [indexes: 241..242, line/column: 21/12..21/13, file: test] (1)
            )
            Assignment [indexes: 243..251, line/column: 22/1..22/9, file: test] (
                lhs = NamedReference [indexes: 243..247, line/column: 22/1..22/5, file: test] (
                    name = open
                )
                rhs = IntLiteral [indexes: 250..251, line/column: 22/8..22/9, file: test] (1)
            )
            Assignment [indexes: 252..264, line/column: 23/1..23/13, file: test] (
                lhs = NamedReference [indexes: 252..260, line/column: 23/1..23/9, file: test] (
                    name = operator
                )
                rhs = IntLiteral [indexes: 263..264, line/column: 23/12..23/13, file: test] (1)
            )
            Assignment [indexes: 265..272, line/column: 24/1..24/8, file: test] (
                lhs = NamedReference [indexes: 265..268, line/column: 24/1..24/4, file: test] (
                    name = out
                )
                rhs = IntLiteral [indexes: 271..272, line/column: 24/7..24/8, file: test] (1)
            )
            Assignment [indexes: 273..285, line/column: 25/1..25/13, file: test] (
                lhs = NamedReference [indexes: 273..281, line/column: 25/1..25/9, file: test] (
                    name = override
                )
                rhs = IntLiteral [indexes: 284..285, line/column: 25/12..25/13, file: test] (1)
            )
            Assignment [indexes: 286..297, line/column: 26/1..26/12, file: test] (
                lhs = NamedReference [indexes: 286..293, line/column: 26/1..26/8, file: test] (
                    name = private
                )
                rhs = IntLiteral [indexes: 296..297, line/column: 26/11..26/12, file: test] (1)
            )
            Assignment [indexes: 298..311, line/column: 27/1..27/14, file: test] (
                lhs = NamedReference [indexes: 298..307, line/column: 27/1..27/10, file: test] (
                    name = protected
                )
                rhs = IntLiteral [indexes: 310..311, line/column: 27/13..27/14, file: test] (1)
            )
            Assignment [indexes: 312..322, line/column: 28/1..28/11, file: test] (
                lhs = NamedReference [indexes: 312..318, line/column: 28/1..28/7, file: test] (
                    name = public
                )
                rhs = IntLiteral [indexes: 321..322, line/column: 28/10..28/11, file: test] (1)
            )
            Assignment [indexes: 323..334, line/column: 29/1..29/12, file: test] (
                lhs = NamedReference [indexes: 323..330, line/column: 29/1..29/8, file: test] (
                    name = reified
                )
                rhs = IntLiteral [indexes: 333..334, line/column: 29/11..29/12, file: test] (1)
            )
            Assignment [indexes: 335..345, line/column: 30/1..30/11, file: test] (
                lhs = NamedReference [indexes: 335..341, line/column: 30/1..30/7, file: test] (
                    name = sealed
                )
                rhs = IntLiteral [indexes: 344..345, line/column: 30/10..30/11, file: test] (1)
            )
            Assignment [indexes: 346..357, line/column: 31/1..31/12, file: test] (
                lhs = NamedReference [indexes: 346..353, line/column: 31/1..31/8, file: test] (
                    name = tailrec
                )
                rhs = IntLiteral [indexes: 356..357, line/column: 31/11..31/12, file: test] (1)
            )
            Assignment [indexes: 358..365, line/column: 32/1..32/8, file: test] (
                lhs = NamedReference [indexes: 358..361, line/column: 32/1..32/4, file: test] (
                    name = set
                )
                rhs = IntLiteral [indexes: 364..365, line/column: 32/7..32/8, file: test] (1)
            )
            Assignment [indexes: 366..376, line/column: 33/1..33/11, file: test] (
                lhs = NamedReference [indexes: 366..372, line/column: 33/1..33/7, file: test] (
                    name = vararg
                )
                rhs = IntLiteral [indexes: 375..376, line/column: 33/10..33/11, file: test] (1)
            )
            Assignment [indexes: 377..386, line/column: 34/1..34/10, file: test] (
                lhs = NamedReference [indexes: 377..382, line/column: 34/1..34/6, file: test] (
                    name = where
                )
                rhs = IntLiteral [indexes: 385..386, line/column: 34/9..34/10, file: test] (1)
            )
            Assignment [indexes: 387..396, line/column: 35/1..35/10, file: test] (
                lhs = NamedReference [indexes: 387..392, line/column: 35/1..35/6, file: test] (
                    name = field
                )
                rhs = IntLiteral [indexes: 395..396, line/column: 35/9..35/10, file: test] (1)
            )
            Assignment [indexes: 397..409, line/column: 36/1..36/13, file: test] (
                lhs = NamedReference [indexes: 397..405, line/column: 36/1..36/9, file: test] (
                    name = property
                )
                rhs = IntLiteral [indexes: 408..409, line/column: 36/12..36/13, file: test] (1)
            )
            Assignment [indexes: 410..422, line/column: 37/1..37/13, file: test] (
                lhs = NamedReference [indexes: 410..418, line/column: 37/1..37/9, file: test] (
                    name = receiver
                )
                rhs = IntLiteral [indexes: 421..422, line/column: 37/12..37/13, file: test] (1)
            )
            Assignment [indexes: 423..432, line/column: 38/1..38/10, file: test] (
                lhs = NamedReference [indexes: 423..428, line/column: 38/1..38/6, file: test] (
                    name = param
                )
                rhs = IntLiteral [indexes: 431..432, line/column: 38/9..38/10, file: test] (1)
            )
            Assignment [indexes: 433..445, line/column: 39/1..39/13, file: test] (
                lhs = NamedReference [indexes: 433..441, line/column: 39/1..39/9, file: test] (
                    name = setparam
                )
                rhs = IntLiteral [indexes: 444..445, line/column: 39/12..39/13, file: test] (1)
            )
            Assignment [indexes: 446..458, line/column: 40/1..40/13, file: test] (
                lhs = NamedReference [indexes: 446..454, line/column: 40/1..40/9, file: test] (
                    name = delegate
                )
                rhs = IntLiteral [indexes: 457..458, line/column: 40/12..40/13, file: test] (1)
            )
            Assignment [indexes: 459..467, line/column: 41/1..41/9, file: test] (
                lhs = NamedReference [indexes: 459..463, line/column: 41/1..41/5, file: test] (
                    name = file
                )
                rhs = IntLiteral [indexes: 466..467, line/column: 41/8..41/9, file: test] (1)
            )
            Assignment [indexes: 468..478, line/column: 42/1..42/11, file: test] (
                lhs = NamedReference [indexes: 468..474, line/column: 42/1..42/7, file: test] (
                    name = expect
                )
                rhs = IntLiteral [indexes: 477..478, line/column: 42/10..42/11, file: test] (1)
            )
            Assignment [indexes: 479..489, line/column: 43/1..43/11, file: test] (
                lhs = NamedReference [indexes: 479..485, line/column: 43/1..43/7, file: test] (
                    name = actual
                )
                rhs = IntLiteral [indexes: 488..489, line/column: 43/10..43/11, file: test] (1)
            )
            Assignment [indexes: 490..499, line/column: 44/1..44/10, file: test] (
                lhs = NamedReference [indexes: 490..495, line/column: 44/1..44/6, file: test] (
                    name = const
                )
                rhs = IntLiteral [indexes: 498..499, line/column: 44/9..44/10, file: test] (1)
            )
            Assignment [indexes: 500..511, line/column: 45/1..45/12, file: test] (
                lhs = NamedReference [indexes: 500..507, line/column: 45/1..45/8, file: test] (
                    name = suspend
                )
                rhs = IntLiteral [indexes: 510..511, line/column: 45/11..45/12, file: test] (1)
            )
            Assignment [indexes: 512..521, line/column: 46/1..46/10, file: test] (
                lhs = NamedReference [indexes: 512..517, line/column: 46/1..46/6, file: test] (
                    name = value
                )
                rhs = IntLiteral [indexes: 520..521, line/column: 46/9..46/10, file: test] (1)
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `expression limitations`() {
        val results = ParseTestUtil.parse(
            """
            truth = true || false
            falsehood = true && false
            equality = 1 == 2
            unequality = 1 != 2
            ref_equality = a === b
            ref_unequality = a !== b
            smaller = 1 < 2
            larger = 2 > 1
            smaller_or_equal = 1 <= 1
            larger_or_equal = 2 >= 2
            elvis = potential_null ?: fallback
            """.trimIndent()
        )

        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 8..21, line/column: 1/9..1/22, file: test,
                    erroneousSource = indexes: 13..15, line/column: 1/14..1/16, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 34..47, line/column: 2/13..2/26, file: test,
                    erroneousSource = indexes: 39..41, line/column: 2/18..2/20, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 59..65, line/column: 3/12..3/18, file: test,
                    erroneousSource = indexes: 61..63, line/column: 3/14..3/16, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 79..85, line/column: 4/14..4/20, file: test,
                    erroneousSource = indexes: 81..83, line/column: 4/16..4/18, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 101..108, line/column: 5/16..5/23, file: test,
                    erroneousSource = indexes: 103..106, line/column: 5/18..5/21, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 126..133, line/column: 6/18..6/25, file: test,
                    erroneousSource = indexes: 128..131, line/column: 6/20..6/23, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 144..149, line/column: 7/11..7/16, file: test,
                    erroneousSource = indexes: 146..147, line/column: 7/13..7/14, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 159..164, line/column: 8/10..8/15, file: test,
                    erroneousSource = indexes: 161..162, line/column: 8/12..8/13, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 184..190, line/column: 9/20..9/26, file: test,
                    erroneousSource = indexes: 186..188, line/column: 9/22..9/24, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 209..215, line/column: 10/19..10/25, file: test,
                    erroneousSource = indexes: 211..213, line/column: 10/21..10/23, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 224..250, line/column: 11/9..11/35, file: test,
                    erroneousSource = indexes: 239..241, line/column: 11/24..11/26, file: test
                )
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `unsupported constructs`() {
        val code = """
            a = if (a > b) a else b
            a = for (item in items) {}
            a = while (index < items.size) {}
            a = when (obj) {}
            a = try { input.toInt() } catch (e: NumberFormatException) { null }
            a = object {}
            a = super<Something>.method()

            a = [1, 2, 3]

            a = SomeClass::someMethod

            sum = { x: Int, y: Int -> x + y }

            """.trimIndent()

        val results = ParseTestUtil.parse(code)

        val expected = """
            // (0 .. 23): a = if (a > b) a else b
            ErroneousStatement (
                ParsingError(
                    message = Parsing failure, unexpected tokenType in expression: IF,
                    potentialElementSource = indexes: 4..23, line/column: 1/5..1/24, file: test,
                    erroneousSource = indexes: 4..23, line/column: 1/5..1/24, file: test
                )
            )

            // (24 .. 50): a = for (item in items) {}
            ErroneousStatement (
                ParsingError(
                    message = Parsing failure, unexpected tokenType in expression: FOR,
                    potentialElementSource = indexes: 28..50, line/column: 2/5..2/27, file: test,
                    erroneousSource = indexes: 28..50, line/column: 2/5..2/27, file: test
                )
            )

            // (51 .. 84): a = while (index < items.size) {}
            ErroneousStatement (
                ParsingError(
                    message = Parsing failure, unexpected tokenType in expression: WHILE,
                    potentialElementSource = indexes: 55..84, line/column: 3/5..3/34, file: test,
                    erroneousSource = indexes: 55..84, line/column: 3/5..3/34, file: test
                )
            )

            // (85 .. 102): a = when (obj) {}
            ErroneousStatement (
                ParsingError(
                    message = Parsing failure, unexpected tokenType in expression: WHEN,
                    potentialElementSource = indexes: 89..102, line/column: 4/5..4/18, file: test,
                    erroneousSource = indexes: 89..102, line/column: 4/5..4/18, file: test
                )
            )

            // (103 .. 170): a = try { input.toInt() } catch (e: NumberFormatException) { null }
            ErroneousStatement (
                ParsingError(
                    message = Parsing failure, unexpected tokenType in expression: TRY,
                    potentialElementSource = indexes: 107..170, line/column: 5/5..5/68, file: test,
                    erroneousSource = indexes: 107..170, line/column: 5/5..5/68, file: test
                )
            )

            // (171 .. 184): a = object {}
            ErroneousStatement (
                ParsingError(
                    message = Parsing failure, unexpected tokenType in expression: OBJECT_LITERAL,
                    potentialElementSource = indexes: 175..184, line/column: 6/5..6/14, file: test,
                    erroneousSource = indexes: 175..184, line/column: 6/5..6/14, file: test
                )
            )

            // (185 .. 214): a = super<Something>.method()
            ErroneousStatement (
                ParsingError(
                    message = Parsing failure, unexpected tokenType in expression: SUPER_EXPRESSION,
                    potentialElementSource = indexes: 189..205, line/column: 7/5..7/21, file: test,
                    erroneousSource = indexes: 189..205, line/column: 7/5..7/21, file: test
                )
            )

            // (216 .. 229): a = [1, 2, 3]
            ErroneousStatement (
                ParsingError(
                    message = Parsing failure, unexpected tokenType in expression: COLLECTION_LITERAL_EXPRESSION,
                    potentialElementSource = indexes: 220..229, line/column: 9/5..9/14, file: test,
                    erroneousSource = indexes: 220..229, line/column: 9/5..9/14, file: test
                )
            )

            // (231 .. 256): a = SomeClass::someMethod
            ErroneousStatement (
                ParsingError(
                    message = Parsing failure, unexpected tokenType in expression: CALLABLE_REFERENCE_EXPRESSION,
                    potentialElementSource = indexes: 235..256, line/column: 11/5..11/26, file: test,
                    erroneousSource = indexes: 235..256, line/column: 11/5..11/26, file: test
                )
            )

            // (258 .. 291): sum = { x: Int, y: Int -> x + y }
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = FunctionDeclaration,
                    potentialElementSource = indexes: 264..291, line/column: 13/7..13/34, file: test,
                    erroneousSource = indexes: 264..291, line/column: 13/7..13/34, file: test
                )
            )""".trimIndent()
        results.assert(removeCommentAndEmptyLines(expected))
    }
}
