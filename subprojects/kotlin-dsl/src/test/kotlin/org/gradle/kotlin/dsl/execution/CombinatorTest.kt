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
        assertSuccess(parser("foo"))
        assertFailure(
            parser("bar"),
            "Expecting symbol 'foo', but got 'bar' instead"
        )
    }

    @Test
    fun `can parse sequence of symbols`() {
        val parser = symbol("foo") * symbol("bar")
        assertSuccess(parser("foo bar"))
        assertSuccess(parser("foo /*comment*/  bar"))
    }

    @Test
    fun `can parse alternatives`() {
        val parser = symbol("foo") + symbol("bar")
        assertSuccess(parser("foo"))
        assertSuccess(parser("bar"))
        assertFailure(parser("baz"), "Expecting symbol 'bar', but got 'baz' instead")

        val fooBarBaz = parser + symbol("baz")
        assertSuccess(fooBarBaz("baz"))
        assertFailure(fooBarBaz("gazonk"), "Expecting symbol 'baz', but got 'gazonk' instead")
    }

    @Test
    fun `can parse many symbols`() {
        val parser = zeroOrMore(symbol("foo"))
        assertSuccess(
            parser("foo"),
            listOf(Unit)
        )
        assertSuccess(
            parser("foo foo"),
            listOf(Unit, Unit)
        )
    }

    @Test
    fun `can parse many symbols before another symbol`() {
        val parser = zeroOrMore(symbol("foo")) * symbol("bar")
        assertSuccess(
            parser("foo bar"),
            listOf(Unit)
        )
        assertSuccess(
            parser("foo foo bar"),
            listOf(Unit, Unit)
        )
    }

    @Test
    fun `can fail many symbols`() {
        val parser = zeroOrMore(symbol("foo"))
        assertSuccess(
            parser("foofoo"),
            listOf()
        )
    }

    @Test
    fun `can fail on sequence of symbols`() {
        val parser = symbol("foo") * symbol("bar")
        assertFailure(parser("foo"), "Expecting symbol 'bar'")
        assertFailure(parser("bar"), "Expecting symbol 'foo', but got 'bar' instead")
        assertFailure(parser("foobar"), "Expecting symbol 'foo', but got 'foobar' instead")
    }

    @Test
    fun `can parse tokens`() {
        val parser = token(KtTokens.OPEN_QUOTE)
        assertSuccess(parser("\""))
    }

    @Test
    fun `can parse string literals`() {
        val parser = stringLiteral()
        assertSuccess(
            parser("\"foo\""),
            "foo"
        )
    }

    private
    fun assertFailure(parse: ParserResult<Unit>, reason: String) {
        assertThat(
            parse,
            equalTo(ParserResult.Failure(reason))
        )
    }

    private
    fun assertSuccess(result: ParserResult<Unit>) {
        assertSuccess(result, Unit)
    }

    private
    fun <T> assertSuccess(result: ParserResult<T>, expected: T) {
        assertThat(
            result,
            equalTo(ParserResult.Success(expected))
        )
    }
}
