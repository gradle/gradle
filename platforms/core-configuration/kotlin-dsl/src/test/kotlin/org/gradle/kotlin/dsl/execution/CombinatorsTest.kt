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
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.junit.Test


class CombinatorsTest {

    private
    val combinator = Combinator(ignoresComments = false, ignoresNewline = false)

    @Test
    fun `can recurse`() {
        var parser by reference<String>()
        parser = combinator.paren(parser) + combinator.token(IDENTIFIER) { tokenText }

        assertSuccess(parser("ok"), "ok")
        assertSuccess(parser("(ok)"), "ok")
        assertSuccess(parser("((ok))"), "ok")
        assertSuccess(parser("(((ok)))"), "ok")
        assertFailure(parser("(ok("), "Expecting token of type IDENTIFIER, but got LPAR instead")
    }

    @Test
    fun `can parse tokens`() {
        val parser = combinator.token(KtTokens.OPEN_QUOTE)
        assertSuccess(parser("\""))
    }

    @Test
    fun `can parse string literals`() {
        val parser = combinator.stringLiteral
        assertSuccess(
            parser("\"foo\""),
            "foo"
        )
    }

    @Test
    fun `can parse integers`() {
        val parser = combinator.integerLiteral

        // regular integers
        assertSuccess(parser("0"))
        assertSuccess(parser("5"))
        assertSuccess(parser("13"))
        assertSuccess(parser("1_000_000"))

        // unsigned integers
        assertSuccess(parser("0u"))
        assertSuccess(parser("5U"))
        assertSuccess(parser("13u"))
        assertSuccess(parser("1_000_000U"))

        // long integers
        assertSuccess(parser("0L"))
        assertSuccess(parser("5l"))
        assertSuccess(parser("13L"))
        assertSuccess(parser("1_000_000l"))

        // unsigned long integers
        assertSuccess(parser("0uL"))
        assertSuccess(parser("5Ul"))
        assertSuccess(parser("13UL"))
        assertSuccess(parser("1_000_000ul"))

        // hex numbers
        assertSuccess(parser("0x0"))
        assertSuccess(parser("0X0"))
        assertSuccess(parser("0Xff_ee"))
        assertSuccess(parser("0Xabc_e"))

        // binary numbers
        assertSuccess(parser("0b0"))
        assertSuccess(parser("0B0"))
        assertSuccess(parser("0b101010"))
        assertSuccess(parser("0b10101_0"))

        assertFailure(
            parser("bar"),
            "Expecting token of type INTEGER_LITERAL, but got IDENTIFIER ('bar') instead"
        )
    }

    @Test
    fun `can parse floating point numbers`() {
        val parser = combinator.floatLiteral

        assertSuccess(parser("0e0"))
        assertSuccess(parser("0E0"))
        assertSuccess(parser(".5"))
        assertSuccess(parser("0.5"))
        assertSuccess(parser("0.5e10"))

        assertSuccess(parser("0f"))
        assertSuccess(parser("0F"))
        assertSuccess(parser("12e3F"))
        assertSuccess(parser(".5f"))
        assertSuccess(parser("0.5f"))
        assertSuccess(parser("0.5e10f"))

        assertFailure(
            parser("bar"),
            "Expecting token of type FLOAT_CONSTANT, but got IDENTIFIER ('bar') instead"
        )
    }

    @Test
    fun `can parse boolean literals`() {
        val parser = combinator.booleanLiteral

        assertSuccess(parser("true"), true)
        assertSuccess(parser("false"), false)

        assertFailure(
            parser("TRUE"),
            "Expecting token of type false, but got IDENTIFIER ('TRUE') instead"
        )
    }

    @Test
    fun `can parse character literals`() {
        val parser = combinator.characterLiteral

        assertSuccess(parser("'\''"))
        assertSuccess(parser("'\n'"))
        assertSuccess(parser("'\u0036'"))

        assertFailure(
            parser("TRUE"),
            "Expecting token of type CHARACTER_LITERAL, but got IDENTIFIER ('TRUE') instead"
        )
    }

    @Test
    fun `can parse symbols`() {
        val parser = combinator.symbol("foo")
        assertSuccess(parser("foo"))
        assertFailure(
            parser("bar"),
            "Expecting symbol 'foo', but got 'bar' instead"
        )
    }

    @Test
    fun `can parse sequence of symbols`() {
        val parser = combinator.symbol("foo") * combinator.symbol("bar")
        assertSuccess(parser("foo bar"))
        assertSuccess(parser("foo /*comment*/  bar"))
    }

    @Test
    fun `can parse alternatives`() {
        val parser = combinator.symbol("foo") + combinator.symbol("bar")
        assertSuccess(parser("foo"))
        assertSuccess(parser("bar"))
        assertFailure(parser("baz"), "Expecting symbol 'bar', but got 'baz' instead")

        val fooBarBaz = parser + combinator.symbol("baz")
        assertSuccess(fooBarBaz("baz"))
        assertFailure(fooBarBaz("gazonk"), "Expecting symbol 'baz', but got 'gazonk' instead")
    }

    @Test
    fun `can parse many symbols`() {
        val parser = zeroOrMore(combinator.symbol("foo"))
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
        val parser = zeroOrMore(combinator.symbol("foo")) * combinator.symbol("bar")
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
        val parser = zeroOrMore(combinator.symbol("foo"))
        assertSuccess(
            parser("foofoo"),
            listOf()
        )
    }

    @Test
    fun `fails to parse no symbol as one or more`() {
        val parser = oneOrMore(combinator.symbol("foo"))
        assertFailure(
            parser(""),
            "Expecting at least one occurrence, but none found"
        )
    }

    @Test
    fun `can parse one symbol as one or more`() {
        val parser = oneOrMore(combinator.symbol("foo"))
        assertSuccess(
            parser("foo"),
            listOf(Unit)
        )
    }

    @Test
    fun `can parse multiple symbols as one or more`() {
        val parser = oneOrMore(combinator.symbol("foo"))
        assertSuccess(
            parser("foo foo"),
            listOf(Unit, Unit)
        )
    }

    @Test
    fun `can fail on sequence of symbols`() {
        val parser = combinator.symbol("foo") * combinator.symbol("bar")
        assertFailure(parser("foo"), "Expecting symbol 'bar'")
        assertFailure(parser("bar"), "Expecting symbol 'foo', but got 'bar' instead")
        assertFailure(parser("foobar"), "Expecting symbol 'foo', but got 'foobar' instead")
    }

    private
    fun assertFailure(parse: ParserResult<*>, reason: String) {
        assertThat(
            parse,
            equalTo(failure(reason))
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


