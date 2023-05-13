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
import org.jetbrains.kotlin.lexer.KtTokens.OPEN_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.REGULAR_STRING_PART
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
fun <T> many(parser: Parser<T>): Parser<List<T>> = {
    manyImpl(parser)
}


private
fun <T> KotlinLexer.manyImpl(parser: Parser<T>): ParserResult<List<T>> {
    var lazyResult: MutableList<T>? = null
    while (true) {
        val mark = currentPosition
        when (val r = parser()) {
            is ParserResult.Failure -> {
                restore(mark)
                return ParserResult.Success(lazyResult ?: emptyList())
            }

            is ParserResult.Success -> {
                val result: MutableList<T> =
                    when (val result = lazyResult) {
                        null -> {
                            mutableListOf<T>().also {
                                lazyResult = it
                            }
                        }

                        else -> result
                    }
                when (tokenType) {
                    null -> return ParserResult.Success(result.apply { add(r.result) })
                    else -> result.add(r.result)
                }
            }
        }
    }
}


@JvmName("plusTU")
internal
inline operator fun <T, U> Parser<T>.plus(crossinline suffix: Parser<U>): Parser<Pair<T, U>> =
    zip(this, suffix) { p, s -> p to s }


@JvmName("plusUnitT")
internal
inline operator fun <T> Parser<Unit>.plus(crossinline suffix: Parser<T>): Parser<T> =
    zip(this, suffix) { _, s -> s }


@JvmName("plusUnitUnit")
internal
inline operator fun Parser<Unit>.plus(crossinline suffix: Parser<Unit>): Parser<Unit> =
    zip(this, suffix) { _, _ -> }


@JvmName("plusTUnit")
internal
inline operator fun <T> Parser<T>.plus(crossinline suffix: Parser<Unit>): Parser<T> =
    zip(this, suffix) { p, _ -> p }


private
inline fun <T, U, R> zip(
    crossinline prefix: Parser<T>,
    crossinline suffix: Parser<U>,
    crossinline f: (T, U) -> R
): Parser<R> = {
    when (val r = prefix()) {
        is ParserResult.Failure -> r
        is ParserResult.Success -> suffix().map {
            f(r.result, it)
        }
    }
}


internal
fun symbol(s: String): Parser<Unit> {
    val failure = failure("Expecting symbol '$s'")
    return {
        if (tokenType == IDENTIFIER && tokenText == s) {
            advance()
            skipWhitespace(false)
            unitSuccess
        } else {
            failure
        }
    }
}


internal
fun stringLiteral(): Parser<String> =
    token(OPEN_QUOTE) +
        token(REGULAR_STRING_PART) { tokenText } +
        token(CLOSING_QUOTE)


internal
fun token(ktToken: KtToken): Parser<Unit> =
    token(ktToken) { }


internal
fun <T> token(token: KtToken, f: KotlinLexer.() -> T): Parser<T> {
    val failure = failure("Expecting token '$token'")
    return {
        when (tokenType) {
            token -> {
                ParserResult.Success(f()).also {
                    advance()
                }
            }

            else -> {
                failure
            }
        }
    }
}


internal
fun ws(): Parser<Unit> = {
    skipWhitespace(false)
    unitSuccess
}


internal
fun wsOrNewLine(): Parser<Unit> = {
    skipWhitespace(true)
    unitSuccess
}


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
    statementSeparatorImpl()
}


private
fun KotlinLexer.statementSeparatorImpl(): ParserResult<Unit> {
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
    return if (seenSeparator) unitSuccess
    else statementSeparatorFailure
}


private
val statementSeparatorFailure = failure("Expecting <STATEMENT SEPARATOR>")


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
