/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.declarativedsl.parsing

import org.junit.Test

class LiteralConstantParsingTest {

    @Test
    fun `integer literals`() {
        val code = """
                a = 1
                a = 0x1
                a = 0X1
                a = 0b1
                a = 0B1
                a = 1L
                a = 0x1L
                a = 0X1L
                a = 0b1L
                a = 0B1L
                a = 1l
                a = 0x1l
                a = 0X1l
                a = 0b1l
                a = 0B1l
                a = 0
                a = 1_2
                a = 12__34
                a = 0x1_2_3_4
                a = 0B0
                a = 0b0001_0010_0100_1000
                a = 1_2L
                a = -12__34l
                a = 0x1_2_3_4L
                a = 0B0L
                a = -0b0001_0010_0100_1000l
                a = 0xa_af1
                a = -0xa_af_1
            """.trimIndent()

        val results = ParseTestUtil.parse(code)
        val expected = """
                // (0 .. 5): a = 1
                Assignment [indexes: 0..5, line/column: 1/1..1/6, file: test] (
                    lhs = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 4..5, line/column: 1/5..1/6, file: test] (1)
                )

                // (6 .. 13): a = 0x1
                Assignment [indexes: 6..13, line/column: 2/1..2/8, file: test] (
                    lhs = PropertyAccess [indexes: 6..7, line/column: 2/1..2/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 10..13, line/column: 2/5..2/8, file: test] (1)
                )

                // (14 .. 21): a = 0X1
                Assignment [indexes: 14..21, line/column: 3/1..3/8, file: test] (
                    lhs = PropertyAccess [indexes: 14..15, line/column: 3/1..3/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 18..21, line/column: 3/5..3/8, file: test] (1)
                )

                // (22 .. 29): a = 0b1
                Assignment [indexes: 22..29, line/column: 4/1..4/8, file: test] (
                    lhs = PropertyAccess [indexes: 22..23, line/column: 4/1..4/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 26..29, line/column: 4/5..4/8, file: test] (1)
                )

                // (30 .. 37): a = 0B1
                Assignment [indexes: 30..37, line/column: 5/1..5/8, file: test] (
                    lhs = PropertyAccess [indexes: 30..31, line/column: 5/1..5/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 34..37, line/column: 5/5..5/8, file: test] (1)
                )

                // (38 .. 44): a = 1L
                Assignment [indexes: 38..44, line/column: 6/1..6/7, file: test] (
                    lhs = PropertyAccess [indexes: 38..39, line/column: 6/1..6/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 42..44, line/column: 6/5..6/7, file: test] (1)
                )

                // (45 .. 53): a = 0x1L
                Assignment [indexes: 45..53, line/column: 7/1..7/9, file: test] (
                    lhs = PropertyAccess [indexes: 45..46, line/column: 7/1..7/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 49..53, line/column: 7/5..7/9, file: test] (1)
                )

                // (54 .. 62): a = 0X1L
                Assignment [indexes: 54..62, line/column: 8/1..8/9, file: test] (
                    lhs = PropertyAccess [indexes: 54..55, line/column: 8/1..8/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 58..62, line/column: 8/5..8/9, file: test] (1)
                )

                // (63 .. 71): a = 0b1L
                Assignment [indexes: 63..71, line/column: 9/1..9/9, file: test] (
                    lhs = PropertyAccess [indexes: 63..64, line/column: 9/1..9/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 67..71, line/column: 9/5..9/9, file: test] (1)
                )

                // (72 .. 80): a = 0B1L
                Assignment [indexes: 72..80, line/column: 10/1..10/9, file: test] (
                    lhs = PropertyAccess [indexes: 72..73, line/column: 10/1..10/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 76..80, line/column: 10/5..10/9, file: test] (1)
                )

                // (81 .. 87): a = 1l
                Assignment [indexes: 81..87, line/column: 11/1..11/7, file: test] (
                    lhs = PropertyAccess [indexes: 81..82, line/column: 11/1..11/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 85..87, line/column: 11/5..11/7, file: test] (1)
                )

                // (88 .. 96): a = 0x1l
                Assignment [indexes: 88..96, line/column: 12/1..12/9, file: test] (
                    lhs = PropertyAccess [indexes: 88..89, line/column: 12/1..12/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 92..96, line/column: 12/5..12/9, file: test] (1)
                )

                // (97 .. 105): a = 0X1l
                Assignment [indexes: 97..105, line/column: 13/1..13/9, file: test] (
                    lhs = PropertyAccess [indexes: 97..98, line/column: 13/1..13/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 101..105, line/column: 13/5..13/9, file: test] (1)
                )

                // (106 .. 114): a = 0b1l
                Assignment [indexes: 106..114, line/column: 14/1..14/9, file: test] (
                    lhs = PropertyAccess [indexes: 106..107, line/column: 14/1..14/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 110..114, line/column: 14/5..14/9, file: test] (1)
                )

                // (115 .. 123): a = 0B1l
                Assignment [indexes: 115..123, line/column: 15/1..15/9, file: test] (
                    lhs = PropertyAccess [indexes: 115..116, line/column: 15/1..15/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 119..123, line/column: 15/5..15/9, file: test] (1)
                )

                // (124 .. 129): a = 0
                Assignment [indexes: 124..129, line/column: 16/1..16/6, file: test] (
                    lhs = PropertyAccess [indexes: 124..125, line/column: 16/1..16/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 128..129, line/column: 16/5..16/6, file: test] (0)
                )

                // (130 .. 137): a = 1_2
                Assignment [indexes: 130..137, line/column: 17/1..17/8, file: test] (
                    lhs = PropertyAccess [indexes: 130..131, line/column: 17/1..17/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 134..137, line/column: 17/5..17/8, file: test] (12)
                )

                // (138 .. 148): a = 12__34
                Assignment [indexes: 138..148, line/column: 18/1..18/11, file: test] (
                    lhs = PropertyAccess [indexes: 138..139, line/column: 18/1..18/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 142..148, line/column: 18/5..18/11, file: test] (1234)
                )

                // (149 .. 162): a = 0x1_2_3_4
                Assignment [indexes: 149..162, line/column: 19/1..19/14, file: test] (
                    lhs = PropertyAccess [indexes: 149..150, line/column: 19/1..19/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 153..162, line/column: 19/5..19/14, file: test] (4660)
                )

                // (163 .. 170): a = 0B0
                Assignment [indexes: 163..170, line/column: 20/1..20/8, file: test] (
                    lhs = PropertyAccess [indexes: 163..164, line/column: 20/1..20/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 167..170, line/column: 20/5..20/8, file: test] (0)
                )

                // (171 .. 196): a = 0b0001_0010_0100_1000
                Assignment [indexes: 171..196, line/column: 21/1..21/26, file: test] (
                    lhs = PropertyAccess [indexes: 171..172, line/column: 21/1..21/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 175..196, line/column: 21/5..21/26, file: test] (4680)
                )

                // (197 .. 205): a = 1_2L
                Assignment [indexes: 197..205, line/column: 22/1..22/9, file: test] (
                    lhs = PropertyAccess [indexes: 197..198, line/column: 22/1..22/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 201..205, line/column: 22/5..22/9, file: test] (12)
                )

                // (206 .. 218): a = -12__34l
                Assignment [indexes: 206..218, line/column: 23/1..23/13, file: test] (
                    lhs = PropertyAccess [indexes: 206..207, line/column: 23/1..23/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 210..218, line/column: 23/5..23/13, file: test] (-1234)
                )

                // (219 .. 233): a = 0x1_2_3_4L
                Assignment [indexes: 219..233, line/column: 24/1..24/15, file: test] (
                    lhs = PropertyAccess [indexes: 219..220, line/column: 24/1..24/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 223..233, line/column: 24/5..24/15, file: test] (4660)
                )

                // (234 .. 242): a = 0B0L
                Assignment [indexes: 234..242, line/column: 25/1..25/9, file: test] (
                    lhs = PropertyAccess [indexes: 234..235, line/column: 25/1..25/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 238..242, line/column: 25/5..25/9, file: test] (0)
                )

                // (243 .. 270): a = -0b0001_0010_0100_1000l
                Assignment [indexes: 243..270, line/column: 26/1..26/28, file: test] (
                    lhs = PropertyAccess [indexes: 243..244, line/column: 26/1..26/2, file: test] (
                        name = a
                    )
                    rhs = LongLiteral [indexes: 247..270, line/column: 26/5..26/28, file: test] (-4680)
                )

                // (271 .. 282): a = 0xa_af1
                Assignment [indexes: 271..282, line/column: 27/1..27/12, file: test] (
                    lhs = PropertyAccess [indexes: 271..272, line/column: 27/1..27/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 275..282, line/column: 27/5..27/12, file: test] (43761)
                )

                // (283 .. 296): a = -0xa_af_1
                Assignment [indexes: 283..296, line/column: 28/1..28/14, file: test] (
                    lhs = PropertyAccess [indexes: 283..284, line/column: 28/1..28/2, file: test] (
                        name = a
                    )
                    rhs = IntLiteral [indexes: 287..296, line/column: 28/5..28/14, file: test] (-43761)
                )""".trimIndent()

        results.assert(removeCommentAndEmptyLines(expected))
    }

