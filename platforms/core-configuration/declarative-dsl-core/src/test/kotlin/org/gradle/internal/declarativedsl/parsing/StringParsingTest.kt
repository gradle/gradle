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

class StringParsingTest {

    @Test
    fun `empty or blank`() {
        val code = readInputFromFile("stringParsingTestInput_emptyOrBlank.txt")

        val results = ParseTestUtil.parse(code)

        val expected = """
                Assignment [indexes: 0..6, line/column: 1/1..1/7, file: test] (
                    lhs = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                        name = a
                    )
                    rhs = StringLiteral [indexes: 4..6, line/column: 1/5..1/7, file: test] ()
                )
                Assignment [indexes: 7..17, line/column: 2/1..2/11, file: test] (
                    lhs = PropertyAccess [indexes: 7..8, line/column: 2/1..2/2, file: test] (
                        name = b
                    )
                    rhs = StringLiteral [indexes: 11..17, line/column: 2/5..2/11, file: test] ()
                )
                Assignment [indexes: 18..25, line/column: 3/1..3/8, file: test] (
                    lhs = PropertyAccess [indexes: 18..19, line/column: 3/1..3/2, file: test] (
                        name = c
                    )
                    rhs = StringLiteral [indexes: 22..25, line/column: 3/5..3/8, file: test] ( )
                )
                Assignment [indexes: 26..37, line/column: 4/1..4/12, file: test] (
                    lhs = PropertyAccess [indexes: 26..27, line/column: 4/1..4/2, file: test] (
                        name = d
                    )
                    rhs = StringLiteral [indexes: 30..37, line/column: 4/5..4/12, file: test] ( )
                )
                Assignment [indexes: 38..47, line/column: 5/1..5/10, file: test] (
                    lhs = PropertyAccess [indexes: 38..39, line/column: 5/1..5/2, file: test] (
                        name = e
                    )
                    rhs = StringLiteral [indexes: 42..47, line/column: 5/5..5/10, file: test] (   )
                )
                Assignment [indexes: 48..61, line/column: 6/1..6/14, file: test] (
                    lhs = PropertyAccess [indexes: 48..49, line/column: 6/1..6/2, file: test] (
                        name = f
                    )
                    rhs = StringLiteral [indexes: 52..61, line/column: 6/5..6/14, file: test] (   )
                )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `escape chars`() {
        val code = readInputFromFile("stringParsingTestInput_escapeChars.txt")

        val results = ParseTestUtil.parse(code)

        val expected = """
                |Assignment [indexes: 0..15, line/column: 1/1..1/16, file: test] (
                |    lhs = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                |        name = s
                |    )
                |    rhs = StringLiteral [indexes: 4..15, line/column: 1/5..1/16, file: test] (_\_	_
                |)
                |)
                |Assignment [indexes: 92..110, line/column: 3/1..3/19, file: test] (
                |    lhs = PropertyAccess [indexes: 92..93, line/column: 3/1..3/2, file: test] (
                |        name = q
                |    )
                |    rhs = StringLiteral [indexes: 96..110, line/column: 3/5..3/19, file: test] (⇤⇥)
                |)""".trimMargin()
        results.assert(expected)
    }

    @Test
    fun `multi-line`() {
        val code = readInputFromFile("stringParsingTestInput_multiLine.txt")

        val results = ParseTestUtil.parse(code)

        val expected = """
                Assignment [indexes: 0..13, line/column: 1/1..1/14, file: test] (
                    lhs = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                        name = a
                    )
                    rhs = StringLiteral [indexes: 4..13, line/column: 1/5..1/14, file: test] (a
                b
                c)
                )
                Assignment [indexes: 14..29, line/column: 2/1..4/5, file: test] (
                    lhs = PropertyAccess [indexes: 14..15, line/column: 2/1..2/2, file: test] (
                        name = b
                    )
                    rhs = StringLiteral [indexes: 18..29, line/column: 2/5..4/5, file: test] (a
                b
                c)
                )
                Assignment [indexes: 30..42, line/column: 5/1..5/13, file: test] (
                    lhs = PropertyAccess [indexes: 30..31, line/column: 5/1..5/2, file: test] (
                        name = q
                    )
                    rhs = StringLiteral [indexes: 34..42, line/column: 5/5..5/13, file: test] (${'$'}1)
                )
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = StringTemplates,
                        potentialElementSource = indexes: 127..145, line/column: 6/5..6/23, file: test,
                        erroneousSource = indexes: 130..142, line/column: 6/8..6/20, file: test
                    )
                )
                ErroneousStatement (
                    UnsupportedConstruct(
                        languageFeature = StringTemplates,
                        potentialElementSource = indexes: 192..201, line/column: 7/5..7/14, file: test,
                        erroneousSource = indexes: 195..198, line/column: 7/8..7/11, file: test
                    )
                )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `templates`() {
        val code = readInputFromFile("stringParsingTestInput_templates.txt")

        val results = ParseTestUtil.parse(code)

        val expected = """
                |Assignment [indexes: 0..9, line/column: 1/1..1/10, file: test] (
                |    lhs = PropertyAccess [indexes: 0..1, line/column: 1/1..1/2, file: test] (
                |        name = a
                |    )
                |    rhs = StringLiteral [indexes: 4..9, line/column: 1/5..1/10, file: test] (abc)
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 14..20, line/column: 2/5..2/11, file: test,
                |        erroneousSource = indexes: 15..19, line/column: 2/6..2/10, file: test
                |    )
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 25..35, line/column: 3/5..3/15, file: test,
                |        erroneousSource = indexes: 28..32, line/column: 3/8..3/12, file: test
                |    )
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 40..52, line/column: 4/5..4/17, file: test,
                |        erroneousSource = indexes: 45..47, line/column: 4/10..4/12, file: test
                |    )
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 57..71, line/column: 5/5..5/19, file: test,
                |        erroneousSource = indexes: 62..66, line/column: 5/10..5/14, file: test
                |    )
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 76..97, line/column: 6/5..8/4, file: test,
                |        erroneousSource = indexes: 82..93, line/column: 7/3..7/14, file: test
                |    )
                |)
                |Assignment [indexes: 98..110, line/column: 9/1..9/13, file: test] (
                |    lhs = PropertyAccess [indexes: 98..99, line/column: 9/1..9/2, file: test] (
                |        name = g
                |    )
                |    rhs = StringLiteral [indexes: 102..110, line/column: 9/5..9/13, file: test] (\n)
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 115..131, line/column: 10/5..10/21, file: test,
                |        erroneousSource = indexes: 119..125, line/column: 10/9..10/15, file: test
                |    )
                |)
                |Assignment [indexes: 132..147, line/column: 11/1..11/16, file: test] (
                |    lhs = PropertyAccess [indexes: 132..133, line/column: 11/1..11/2, file: test] (
                |        name = i
                |    )
                |    rhs = StringLiteral [indexes: 136..147, line/column: 11/5..11/16, file: test] (${'$'} foo)
                |)""".trimMargin()
        results.assert(expected)
    }

    private fun readInputFromFile(fileName: String) = this::class.java.getResource(fileName)?.readText(Charsets.UTF_8)
        ?: error("unable to read from input file $fileName")

}
