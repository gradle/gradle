/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.execution

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.jetbrains.kotlin.lexer.KtTokens
import org.junit.Test


class CombinatorTest {

    @Test
    fun `can parse symbols`() {
        val parser = symbol("foo")
        assertThat(
            parser.parse("foo"),
            equalTo(ParserResult.Success(Unit))
        )
        assertThat(
            parser.parse("bar"),
            equalTo(ParserResult.Failure("Expecting symbol 'foo', got 'IDENTIFIER'"))
        )
    }

    @Test
    fun `can parse sequence of symbols`() {
        val parser = symbol("foo") + symbol("bar")
        assertThat(
            parser.parse("foo bar"),
            equalTo(ParserResult.Success(Unit))
        )
        assertThat(
            parser.parse("foo /*comment*/  bar"),
            equalTo(ParserResult.Success(Unit))
        )
    }

    @Test
    fun `can parse many symbols`() {
        val parser = many(symbol("foo"))
        assertThat(
            parser.parse("foo"),
            equalTo(ParserResult.Success(listOf(Unit)))
        )
        assertThat(
            parser.parse("foo foo"),
            equalTo(ParserResult.Success(listOf(Unit, Unit)))
        )
    }

    @Test
    fun `can parse many symbols before another symbol`() {
        val parser = many(symbol("foo")) + symbol("bar")
        assertThat(
            parser.parse("foo bar"),
            equalTo(ParserResult.Success(listOf(Unit)))
        )
        assertThat(
            parser.parse("foo foo bar"),
            equalTo(ParserResult.Success(listOf(Unit, Unit)))
        )
    }

    @Test
    fun `can fail many symbols`() {
        val parser = many(symbol("foo"))
        assertThat(
            parser.parse("foofoo"),
            equalTo(ParserResult.Success(listOf()))
        )
    }

    @Test
    fun `can fail on sequence of symbols`() {
        val parser = symbol("foo") + symbol("bar")
        assertThat(
            parser.parse("foo"),
            equalTo(ParserResult.Failure("Expecting symbol 'bar', got 'null'"))
        )
        assertThat(
            parser.parse("bar"),
            equalTo(ParserResult.Failure("Expecting symbol 'foo', got 'IDENTIFIER'"))
        )
        assertThat(
            parser.parse("foobar"),
            equalTo(ParserResult.Failure("Expecting symbol 'foo', got 'IDENTIFIER'"))
        )
    }

    @Test
    fun `can parse tokens`() {
        val parser = token(KtTokens.OPEN_QUOTE)
        assertThat(
            parser.parse("\""),
            equalTo(ParserResult.Success(Unit))
        )
    }

    @Test
    fun `can parse string literals`() {
        val parser = stringLiteral()
        assertThat(
            parser.parse("\"foo\""),
            equalTo(ParserResult.Success("foo"))
        )
    }
}
