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

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens.CLOSING_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.COMMENTS
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.lexer.KtTokens.LPAR
import org.jetbrains.kotlin.lexer.KtTokens.OPEN_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.REGULAR_STRING_PART
import org.jetbrains.kotlin.lexer.KtTokens.RPAR
import org.jetbrains.kotlin.lexer.KtTokens.SEMICOLON
import org.jetbrains.kotlin.lexer.KtTokens.WHITE_SPACE


internal
typealias Parser<T> = KotlinLexer.() -> ParserResult<T>


internal
inline fun <T, R> Parser<T>.map(crossinline f: (T) -> R): Parser<R> {
    val parser = this
    return {
        parser().map(f)
    }
}


internal
operator fun <T> Parser<T>.invoke(input: String) =
    KotlinLexer().let { lexer ->
        lexer.start(input)
        this(lexer)
    }


internal
fun <T> zeroOrMore(parser: Parser<T>): Parser<List<T>> = {
    var lazyResult: MutableList<T>? = null
    while (tokenType != null) {
        val mark = currentPosition
        when (val r = parser()) {
            is ParserResult.Failure -> {
                restore(mark)
                break
            }

            is ParserResult.Success -> {
                (lazyResult ?: mutableListOf<T>().also { lazyResult = it })
                    .add(r.result)
            }
        }
    }
    ParserResult.Success(lazyResult ?: emptyList())
}


@JvmName("timesTU")
internal
inline operator fun <T, U> Parser<T>.times(crossinline suffix: Parser<U>): Parser<Pair<T, U>> =
    zip(this, suffix) { p, s -> p to s }


@JvmName("timesUnitT")
internal
inline operator fun <T> Parser<Unit>.times(crossinline suffix: Parser<T>): Parser<T> =
    zip(this, suffix) { _, s -> s }


@JvmName("timesUnitUnit")
internal
inline operator fun Parser<Unit>.times(crossinline suffix: Parser<Unit>): Parser<Unit> =
    zipM(this, suffix) { _, _ -> unitSuccess }


@JvmName("timesTUnit")
internal
inline operator fun <T> Parser<T>.times(crossinline suffix: Parser<Unit>): Parser<T> =
    zip(this, suffix) { p, _ -> p }


internal
inline operator fun <T> Parser<T>.plus(crossinline alternative: Parser<T>): Parser<T> =
    either(this, alternative, { it }, { it })


internal
inline fun <T, U> flip(
    crossinline t: Parser<T>,
    crossinline u: Parser<U>,
): Parser<Pair<U, T>> =
    zip(t, u) { tr, ur -> ur to tr }


internal
inline fun <T, U, R> zip(
    crossinline t: Parser<T>,
    crossinline u: Parser<U>,
    crossinline f: (T, U) -> R
): Parser<R> = {
    when (val tr = t()) {
        is ParserResult.Failure -> tr
        is ParserResult.Success -> when (val ur = u()) {
            is ParserResult.Failure -> ur
            is ParserResult.Success -> ParserResult.Success(f(tr.result, ur.result))
        }
    }
}


internal
inline fun <T, U, R> zipM(
    crossinline t: Parser<T>,
    crossinline u: Parser<U>,
    crossinline f: (T, U) -> ParserResult<R>
): Parser<R> = {
    when (val tr = t()) {
        is ParserResult.Failure -> tr
        is ParserResult.Success -> when (val ur = u()) {
            is ParserResult.Failure -> ur
            is ParserResult.Success -> f(tr.result, ur.result)
        }
    }
}


private
inline fun <L, R, T> either(
    crossinline left: Parser<L>,
    crossinline right: Parser<R>,
    crossinline l: (L) -> T,
    crossinline r: (R) -> T,
): Parser<T> = {
    val mark = currentPosition
    when (val lr = left()) {
        is ParserResult.Failure -> {
            restore(mark)
            right().map(r)
        }

        is ParserResult.Success -> {
            lr.map(l)
        }
    }
}


internal
fun symbol(s: String): Parser<Unit> {
    return {
        if (tokenType == IDENTIFIER && tokenText == s) {
            advance()
            skipWhitespace(false)
            unitSuccess
        } else if (tokenType == IDENTIFIER) {
            failure("Expecting symbol '$s', but got '$tokenText' instead")
        } else if (tokenType == null) {
            failure("Expecting symbol '$s'")
        } else {
            failure("Expecting symbol '$s', but got a token of type '$tokenType' instead")
        }
    }
}


internal
fun stringLiteral(): Parser<String> =
    stringLiteral_


private
val stringLiteral_: Parser<String> =
    token(OPEN_QUOTE) *
        token(REGULAR_STRING_PART) { tokenText } *
        token(CLOSING_QUOTE)


internal
fun token(ktToken: KtToken): Parser<Unit> =
    token(ktToken) { }


internal
inline fun <T> token(token: KtToken, crossinline f: KotlinLexer.() -> T): Parser<T> {
    return {
        when (tokenType) {
            token -> {
                ParserResult.Success(f()).also {
                    advance()
                }
            }

            else -> {
                failure("Expecting token of type $token, but got $tokenType${if (tokenType == IDENTIFIER) " ('$tokenText')" else ""} instead")
            }
        }
    }
}


internal
fun ws(): Parser<Unit> = ws_


private
val ws_: Parser<Unit> = {
    skipWhitespace(false)
    unitSuccess
}


internal
fun wsOrNewLine(): Parser<Unit> = {
    skipWhitespace(true)
    unitSuccess
}


internal
inline fun <T> paren(crossinline parser: Parser<T>): Parser<T> =
    lpar * ws() * parser * ws() * rpar


private
val lpar = token(LPAR)


private
val rpar = token(RPAR)


internal
fun <T : Any> optional(parser: Parser<T>): Parser<T?> = {
    val mark = currentPosition
    when (val r = parser()) {
        is ParserResult.Failure -> {
            restore(mark)
            nullSuccess
        }

        is ParserResult.Success -> r
    }
}


private
fun KotlinLexer.skipWhitespace(acceptNewLines: Boolean) {
    while (tokenType != null) {
        when {
            tokenType == WHITE_SPACE && (acceptNewLines || !hasNewLine()) -> {
                advance()
            }

            tokenType in COMMENTS -> {
                advance()
            }

            else -> {
                break
            }
        }
    }
}


internal
fun statementSeparator(): Parser<Unit> = {
    var seenSeparator = false
    while (tokenType != null) {
        when (tokenType) {
            WHITE_SPACE -> {
                if (hasNewLine()) {
                    seenSeparator = true
                }
                advance()
            }

            SEMICOLON -> {
                advance()
                seenSeparator = true
            }

            in COMMENTS -> {
                advance()
            }

            else -> {
                break
            }
        }
    }
    if (seenSeparator) unitSuccess
    else failure("Expecting STATEMENT SEPARATOR, but not found")
}


private
fun KotlinLexer.hasNewLine() =
    '\n' in tokenSequence


internal
fun failure(reason: String): ParserResult.Failure =
    ParserResult.Failure(reason)


internal
sealed interface ParserResult<out T> {
    data class Success<T>(val result: T) : ParserResult<T>
    data class Failure(val reason: String) : ParserResult<Nothing>
}


internal
inline fun <R, T> ParserResult<T>.map(f: (T) -> R): ParserResult<R> = when (this) {
    is ParserResult.Failure -> this
    is ParserResult.Success -> ParserResult.Success(f(result))
}


private
val unitSuccess = ParserResult.Success(Unit)


private
val nullSuccess = ParserResult.Success(null)
