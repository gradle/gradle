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

import org.gradle.util.internal.ToBeImplemented
import org.junit.jupiter.api.Test

@ToBeImplemented // TODO: remove/fix
class LiteralConstantParsingTest {

    @Test
    fun `integer literals`() {
        val code = """
                val a = 1
                val a = 0x1
                val a = 0X1
                val a = 0b1
                val a = 0B1
                val a = 1L
                val a = 0x1L
                val a = 0X1L
                val a = 0b1L
                val a = 0B1L
                val a = 1l
                val a = 0x1l
                val a = 0X1l
                val a = 0b1l
                val a = 0B1l
                val a = 0
                val a = 1_2
                val a = 12__34
                val a = 0x1_2_3_4
                val a = 0B0
                val a = 0b0001_0010_0100_1000
                val a = 1_2L
                val a = 12__34l
                val a = 0x1_2_3_4L
                val a = 0B0L
                val a = 0b0001_0010_0100_1000l
                val a = 0xa_af1
                val a = 0xa_af_1
            """.trimIndent()

        val results = ParseTestUtil.parse(code)
        val expected = """
                // (0 .. 9): val a = 1
                LocalValue [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 8..9, line/column: 1/9..1/10, file: test] (1)
                )

                // (10 .. 21): val a = 0x1
                LocalValue [indexes: 10..21, line/column: 2/1..2/12, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 18..21, line/column: 2/9..2/12, file: test] (1)
                )

                // (22 .. 33): val a = 0X1
                LocalValue [indexes: 22..33, line/column: 3/1..3/12, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 30..33, line/column: 3/9..3/12, file: test] (1)
                )

                // (34 .. 45): val a = 0b1
                LocalValue [indexes: 34..45, line/column: 4/1..4/12, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 42..45, line/column: 4/9..4/12, file: test] (1)
                )

                // (46 .. 57): val a = 0B1
                LocalValue [indexes: 46..57, line/column: 5/1..5/12, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 54..57, line/column: 5/9..5/12, file: test] (1)
                )

                // (58 .. 68): val a = 1L
                LocalValue [indexes: 58..68, line/column: 6/1..6/11, file: test] (
                    name = a
                    rhs = LongLiteral [indexes: 66..68, line/column: 6/9..6/11, file: test] (1)
                )

                // (69 .. 81): val a = 0x1L
                LocalValue [indexes: 69..81, line/column: 7/1..7/13, file: test] (
                    name = a
                    rhs = LongLiteral [indexes: 77..81, line/column: 7/9..7/13, file: test] (1)
                )

                // (82 .. 94): val a = 0X1L
                LocalValue [indexes: 82..94, line/column: 8/1..8/13, file: test] (
                    name = a
                    rhs = LongLiteral [indexes: 90..94, line/column: 8/9..8/13, file: test] (1)
                )

                // (95 .. 107): val a = 0b1L
                LocalValue [indexes: 95..107, line/column: 9/1..9/13, file: test] (
                    name = a
                    rhs = LongLiteral [indexes: 103..107, line/column: 9/9..9/13, file: test] (1)
                )

                // (108 .. 120): val a = 0B1L
                LocalValue [indexes: 108..120, line/column: 10/1..10/13, file: test] (
                    name = a
                    rhs = LongLiteral [indexes: 116..120, line/column: 10/9..10/13, file: test] (1)
                )

                // (121 .. 131): val a = 1l
                ErroneousStatement (
                    ParsingError(
                        message = Incorrect constant expression, wrong long suffix: 1l,
                        potentialElementSource = indexes: 129..131, line/column: 11/9..11/11, file: test,
                        erroneousSource = indexes: 129..131, line/column: 11/9..11/11, file: test
                    )
                )

                // (132 .. 144): val a = 0x1l
                ErroneousStatement (
                    ParsingError(
                        message = Incorrect constant expression, wrong long suffix: 0x1l,
                        potentialElementSource = indexes: 140..144, line/column: 12/9..12/13, file: test,
                        erroneousSource = indexes: 140..144, line/column: 12/9..12/13, file: test
                    )
                )

                // (145 .. 157): val a = 0X1l
                ErroneousStatement (
                    ParsingError(
                        message = Incorrect constant expression, wrong long suffix: 0X1l,
                        potentialElementSource = indexes: 153..157, line/column: 13/9..13/13, file: test,
                        erroneousSource = indexes: 153..157, line/column: 13/9..13/13, file: test
                    )
                )

                // (158 .. 170): val a = 0b1l
                ErroneousStatement (
                    ParsingError(
                        message = Incorrect constant expression, wrong long suffix: 0b1l,
                        potentialElementSource = indexes: 166..170, line/column: 14/9..14/13, file: test,
                        erroneousSource = indexes: 166..170, line/column: 14/9..14/13, file: test
                    )
                )

                // (171 .. 183): val a = 0B1l
                ErroneousStatement (
                    ParsingError(
                        message = Incorrect constant expression, wrong long suffix: 0B1l,
                        potentialElementSource = indexes: 179..183, line/column: 15/9..15/13, file: test,
                        erroneousSource = indexes: 179..183, line/column: 15/9..15/13, file: test
                    )
                )

                // (184 .. 193): val a = 0
                LocalValue [indexes: 184..193, line/column: 16/1..16/10, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 192..193, line/column: 16/9..16/10, file: test] (0)
                )

                // (194 .. 205): val a = 1_2
                LocalValue [indexes: 194..205, line/column: 17/1..17/12, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 202..205, line/column: 17/9..17/12, file: test] (12)
                )

                // (206 .. 220): val a = 12__34
                LocalValue [indexes: 206..220, line/column: 18/1..18/15, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 214..220, line/column: 18/9..18/15, file: test] (1234)
                )

                // (221 .. 238): val a = 0x1_2_3_4
                LocalValue [indexes: 221..238, line/column: 19/1..19/18, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 229..238, line/column: 19/9..19/18, file: test] (4660)
                )

                // (239 .. 250): val a = 0B0
                LocalValue [indexes: 239..250, line/column: 20/1..20/12, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 247..250, line/column: 20/9..20/12, file: test] (0)
                )

                // (251 .. 280): val a = 0b0001_0010_0100_1000
                LocalValue [indexes: 251..280, line/column: 21/1..21/30, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 259..280, line/column: 21/9..21/30, file: test] (4680)
                )

                // (281 .. 293): val a = 1_2L
                LocalValue [indexes: 281..293, line/column: 22/1..22/13, file: test] (
                    name = a
                    rhs = LongLiteral [indexes: 289..293, line/column: 22/9..22/13, file: test] (12)
                )

                // (294 .. 309): val a = 12__34l
                ErroneousStatement (
                    ParsingError(
                        message = Incorrect constant expression, wrong long suffix: 12__34l,
                        potentialElementSource = indexes: 302..309, line/column: 23/9..23/16, file: test,
                        erroneousSource = indexes: 302..309, line/column: 23/9..23/16, file: test
                    )
                )

                // (310 .. 328): val a = 0x1_2_3_4L
                LocalValue [indexes: 310..328, line/column: 24/1..24/19, file: test] (
                    name = a
                    rhs = LongLiteral [indexes: 318..328, line/column: 24/9..24/19, file: test] (4660)
                )

                // (329 .. 341): val a = 0B0L
                LocalValue [indexes: 329..341, line/column: 25/1..25/13, file: test] (
                    name = a
                    rhs = LongLiteral [indexes: 337..341, line/column: 25/9..25/13, file: test] (0)
                )

                // (342 .. 372): val a = 0b0001_0010_0100_1000l
                ErroneousStatement (
                    ParsingError(
                        message = Incorrect constant expression, wrong long suffix: 0b0001_0010_0100_1000l,
                        potentialElementSource = indexes: 350..372, line/column: 26/9..26/31, file: test,
                        erroneousSource = indexes: 350..372, line/column: 26/9..26/31, file: test
                    )
                )

                // (373 .. 388): val a = 0xa_af1
                LocalValue [indexes: 373..388, line/column: 27/1..27/16, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 381..388, line/column: 27/9..27/16, file: test] (43761)
                )

                // (389 .. 405): val a = 0xa_af_1
                LocalValue [indexes: 389..405, line/column: 28/1..28/17, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 397..405, line/column: 28/9..28/17, file: test] (43761)
                )""".trimIndent()

        results.assert(removeCommentAndEmptyLines(expected))

        // TODO: do we really want to not handle the `l` suffix? if so, the grammar needs to specify that
        // TODO: we do support both bin and hex literals, our grammar says otherwise
    }