    @Test
    fun `floating point literals`() {
        val results = ParseTestUtil.parse("""
                a = 1.0
                a = 1e1
                a = 1.0e1
                a = 1e-1
                a = 1.0e-1
                a = 1F
                a = 1.0F
                a = 1e1F
                a = 1.0e1F
                a = 1e-1F
                a = 1.0e-1F
                a = 1f
                a = 1.0f
                a = 1e1f
                a = 1.0e1f
                a = 1e-1f
                a = 1.0e-1f
                a = .1_1
                a = 3.141_592
                a = 1e1__3_7
                a = 1_0f
                a = 1e1_2f
                a = 2_2.0f
                a = .3_3f
                a = 3.14_16f
                a = 6.022___137e+2_3f
            """.trimIndent()
        )

        val expected = """
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 4..7, line/column: 1/5..1/8, file: test,
                        erroneousSource = indexes: 4..7, line/column: 1/5..1/8, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 12..15, line/column: 2/5..2/8, file: test,
                        erroneousSource = indexes: 12..15, line/column: 2/5..2/8, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 20..25, line/column: 3/5..3/10, file: test,
                        erroneousSource = indexes: 20..25, line/column: 3/5..3/10, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 30..34, line/column: 4/5..4/9, file: test,
                        erroneousSource = indexes: 30..34, line/column: 4/5..4/9, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 39..45, line/column: 5/5..5/11, file: test,
                        erroneousSource = indexes: 39..45, line/column: 5/5..5/11, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 50..52, line/column: 6/5..6/7, file: test,
                        erroneousSource = indexes: 50..52, line/column: 6/5..6/7, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 57..61, line/column: 7/5..7/9, file: test,
                        erroneousSource = indexes: 57..61, line/column: 7/5..7/9, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 66..70, line/column: 8/5..8/9, file: test,
                        erroneousSource = indexes: 66..70, line/column: 8/5..8/9, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 75..81, line/column: 9/5..9/11, file: test,
                        erroneousSource = indexes: 75..81, line/column: 9/5..9/11, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 86..91, line/column: 10/5..10/10, file: test,
                        erroneousSource = indexes: 86..91, line/column: 10/5..10/10, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 96..103, line/column: 11/5..11/12, file: test,
                        erroneousSource = indexes: 96..103, line/column: 11/5..11/12, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 108..110, line/column: 12/5..12/7, file: test,
                        erroneousSource = indexes: 108..110, line/column: 12/5..12/7, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 115..119, line/column: 13/5..13/9, file: test,
                        erroneousSource = indexes: 115..119, line/column: 13/5..13/9, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 124..128, line/column: 14/5..14/9, file: test,
                        erroneousSource = indexes: 124..128, line/column: 14/5..14/9, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 133..139, line/column: 15/5..15/11, file: test,
                        erroneousSource = indexes: 133..139, line/column: 15/5..15/11, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 144..149, line/column: 16/5..16/10, file: test,
                        erroneousSource = indexes: 144..149, line/column: 16/5..16/10, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 154..161, line/column: 17/5..17/12, file: test,
                        erroneousSource = indexes: 154..161, line/column: 17/5..17/12, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 166..170, line/column: 18/5..18/9, file: test,
                        erroneousSource = indexes: 166..170, line/column: 18/5..18/9, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 175..184, line/column: 19/5..19/14, file: test,
                        erroneousSource = indexes: 175..184, line/column: 19/5..19/14, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 189..197, line/column: 20/5..20/13, file: test,
                        erroneousSource = indexes: 189..197, line/column: 20/5..20/13, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 202..206, line/column: 21/5..21/9, file: test,
                        erroneousSource = indexes: 202..206, line/column: 21/5..21/9, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 211..217, line/column: 22/5..22/11, file: test,
                        erroneousSource = indexes: 211..217, line/column: 22/5..22/11, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 222..228, line/column: 23/5..23/11, file: test,
                        erroneousSource = indexes: 222..228, line/column: 23/5..23/11, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 233..238, line/column: 24/5..24/10, file: test,
                        erroneousSource = indexes: 233..238, line/column: 24/5..24/10, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 243..251, line/column: 25/5..25/13, file: test,
                        erroneousSource = indexes: 243..251, line/column: 25/5..25/13, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 256..273, line/column: 26/5..26/22, file: test,
                        erroneousSource = indexes: 256..273, line/column: 26/5..26/22, file: test
                    )
                )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `unsigned literal`() {
        val code = """
                a = 1u
                a = 0u
                a = 1_1u
                a = -2u
                a = 0xFFu
                a = 0b100u
                a = 3.14u
                a = 1e1u
                a = 1.0e1u
                a = 2_2.0fu
                a = 6.022_137e+2_3fu
                a = 1U
                a = 0xFU
                a = 1uU
                a = 1Uu
                a = 1Lu
                a = 1LU
                a = 1uL
                a = 1UL
                a = 3Ul
            """.trimIndent()
        val results = ParseTestUtil.parse(code)

        val expected = """
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = UnsignedType,
                        potentialElementSource = indexes: 4..6, line/column: 1/5..1/7, file: test,
                        erroneousSource = indexes: 4..6, line/column: 1/5..1/7, file: test
                    )
                )
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = UnsignedType,
                        potentialElementSource = indexes: 11..13, line/column: 2/5..2/7, file: test,
                        erroneousSource = indexes: 11..13, line/column: 2/5..2/7, file: test
                    )
                )
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = UnsignedType,
                        potentialElementSource = indexes: 18..22, line/column: 3/5..3/9, file: test,
                        erroneousSource = indexes: 18..22, line/column: 3/5..3/9, file: test
                    )
                )
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = UnsignedType,
                        potentialElementSource = indexes: 28..30, line/column: 4/6..4/8, file: test,
                        erroneousSource = indexes: 28..30, line/column: 4/6..4/8, file: test
                    )
                )
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = UnsignedType,
                        potentialElementSource = indexes: 35..40, line/column: 5/5..5/10, file: test,
                        erroneousSource = indexes: 35..40, line/column: 5/5..5/10, file: test
                    )
                )
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = UnsignedType,
                        potentialElementSource = indexes: 45..51, line/column: 6/5..6/11, file: test,
                        erroneousSource = indexes: 45..51, line/column: 6/5..6/11, file: test
                    )
                )
                ErroneousStatement (
                    MultipleFailures(
                        ParsingError(
                            message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                            potentialElementSource = indexes: 56..60, line/column: 7/5..7/9, file: test,
                            erroneousSource = indexes: 56..60, line/column: 7/5..7/9, file: test
                        )
                        ParsingError(
                            message = Argument is absent,
                            potentialElementSource = indexes: 62..63, line/column: 8/1..8/2, file: test,
                            erroneousSource = indexes: 62..63, line/column: 8/1..8/2, file: test
                        )
                        ParsingError(
                            message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                            potentialElementSource = indexes: 66..69, line/column: 8/5..8/8, file: test,
                            erroneousSource = indexes: 66..69, line/column: 8/5..8/8, file: test
                        )
                        ParsingError(
                            message = Argument is absent,
                            potentialElementSource = indexes: 71..72, line/column: 9/1..9/2, file: test,
                            erroneousSource = indexes: 71..72, line/column: 9/1..9/2, file: test
                        )
                        ParsingError(
                            message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                            potentialElementSource = indexes: 75..80, line/column: 9/5..9/10, file: test,
                            erroneousSource = indexes: 75..80, line/column: 9/5..9/10, file: test
                        )
                        ParsingError(
                            message = Argument is absent,
                            potentialElementSource = indexes: 82..83, line/column: 10/1..10/2, file: test,
                            erroneousSource = indexes: 82..83, line/column: 10/1..10/2, file: test
                        )
                        ParsingError(
                            message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                            potentialElementSource = indexes: 86..92, line/column: 10/5..10/11, file: test,
                            erroneousSource = indexes: 86..92, line/column: 10/5..10/11, file: test
                        )
                        ParsingError(
                            message = Argument is absent,
                            potentialElementSource = indexes: 94..95, line/column: 11/1..11/2, file: test,
                            erroneousSource = indexes: 94..95, line/column: 11/1..11/2, file: test
                        )
                        ParsingError(
                            message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                            potentialElementSource = indexes: 98..113, line/column: 11/5..11/20, file: test,
                            erroneousSource = indexes: 98..113, line/column: 11/5..11/20, file: test
                        )
                        ParsingError(
                            message = Argument is absent,
                            potentialElementSource = indexes: 115..116, line/column: 12/1..12/2, file: test,
                            erroneousSource = indexes: 115..116, line/column: 12/1..12/2, file: test
                        )
                        UnsupportedConstruct(
                            languageFeature = UnsignedType,
                            potentialElementSource = indexes: 119..121, line/column: 12/5..12/7, file: test,
                            erroneousSource = indexes: 119..121, line/column: 12/5..12/7, file: test
                        )
                    )
                )
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = UnsignedType,
                        potentialElementSource = indexes: 126..130, line/column: 13/5..13/9, file: test,
                        erroneousSource = indexes: 126..130, line/column: 13/5..13/9, file: test
                    )
                )
                ErroneousStatement (
                    MultipleFailures(
                        UnsupportedConstruct(
                            languageFeature = UnsignedType,
                            potentialElementSource = indexes: 135..137, line/column: 14/5..14/7, file: test,
                            erroneousSource = indexes: 135..137, line/column: 14/5..14/7, file: test
                        )
                        ParsingError(
                            message = Argument is absent,
                            potentialElementSource = indexes: 139..140, line/column: 15/1..15/2, file: test,
                            erroneousSource = indexes: 139..140, line/column: 15/1..15/2, file: test
                        )
                        UnsupportedConstruct(
                            languageFeature = UnsignedType,
                            potentialElementSource = indexes: 143..145, line/column: 15/5..15/7, file: test,
                            erroneousSource = indexes: 143..145, line/column: 15/5..15/7, file: test
                        )
                        ParsingError(
                            message = Argument is absent,
                            potentialElementSource = indexes: 147..148, line/column: 16/1..16/2, file: test,
                            erroneousSource = indexes: 147..148, line/column: 16/1..16/2, file: test
                        )
                        ParsingError(
                            message = Argument is absent,
                            potentialElementSource = indexes: 155..156, line/column: 17/1..17/2, file: test,
                            erroneousSource = indexes: 155..156, line/column: 17/1..17/2, file: test
                        )
                        ParsingError(
                            message = Argument is absent,
                            potentialElementSource = indexes: 163..164, line/column: 18/1..18/2, file: test,
                            erroneousSource = indexes: 163..164, line/column: 18/1..18/2, file: test
                        )
                        UnsupportedConstruct(
                            languageFeature = UnsignedType,
                            potentialElementSource = indexes: 167..170, line/column: 18/5..18/8, file: test,
                            erroneousSource = indexes: 167..170, line/column: 18/5..18/8, file: test
                        )
                    )
                )
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = UnsignedType,
                        potentialElementSource = indexes: 175..178, line/column: 19/5..19/8, file: test,
                        erroneousSource = indexes: 175..178, line/column: 19/5..19/8, file: test
                    )
                )
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = UnsignedType,
                        potentialElementSource = indexes: 183..186, line/column: 20/5..20/8, file: test,
                        erroneousSource = indexes: 183..186, line/column: 20/5..20/8, file: test
                    )
                )""".trimIndent()
        results.assert(removeCommentAndEmptyLines(expected))
    }

    @Test
    fun `boolean literal`() {
        val code = """
                a = true
                a = TRUE
                a = false
                a = FALSE
            """.trimIndent()
        val results = ParseTestUtil.parse(code)

        val expected = """
                Assignment [indexes: 0..8, line/column: 1/1..1/9, file: test] (
                    lhs = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                        name = a
                    )
                    rhs = BooleanLiteral [indexes: 4..8, line/column: 1/5..1/9, file: test] (true)
                )
                Assignment [indexes: 9..17, line/column: 2/1..2/9, file: test] (
                    lhs = PropertyAccess [indexes: 9..10, line/column: 2/1..2/2, file: test] (
                        name = a
                    )
                    rhs = PropertyAccess [indexes: 13..17, line/column: 2/5..2/9, file: test] (
                        name = TRUE
                    )
                )
                Assignment [indexes: 18..27, line/column: 3/1..3/10, file: test] (
                    lhs = PropertyAccess [indexes: 18..19, line/column: 3/1..3/2, file: test] (
                        name = a
                    )
                    rhs = BooleanLiteral [indexes: 22..27, line/column: 3/5..3/10, file: test] (false)
                )
                Assignment [indexes: 28..37, line/column: 4/1..4/10, file: test] (
                    lhs = PropertyAccess [indexes: 28..29, line/column: 4/1..4/2, file: test] (
                        name = a
                    )
                    rhs = PropertyAccess [indexes: 32..37, line/column: 4/5..4/10, file: test] (
                        name = FALSE
                    )
                )""".trimIndent()
        results.assert(removeCommentAndEmptyLines(expected))
    }

}
