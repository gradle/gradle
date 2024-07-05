package org.gradle.internal.declarativedsl.parsing


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
        ParseTestUtil.parse(code).assert(expected)
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
        ParseTestUtil.parse(code).assert(expected)
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
        ParseTestUtil.parse(code).assert(expected)
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
    fun `backtick identifiers`() {
        val results = ParseTestUtil.parse(
            """
            `some content with spaces`()
            val `more conent with spaces` = 1
            """.trimIndent()
        )

        val expected = """
            FunctionCall [indexes: 0..28, line/column: 1/1..1/29, file: test] (
                name = `some content with spaces`
                args = []
            )
            LocalValue [indexes: 29..62, line/column: 2/1..2/34, file: test] (
                name = `more conent with spaces`
                rhs = IntLiteral [indexes: 61..62, line/column: 2/33..2/34, file: test] (1)
            )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `reserved keywords`() {
        val results = ParseTestUtil.parse(
            """
            val abstract = 1
            val annotation = 1
            val by = 1
            val catch = 1
            val companion = 1
            val constructor = 1
            val crossinline = 1
            val data = 1
            val dynamic = 1
            val enum = 1
            val external = 1
            val final = 1
            val finally = 1
            val get = 1
            val import = 1
            val infix = 1
            val init = 1
            val inline = 1
            val inner = 1
            val internal = 1
            val lateinit = 1
            val noinline = 1
            val open = 1
            val operator = 1
            val out = 1
            val override = 1
            val private = 1
            val protected = 1
            val public = 1
            val reified = 1
            val sealed = 1
            val tailrec = 1
            val set = 1
            val vararg = 1
            val where = 1
            val field = 1
            val property = 1
            val receiver = 1
            val param = 1
            val setparam = 1
            val delegate = 1
            val file = 1
            val expect = 1
            val actual = 1
            val const = 1
            val suspend = 1
            val value = 1
            """.trimIndent()
        )

        val expected = """
            LocalValue [indexes: 0..16, line/column: 1/1..1/17, file: test] (
                name = abstract
                rhs = IntLiteral [indexes: 15..16, line/column: 1/16..1/17, file: test] (1)
            )
            LocalValue [indexes: 17..35, line/column: 2/1..2/19, file: test] (
                name = annotation
                rhs = IntLiteral [indexes: 34..35, line/column: 2/18..2/19, file: test] (1)
            )
            LocalValue [indexes: 36..46, line/column: 3/1..3/11, file: test] (
                name = by
                rhs = IntLiteral [indexes: 45..46, line/column: 3/10..3/11, file: test] (1)
            )
            LocalValue [indexes: 47..60, line/column: 4/1..4/14, file: test] (
                name = catch
                rhs = IntLiteral [indexes: 59..60, line/column: 4/13..4/14, file: test] (1)
            )
            LocalValue [indexes: 61..78, line/column: 5/1..5/18, file: test] (
                name = companion
                rhs = IntLiteral [indexes: 77..78, line/column: 5/17..5/18, file: test] (1)
            )
            LocalValue [indexes: 79..98, line/column: 6/1..6/20, file: test] (
                name = constructor
                rhs = IntLiteral [indexes: 97..98, line/column: 6/19..6/20, file: test] (1)
            )
            LocalValue [indexes: 99..118, line/column: 7/1..7/20, file: test] (
                name = crossinline
                rhs = IntLiteral [indexes: 117..118, line/column: 7/19..7/20, file: test] (1)
            )
            LocalValue [indexes: 119..131, line/column: 8/1..8/13, file: test] (
                name = data
                rhs = IntLiteral [indexes: 130..131, line/column: 8/12..8/13, file: test] (1)
            )
            LocalValue [indexes: 132..147, line/column: 9/1..9/16, file: test] (
                name = dynamic
                rhs = IntLiteral [indexes: 146..147, line/column: 9/15..9/16, file: test] (1)
            )
            LocalValue [indexes: 148..160, line/column: 10/1..10/13, file: test] (
                name = enum
                rhs = IntLiteral [indexes: 159..160, line/column: 10/12..10/13, file: test] (1)
            )
            LocalValue [indexes: 161..177, line/column: 11/1..11/17, file: test] (
                name = external
                rhs = IntLiteral [indexes: 176..177, line/column: 11/16..11/17, file: test] (1)
            )
            LocalValue [indexes: 178..191, line/column: 12/1..12/14, file: test] (
                name = final
                rhs = IntLiteral [indexes: 190..191, line/column: 12/13..12/14, file: test] (1)
            )
            LocalValue [indexes: 192..207, line/column: 13/1..13/16, file: test] (
                name = finally
                rhs = IntLiteral [indexes: 206..207, line/column: 13/15..13/16, file: test] (1)
            )
            LocalValue [indexes: 208..219, line/column: 14/1..14/12, file: test] (
                name = get
                rhs = IntLiteral [indexes: 218..219, line/column: 14/11..14/12, file: test] (1)
            )
            LocalValue [indexes: 220..234, line/column: 15/1..15/15, file: test] (
                name = import
                rhs = IntLiteral [indexes: 233..234, line/column: 15/14..15/15, file: test] (1)
            )
            LocalValue [indexes: 235..248, line/column: 16/1..16/14, file: test] (
                name = infix
                rhs = IntLiteral [indexes: 247..248, line/column: 16/13..16/14, file: test] (1)
            )
            LocalValue [indexes: 249..261, line/column: 17/1..17/13, file: test] (
                name = init
                rhs = IntLiteral [indexes: 260..261, line/column: 17/12..17/13, file: test] (1)
            )
            LocalValue [indexes: 262..276, line/column: 18/1..18/15, file: test] (
                name = inline
                rhs = IntLiteral [indexes: 275..276, line/column: 18/14..18/15, file: test] (1)
            )
            LocalValue [indexes: 277..290, line/column: 19/1..19/14, file: test] (
                name = inner
                rhs = IntLiteral [indexes: 289..290, line/column: 19/13..19/14, file: test] (1)
            )
            LocalValue [indexes: 291..307, line/column: 20/1..20/17, file: test] (
                name = internal
                rhs = IntLiteral [indexes: 306..307, line/column: 20/16..20/17, file: test] (1)
            )
            LocalValue [indexes: 308..324, line/column: 21/1..21/17, file: test] (
                name = lateinit
                rhs = IntLiteral [indexes: 323..324, line/column: 21/16..21/17, file: test] (1)
            )
            LocalValue [indexes: 325..341, line/column: 22/1..22/17, file: test] (
                name = noinline
                rhs = IntLiteral [indexes: 340..341, line/column: 22/16..22/17, file: test] (1)
            )
            LocalValue [indexes: 342..354, line/column: 23/1..23/13, file: test] (
                name = open
                rhs = IntLiteral [indexes: 353..354, line/column: 23/12..23/13, file: test] (1)
            )
            LocalValue [indexes: 355..371, line/column: 24/1..24/17, file: test] (
                name = operator
                rhs = IntLiteral [indexes: 370..371, line/column: 24/16..24/17, file: test] (1)
            )
            LocalValue [indexes: 372..383, line/column: 25/1..25/12, file: test] (
                name = out
                rhs = IntLiteral [indexes: 382..383, line/column: 25/11..25/12, file: test] (1)
            )
            LocalValue [indexes: 384..400, line/column: 26/1..26/17, file: test] (
                name = override
                rhs = IntLiteral [indexes: 399..400, line/column: 26/16..26/17, file: test] (1)
            )
            LocalValue [indexes: 401..416, line/column: 27/1..27/16, file: test] (
                name = private
                rhs = IntLiteral [indexes: 415..416, line/column: 27/15..27/16, file: test] (1)
            )
            LocalValue [indexes: 417..434, line/column: 28/1..28/18, file: test] (
                name = protected
                rhs = IntLiteral [indexes: 433..434, line/column: 28/17..28/18, file: test] (1)
            )
            LocalValue [indexes: 435..449, line/column: 29/1..29/15, file: test] (
                name = public
                rhs = IntLiteral [indexes: 448..449, line/column: 29/14..29/15, file: test] (1)
            )
            LocalValue [indexes: 450..465, line/column: 30/1..30/16, file: test] (
                name = reified
                rhs = IntLiteral [indexes: 464..465, line/column: 30/15..30/16, file: test] (1)
            )
            LocalValue [indexes: 466..480, line/column: 31/1..31/15, file: test] (
                name = sealed
                rhs = IntLiteral [indexes: 479..480, line/column: 31/14..31/15, file: test] (1)
            )
            LocalValue [indexes: 481..496, line/column: 32/1..32/16, file: test] (
                name = tailrec
                rhs = IntLiteral [indexes: 495..496, line/column: 32/15..32/16, file: test] (1)
            )
            LocalValue [indexes: 497..508, line/column: 33/1..33/12, file: test] (
                name = set
                rhs = IntLiteral [indexes: 507..508, line/column: 33/11..33/12, file: test] (1)
            )
            LocalValue [indexes: 509..523, line/column: 34/1..34/15, file: test] (
                name = vararg
                rhs = IntLiteral [indexes: 522..523, line/column: 34/14..34/15, file: test] (1)
            )
            LocalValue [indexes: 524..537, line/column: 35/1..35/14, file: test] (
                name = where
                rhs = IntLiteral [indexes: 536..537, line/column: 35/13..35/14, file: test] (1)
            )
            LocalValue [indexes: 538..551, line/column: 36/1..36/14, file: test] (
                name = field
                rhs = IntLiteral [indexes: 550..551, line/column: 36/13..36/14, file: test] (1)
            )
            LocalValue [indexes: 552..568, line/column: 37/1..37/17, file: test] (
                name = property
                rhs = IntLiteral [indexes: 567..568, line/column: 37/16..37/17, file: test] (1)
            )
            LocalValue [indexes: 569..585, line/column: 38/1..38/17, file: test] (
                name = receiver
                rhs = IntLiteral [indexes: 584..585, line/column: 38/16..38/17, file: test] (1)
            )
            LocalValue [indexes: 586..599, line/column: 39/1..39/14, file: test] (
                name = param
                rhs = IntLiteral [indexes: 598..599, line/column: 39/13..39/14, file: test] (1)
            )
            LocalValue [indexes: 600..616, line/column: 40/1..40/17, file: test] (
                name = setparam
                rhs = IntLiteral [indexes: 615..616, line/column: 40/16..40/17, file: test] (1)
            )
            LocalValue [indexes: 617..633, line/column: 41/1..41/17, file: test] (
                name = delegate
                rhs = IntLiteral [indexes: 632..633, line/column: 41/16..41/17, file: test] (1)
            )
            LocalValue [indexes: 634..646, line/column: 42/1..42/13, file: test] (
                name = file
                rhs = IntLiteral [indexes: 645..646, line/column: 42/12..42/13, file: test] (1)
            )
            LocalValue [indexes: 647..661, line/column: 43/1..43/15, file: test] (
                name = expect
                rhs = IntLiteral [indexes: 660..661, line/column: 43/14..43/15, file: test] (1)
            )
            LocalValue [indexes: 662..676, line/column: 44/1..44/15, file: test] (
                name = actual
                rhs = IntLiteral [indexes: 675..676, line/column: 44/14..44/15, file: test] (1)
            )
            LocalValue [indexes: 677..690, line/column: 45/1..45/14, file: test] (
                name = const
                rhs = IntLiteral [indexes: 689..690, line/column: 45/13..45/14, file: test] (1)
            )
            LocalValue [indexes: 691..706, line/column: 46/1..46/16, file: test] (
                name = suspend
                rhs = IntLiteral [indexes: 705..706, line/column: 46/15..46/16, file: test] (1)
            )
            LocalValue [indexes: 707..720, line/column: 47/1..47/14, file: test] (
                name = value
                rhs = IntLiteral [indexes: 719..720, line/column: 47/13..47/14, file: test] (1)
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
                    erroneousSource = indexes: 8..21, line/column: 1/9..1/22, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 34..47, line/column: 2/13..2/26, file: test,
                    erroneousSource = indexes: 34..47, line/column: 2/13..2/26, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 59..65, line/column: 3/12..3/18, file: test,
                    erroneousSource = indexes: 59..65, line/column: 3/12..3/18, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 79..85, line/column: 4/14..4/20, file: test,
                    erroneousSource = indexes: 79..85, line/column: 4/14..4/20, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 101..108, line/column: 5/16..5/23, file: test,
                    erroneousSource = indexes: 101..108, line/column: 5/16..5/23, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 126..133, line/column: 6/18..6/25, file: test,
                    erroneousSource = indexes: 126..133, line/column: 6/18..6/25, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 144..149, line/column: 7/11..7/16, file: test,
                    erroneousSource = indexes: 144..149, line/column: 7/11..7/16, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 159..164, line/column: 8/10..8/15, file: test,
                    erroneousSource = indexes: 159..164, line/column: 8/10..8/15, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 184..190, line/column: 9/20..9/26, file: test,
                    erroneousSource = indexes: 184..190, line/column: 9/20..9/26, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 209..215, line/column: 10/19..10/25, file: test,
                    erroneousSource = indexes: 209..215, line/column: 10/19..10/25, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 224..250, line/column: 11/9..11/35, file: test,
                    erroneousSource = indexes: 224..250, line/column: 11/9..11/35, file: test
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