    @Test
    fun `floating point literals`() {
        val results = ParseTestUtil.parse("""
                val a = 1.0
                val a = 1e1
                val a = 1.0e1
                val a = 1e-1
                val a = 1.0e-1
                val a = 1F
                val a = 1.0F
                val a = 1e1F
                val a = 1.0e1F
                val a = 1e-1F
                val a = 1.0e-1F
                val a = 1f
                val a = 1.0f
                val a = 1e1f
                val a = 1.0e1f
                val a = 1e-1f
                val a = 1.0e-1f
                val a = .1_1
                val a = 3.141_592
                val a = 1e1__3_7
                val a = 1_0f
                val a = 1e1_2f
                val a = 2_2.0f
                val a = .3_3f
                val a = 3.14_16f
                val a = 6.022___137e+2_3f
            """.trimIndent()
        )

        val expected = """
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 8..11, line/column: 1/9..1/12, file: test,
                        erroneousSource = indexes: 8..11, line/column: 1/9..1/12, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 20..23, line/column: 2/9..2/12, file: test,
                        erroneousSource = indexes: 20..23, line/column: 2/9..2/12, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 32..37, line/column: 3/9..3/14, file: test,
                        erroneousSource = indexes: 32..37, line/column: 3/9..3/14, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 46..50, line/column: 4/9..4/13, file: test,
                        erroneousSource = indexes: 46..50, line/column: 4/9..4/13, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 59..65, line/column: 5/9..5/15, file: test,
                        erroneousSource = indexes: 59..65, line/column: 5/9..5/15, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 74..76, line/column: 6/9..6/11, file: test,
                        erroneousSource = indexes: 74..76, line/column: 6/9..6/11, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 85..89, line/column: 7/9..7/13, file: test,
                        erroneousSource = indexes: 85..89, line/column: 7/9..7/13, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 98..102, line/column: 8/9..8/13, file: test,
                        erroneousSource = indexes: 98..102, line/column: 8/9..8/13, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 111..117, line/column: 9/9..9/15, file: test,
                        erroneousSource = indexes: 111..117, line/column: 9/9..9/15, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 126..131, line/column: 10/9..10/14, file: test,
                        erroneousSource = indexes: 126..131, line/column: 10/9..10/14, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 140..147, line/column: 11/9..11/16, file: test,
                        erroneousSource = indexes: 140..147, line/column: 11/9..11/16, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 156..158, line/column: 12/9..12/11, file: test,
                        erroneousSource = indexes: 156..158, line/column: 12/9..12/11, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 167..171, line/column: 13/9..13/13, file: test,
                        erroneousSource = indexes: 167..171, line/column: 13/9..13/13, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 180..184, line/column: 14/9..14/13, file: test,
                        erroneousSource = indexes: 180..184, line/column: 14/9..14/13, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 193..199, line/column: 15/9..15/15, file: test,
                        erroneousSource = indexes: 193..199, line/column: 15/9..15/15, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 208..213, line/column: 16/9..16/14, file: test,
                        erroneousSource = indexes: 208..213, line/column: 16/9..16/14, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 222..229, line/column: 17/9..17/16, file: test,
                        erroneousSource = indexes: 222..229, line/column: 17/9..17/16, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 238..242, line/column: 18/9..18/13, file: test,
                        erroneousSource = indexes: 238..242, line/column: 18/9..18/13, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 251..260, line/column: 19/9..19/18, file: test,
                        erroneousSource = indexes: 251..260, line/column: 19/9..19/18, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 269..277, line/column: 20/9..20/17, file: test,
                        erroneousSource = indexes: 269..277, line/column: 20/9..20/17, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 286..290, line/column: 21/9..21/13, file: test,
                        erroneousSource = indexes: 286..290, line/column: 21/9..21/13, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 299..305, line/column: 22/9..22/15, file: test,
                        erroneousSource = indexes: 299..305, line/column: 22/9..22/15, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 314..320, line/column: 23/9..23/15, file: test,
                        erroneousSource = indexes: 314..320, line/column: 23/9..23/15, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 329..334, line/column: 24/9..24/14, file: test,
                        erroneousSource = indexes: 329..334, line/column: 24/9..24/14, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 343..351, line/column: 25/9..25/17, file: test,
                        erroneousSource = indexes: 343..351, line/column: 25/9..25/17, file: test
                    )
                )
                ErroneousStatement (
                    ParsingError(
                        message = Parsing failure, unsupported constant type: FLOAT_CONSTANT,
                        potentialElementSource = indexes: 360..377, line/column: 26/9..26/26, file: test,
                        erroneousSource = indexes: 360..377, line/column: 26/9..26/26, file: test
                    )
                )""".trimIndent()
        results.assert(expected)

        // TODO: we don't support either float or real literals, our grammar should say that
    }

