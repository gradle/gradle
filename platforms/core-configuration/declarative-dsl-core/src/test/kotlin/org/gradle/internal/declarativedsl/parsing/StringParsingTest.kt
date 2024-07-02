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
class StringParsingTest {

    @Test
    fun `empty or blank`() {
        val code = readInputFromFile("stringParsingTestInput_emptyOrBlank.kt")

        val results = ParseTestUtil.parse(code)

        val expected = """
                LocalValue [indexes: 0..10, line/column: 1/1..1/11, file: test] (
                    name = a
                    rhs = StringLiteral [indexes: 8..10, line/column: 1/9..1/11, file: test] ()
                )
                LocalValue [indexes: 11..25, line/column: 2/1..2/15, file: test] (
                    name = b
                    rhs = StringLiteral [indexes: 19..25, line/column: 2/9..2/15, file: test] ()
                )
                LocalValue [indexes: 26..37, line/column: 3/1..3/12, file: test] (
                    name = c
                    rhs = StringLiteral [indexes: 34..37, line/column: 3/9..3/12, file: test] ( )
                )
                LocalValue [indexes: 38..53, line/column: 4/1..4/16, file: test] (
                    name = d
                    rhs = StringLiteral [indexes: 46..53, line/column: 4/9..4/16, file: test] ( )
                )
                LocalValue [indexes: 54..67, line/column: 5/1..5/14, file: test] (
                    name = e
                    rhs = StringLiteral [indexes: 62..67, line/column: 5/9..5/14, file: test] (   )
                )
                LocalValue [indexes: 68..85, line/column: 6/1..6/18, file: test] (
                    name = f
                    rhs = StringLiteral [indexes: 76..85, line/column: 6/9..6/18, file: test] (   )
                )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `escape chars`() {
        val code = readInputFromFile("stringParsingTestInput_escapeChars.kt")

        val results = ParseTestUtil.parse(code)

        val expected = """
                |LocalValue [indexes: 0..94, line/column: 1/1..1/95, file: test] (
                |    name = s
                |    rhs = StringLiteral [indexes: 8..19, line/column: 1/9..1/20, file: test] (_${'\\'}_${'\t'}_${'\n'})
                |)
                |LocalValue [indexes: 96..118, line/column: 3/1..3/23, file: test] (
                |    name = q
                |    rhs = StringLiteral [indexes: 104..118, line/column: 3/9..3/23, file: test] (⇤⇥)
                |)""".trimMargin()
        results.assert(expected)
    }

    @Test
    fun `multi-line`() {
        val code = readInputFromFile("stringParsingTestInput_multiLine.kt")

        val results = ParseTestUtil.parse(code)

        val expected = """
                LocalValue [indexes: 0..17, line/column: 1/1..1/18, file: test] (
                    name = a
                    rhs = StringLiteral [indexes: 8..17, line/column: 1/9..1/18, file: test] (a
                b
                c)
                )
                LocalValue [indexes: 18..37, line/column: 2/1..4/5, file: test] (
                    name = b
                    rhs = StringLiteral [indexes: 26..37, line/column: 2/9..4/5, file: test] (a
                b
                c)
                )""".trimIndent()
        results.assert(expected)
    }

    @Test
    fun `templates`() {
        val code = readInputFromFile("stringParsingTestInput_templates.kt")

        val results = ParseTestUtil.parse(code)

        val expected = """
                |LocalValue [indexes: 0..13, line/column: 1/1..1/14, file: test] (
                |    name = a
                |    rhs = StringLiteral [indexes: 8..13, line/column: 1/9..1/14, file: test] (abc)
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 22..28, line/column: 2/9..2/15, file: test,
                |        erroneousSource = indexes: 23..27, line/column: 2/10..2/14, file: test
                |    )
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 37..47, line/column: 3/9..3/19, file: test,
                |        erroneousSource = indexes: 40..44, line/column: 3/12..3/16, file: test
                |    )
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 56..68, line/column: 4/9..4/21, file: test,
                |        erroneousSource = indexes: 61..63, line/column: 4/14..4/16, file: test
                |    )
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 77..91, line/column: 5/9..5/23, file: test,
                |        erroneousSource = indexes: 82..86, line/column: 5/14..5/18, file: test
                |    )
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 100..121, line/column: 6/9..8/4, file: test,
                |        erroneousSource = indexes: 106..117, line/column: 7/3..7/14, file: test
                |    )
                |)
                |LocalValue [indexes: 122..138, line/column: 9/1..9/17, file: test] (
                |    name = g
                |    rhs = StringLiteral [indexes: 130..138, line/column: 9/9..9/17, file: test] (\n)
                |)
                |ErroneousStatement (
                |    UnsupportedConstruct(
                |        languageFeature = StringTemplates,
                |        potentialElementSource = indexes: 147..163, line/column: 10/9..10/25, file: test,
                |        erroneousSource = indexes: 151..157, line/column: 10/13..10/19, file: test
                |    )
                |)
                |LocalValue [indexes: 164..183, line/column: 11/1..11/20, file: test] (
                |    name = i
                |    rhs = StringLiteral [indexes: 172..183, line/column: 11/9..11/20, file: test] (${'$'} foo)
                |)""".trimMargin()
        results.assert(expected)
    }

    private fun readInputFromFile(fileName: String) = this::class.java.getResource(fileName)?.readText(Charsets.UTF_8)!!

}
