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
import org.jetbrains.kotlin.lexer.KtTokens


typealias Parser<T> = KotlinLexer.() -> ParserResult<T>


inline
fun <T, R> Parser<T>.map(crossinline f: (T) -> R): Parser<R> {
    val parser = this
    return {
        parser().map(f)
    }
}


fun <T> Parser<T>.parse(input: String) =
    KotlinLexer().let { lexer ->
        lexer.start(input)
        this(lexer)
    }


fun <T> many(parser: Parser<T>): Parser<List<T>> = {
    many_(parser)
}


private
fun <T> KotlinLexer.many_(parser: Parser<T>): ParserResult<List<T>> {
    val result = mutableListOf<T>()
    while (true) {
        val mark = currentPosition
        when (val r = parser()) {
            is ParserResult.Failure -> {
                restore(mark)
                return ParserResult.Success(result)
            }

            is ParserResult.Success -> when (tokenType) {
                null -> return ParserResult.Success(result.apply { add(r.result) })
                else -> result.add(r.result)
            }
        }
    }
}


@JvmName("plusUnitT")
operator fun <T> Parser<Unit>.plus(suffix: Parser<T>): Parser<T> {
    val prefix = this
    return {
        // TODO: combine results of prefix and suffix
        when (val r = prefix()) {
            is ParserResult.Failure -> r
            is ParserResult.Success -> suffix()
        }
    }
}


@JvmName("plusUnitUnit")
operator fun Parser<Unit>.plus(suffix: Parser<Unit>): Parser<Unit> {
    val prefix = this
    return {
        when (val r = prefix()) {
            is ParserResult.Failure -> r
            is ParserResult.Success -> suffix()
        }
    }
}


@JvmName("plusTUnit")
operator fun <T> Parser<T>.plus(suffix: Parser<Unit>): Parser<T> {
    val prefix = this
    return {
        when (val r = prefix()) {
            is ParserResult.Failure -> r
            is ParserResult.Success -> suffix().map { r.result }
        }
    }
}


@JvmName("plusTR")
operator fun <T, R> Parser<T>.plus(suffix: Parser<R>): Parser<Pair<T, R>> {
    val prefix = this
    return {
        when (val r = prefix()) {
            is ParserResult.Failure -> r
            is ParserResult.Success -> suffix().map {
                r.result to it
            }
        }
    }
}


fun symbol(s: String): Parser<Unit> = {
    if (tokenType == KtTokens.IDENTIFIER && tokenText == s) {
        advance()
        skipWhitespace(false)
        ParserResult.Success(Unit)
    } else failure("Expecting symbol '$s', got '$tokenType'")
}


fun stringLiteral(): Parser<String> =
    token(KtTokens.OPEN_QUOTE) +
        token(KtTokens.REGULAR_STRING_PART) { tokenText } +
        token(KtTokens.CLOSING_QUOTE)


fun token(ktToken: KtToken): Parser<Unit> =
    token(ktToken) { }


fun <T> token(token: KtToken, f: KotlinLexer.() -> T): Parser<T> = {
    when (tokenType) {
        token -> {
            ParserResult.Success(f()).also {
                advance()
            }
        }

        else -> {
            failure("Expecting token '$token', got '$tokenType'")
        }
    }
}


fun ws(): Parser<Unit> = {
    skipWhitespace(false)
    ParserResult.Success(Unit)
}


fun wsOrNewLine(): Parser<Unit> = {
    skipWhitespace(true)
    ParserResult.Success(Unit)
}


fun <T : Any> optional(parser: Parser<T>): Parser<T?> = {
    val mark = currentPosition
    when (val r = parser()) {
        is ParserResult.Failure -> {
            restore(mark)
            ParserResult.Success(null)
        }

        is ParserResult.Success -> r
    }
}


fun KotlinLexer.skipWhitespace(acceptNewLines: Boolean) {
    while (tokenType != null) {
        if (tokenType == KtTokens.WHITE_SPACE && (acceptNewLines || !hasNewLine())) {
            advance()
        } else if (tokenType in KtTokens.COMMENTS) {
            advance()
        } else {
            break
        }
    }
}


fun statementSeparator(): Parser<Unit> = {
    statementSeparator_()
}


private
fun KotlinLexer.statementSeparator_(): ParserResult<Unit> {
    var seenSeparator = false
    while (tokenType != null) {
        if (tokenType == KtTokens.WHITE_SPACE) {
            if (hasNewLine()) {
                seenSeparator = true
            }
            advance()
        } else if (tokenType == KtTokens.SEMICOLON) {
            advance()
            seenSeparator = true
        } else if (tokenType in KtTokens.COMMENTS) {
            advance()
        } else {
            break
        }
    }
    return if (seenSeparator) ParserResult.Success(Unit)
    else failure("Expecting <STATEMENT SEPARATOR>")
}


private
fun KotlinLexer.hasNewLine() = '\n' in tokenSequence


fun KotlinLexer.failure(reason: String): ParserResult.Failure =
    ParserResult.Failure("$reason-->${currentPosition.offset}:`$tokenSequence`")


sealed interface ParserResult<out T> {
    data class Success<T>(val result: T) : ParserResult<T>
    data class Failure(val reason: String) : ParserResult<Nothing>
}


inline
fun <R, T> ParserResult<T>.map(f: (T) -> R): ParserResult<R> = when (this) {
    is ParserResult.Failure -> this
    is ParserResult.Success -> ParserResult.Success(f(result))
}