    @Test
    fun `unsigned literal`() {
        val code = """
                val a = 1u
                val a = 0u
                val a = 1_1u
                val a = -2u
                val a = 0xFFu
                val a = 0b100u
                val a = 3.14u
                val a = 1e1u
                val a = 1.0e1u
                val a = 2_2.0fu
                val a = 6.022_137e+2_3fu
                val a = 1U
                val a = 0xFU
                val a = 1uU
                val a = 1Uu
                val a = 1Lu
                val a = 1LU
                val a = 1uL
                val a = 1UL
                val a = 3Ul
            """.trimIndent()
        val results = ParseTestUtil.parse(code)

        val expected = """
                // (0 .. 10): val a = 1u
                LocalValue [indexes: 0..10, line/column: 1/1..1/11, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 8..10, line/column: 1/9..1/11, file: test] (1)
                )

                // (11 .. 21): val a = 0u
                LocalValue [indexes: 11..21, line/column: 2/1..2/11, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 19..21, line/column: 2/9..2/11, file: test] (0)
                )

                // (22 .. 34): val a = 1_1u
                LocalValue [indexes: 22..34, line/column: 3/1..3/13, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 30..34, line/column: 3/9..3/13, file: test] (11)
                )

                // (35 .. 46): val a = -2u
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = PrefixExpression,
                        potentialElementSource = indexes: 43..46, line/column: 4/9..4/12, file: test,
                        erroneousSource = indexes: 43..46, line/column: 4/9..4/12, file: test
                    )
                )

                // (47 .. 60): val a = 0xFFu
                LocalValue [indexes: 47..60, line/column: 5/1..5/14, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 55..60, line/column: 5/9..5/14, file: test] (255)
                )

                // (61 .. 75): val a = 0b100u
                LocalValue [indexes: 61..75, line/column: 6/1..6/15, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 69..75, line/column: 6/9..6/15, file: test] (4)
                )

                // (76 .. 89): val a = 3.14u
                ErroneousStatement (
                    ParsingError(
                        message = Missing right hand side in binary expression,
                        potentialElementSource = indexes: 84..89, line/column: 7/9..7/14, file: test,
                        erroneousSource = indexes: 84..89, line/column: 7/9..7/14, file: test
                    )
                )

                // (90 .. 102): val a = 1e1u
                ErroneousStatement (
                    ParsingError(
                        message = Missing right hand side in binary expression,
                        potentialElementSource = indexes: 98..102, line/column: 8/9..8/13, file: test,
                        erroneousSource = indexes: 98..102, line/column: 8/9..8/13, file: test
                    )
                )

                // (103 .. 117): val a = 1.0e1u
                ErroneousStatement (
                    ParsingError(
                        message = Missing right hand side in binary expression,
                        potentialElementSource = indexes: 111..117, line/column: 9/9..9/15, file: test,
                        erroneousSource = indexes: 111..117, line/column: 9/9..9/15, file: test
                    )
                )

                // (118 .. 133): val a = 2_2.0fu
                ErroneousStatement (
                    ParsingError(
                        message = Missing right hand side in binary expression,
                        potentialElementSource = indexes: 126..133, line/column: 10/9..10/16, file: test,
                        erroneousSource = indexes: 126..133, line/column: 10/9..10/16, file: test
                    )
                )

                // (134 .. 158): val a = 6.022_137e+2_3fu
                ErroneousStatement (
                    ParsingError(
                        message = Missing right hand side in binary expression,
                        potentialElementSource = indexes: 142..158, line/column: 11/9..11/25, file: test,
                        erroneousSource = indexes: 142..158, line/column: 11/9..11/25, file: test
                    )
                )

                // (159 .. 169): val a = 1U
                LocalValue [indexes: 159..169, line/column: 12/1..12/11, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 167..169, line/column: 12/9..12/11, file: test] (1)
                )

                // (170 .. 182): val a = 0xFU
                LocalValue [indexes: 170..182, line/column: 13/1..13/13, file: test] (
                    name = a
                    rhs = IntLiteral [indexes: 178..182, line/column: 13/9..13/13, file: test] (15)
                )

                // (183 .. 194): val a = 1uU
                ErroneousStatement (
                    ParsingError(
                        message = Missing right hand side in binary expression,
                        potentialElementSource = indexes: 191..194, line/column: 14/9..14/12, file: test,
                        erroneousSource = indexes: 191..194, line/column: 14/9..14/12, file: test
                    )
                )

                // (195 .. 206): val a = 1Uu
                ErroneousStatement (
                    ParsingError(
                        message = Missing right hand side in binary expression,
                        potentialElementSource = indexes: 203..206, line/column: 15/9..15/12, file: test,
                        erroneousSource = indexes: 203..206, line/column: 15/9..15/12, file: test
                    )
                )

                // (207 .. 218): val a = 1Lu
                ErroneousStatement (
                    ParsingError(
                        message = Missing right hand side in binary expression,
                        potentialElementSource = indexes: 215..218, line/column: 16/9..16/12, file: test,
                        erroneousSource = indexes: 215..218, line/column: 16/9..16/12, file: test
                    )
                )

                // (219 .. 230): val a = 1LU
                ErroneousStatement (
                    ParsingError(
                        message = Missing right hand side in binary expression,
                        potentialElementSource = indexes: 227..230, line/column: 17/9..17/12, file: test,
                        erroneousSource = indexes: 227..230, line/column: 17/9..17/12, file: test
                    )
                )

                // (231 .. 242): val a = 1uL
                LocalValue [indexes: 231..242, line/column: 18/1..18/12, file: test] (
                    name = a
                    rhs = LongLiteral [indexes: 239..242, line/column: 18/9..18/12, file: test] (1)
                )

                // (243 .. 254): val a = 1UL
                LocalValue [indexes: 243..254, line/column: 19/1..19/12, file: test] (
                    name = a
                    rhs = LongLiteral [indexes: 251..254, line/column: 19/9..19/12, file: test] (1)
                )

                // (255 .. 266): val a = 3Ul
                ErroneousStatement (
                    ParsingError(
                        message = Incorrect constant expression, wrong long suffix: 3Ul,
                        potentialElementSource = indexes: 263..266, line/column: 20/9..20/12, file: test,
                        erroneousSource = indexes: 263..266, line/column: 20/9..20/12, file: test
                    )
                )""".trimIndent()
        results.assert(removeCommentAndEmptyLines(expected))

        // TODO: we do support unsigned literals, our grammar should state that
        // TODO: `Missing right hand side in binary expression` is weird...
        // TODO: we should support negative numeric literal values, see PrefixExpression above
    }

    @Test
    fun `boolean literal`() {
        val code = """
                val a = true
                val a = TRUE
                val a = false
                val a = FALSE
            """.trimIndent()
        val results = ParseTestUtil.parse(code)

        val expected = """
                LocalValue [indexes: 0..12, line/column: 1/1..1/13, file: test] (
                    name = a
                    rhs = BooleanLiteral [indexes: 8..12, line/column: 1/9..1/13, file: test] (true)
                )
                LocalValue [indexes: 13..25, line/column: 2/1..2/13, file: test] (
                    name = a
                    rhs = PropertyAccess [indexes: 21..25, line/column: 2/9..2/13, file: test] (
                        name = TRUE
                    )
                )
                LocalValue [indexes: 26..39, line/column: 3/1..3/14, file: test] (
                    name = a
                    rhs = BooleanLiteral [indexes: 34..39, line/column: 3/9..3/14, file: test] (false)
                )
                LocalValue [indexes: 40..53, line/column: 4/1..4/14, file: test] (
                    name = a
                    rhs = PropertyAccess [indexes: 48..53, line/column: 4/9..4/14, file: test] (
                        name = FALSE
                    )
                )""".trimIndent()
        results.assert(removeCommentAndEmptyLines(expected))

        // TODO: the upper-case values should probably produce an error instead of a property access
    }

}
