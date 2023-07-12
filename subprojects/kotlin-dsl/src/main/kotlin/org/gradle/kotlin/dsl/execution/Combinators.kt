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
import org.jetbrains.kotlin.lexer.KtTokens.CLOSING_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.COMMENTS
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.lexer.KtTokens.LBRACE
import org.jetbrains.kotlin.lexer.KtTokens.LBRACKET
import org.jetbrains.kotlin.lexer.KtTokens.LPAR
import org.jetbrains.kotlin.lexer.KtTokens.OPEN_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.RBRACE
import org.jetbrains.kotlin.lexer.KtTokens.RBRACKET
import org.jetbrains.kotlin.lexer.KtTokens.REGULAR_STRING_PART
import org.jetbrains.kotlin.lexer.KtTokens.RPAR
import org.jetbrains.kotlin.lexer.KtTokens.SEMICOLON
import org.jetbrains.kotlin.lexer.KtTokens.WHITE_SPACE
import kotlin.reflect.KProperty


internal
typealias Parser<T> = KotlinLexer.() -> ParserResult<T>


@Suppress("unused")
private
val debugger: ParserDebugger = ParserDebugger()


@Suppress("unused")
private
class ParserDebugger {
    var level = 0

    fun <T> debug(name: String, parser: Parser<T>): Parser<T> = {
        val levelString = "\t".repeat(level)
        println("${levelString}Parsing with $name @ ${this.currentPosition.offset} ...")
        level++
        val result = parser()
        level--
        println("${levelString}Parsing with $name done @ ${this.currentPosition.offset}, ${if (result is ParserResult.Success) "successful (${result.result})" else "failed"}")
        result
    }
}


internal
class DebugDelegate<T>(val parserBuilder: () -> Parser<T>) {

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Parser<T> =
//        debugger.debug(property.name, parserBuilder())
        parserBuilder()
}


internal
fun <T> debug(parserBuilder: () -> Parser<T>) = DebugDelegate(parserBuilder)


internal
fun <T> debugReference(parserBuilder: () -> Parser<T>): Parser<T> {
    val a by debug(parserBuilder)
    return a
}


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
class ParserRef<T> {
    operator fun getValue(t: Any?, property: KProperty<*>): Parser<T> =
        ref

    operator fun setValue(t: Any?, property: KProperty<*>, value: Parser<T>) {
        parser = value
    }

    private
    var parser: Parser<T> = { error("Parser cannot be used while it's being constructed") }

    private
    val ref: Parser<T> = { parser() }
}


/**
 * Returns a shell parser, holding a mutable reference to another parser.
 * Can be combined with other parsers in the usual way and its delegate can be set at a later moment in time.
 *
 * It can also be used to create recursive parsers.
 * For example, a parser for a balanced parenthesized symbolic expression can be defined as:
 * ```kotlin
 * var parser by reference<String>()
 * parser = paren(parser) + token(IDENTIFIER) { tokenText }
 * ```
 *
 * This will successfully parse all of the below examples:
 * ```
 * ok
 * (ok)
 * ((ok))
 * (((ok)))
 * ```
 *
 * **WARNING** care must be taken to avoid infinite recursion, the delegate parser should always have
 * an input consuming parser at its front (e.g., in `p = paren(p) + p`, the `p` at the right would
 * cause an infinite recursion).
 */
internal
fun <T> reference(): ParserRef<T> = ParserRef()


internal
fun <T> zeroOrMore(parser: Parser<T>): Parser<List<T>> = {
    val lazyResult: MutableList<T>? = orMore(parser)
    ParserResult.Success(lazyResult ?: emptyList())
}


internal
fun <T> oneOrMore(parser: Parser<T>): Parser<List<T>> = {
    when (val lazyResult: MutableList<T>? = orMore(parser)) {
        null -> failure("Expecting at least one occurrence, but none found")
        else -> ParserResult.Success(lazyResult as List<T>)
    }
}


private
fun <T> KotlinLexer.orMore(parser: Parser<T>): MutableList<T>? {
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
    return lazyResult
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
fun <T> optional(parser: Parser<T>): Parser<T?> = {
    val mark = currentPosition
    when (val r = parser()) {
        is ParserResult.Failure -> {
            restore(mark)
            nullSuccess
        }

        is ParserResult.Success -> r
    }
}


internal
open class Combinator(
    val ignoresComments: Boolean,
    val ignoresNewline: Boolean
) {


    internal
    fun token(ktToken: KtToken): Parser<Unit> =
        token(ktToken) { }


    internal
    inline fun <T> token(token: KtToken, crossinline f: KotlinLexer.() -> T): Parser<T> {
        return {
            skipWhitespace()
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
    fun symbol(): Parser<String> {
        return {
            skipWhitespace()
            when (tokenType) {
                IDENTIFIER -> {
                    val result = tokenText
                    advance()
                    ParserResult.Success(result)
                }
                null -> {
                    failure("Expecting a symbol")
                }
                else -> {
                    failure("Expecting a symbol, but got a token of type '$tokenType' instead")
                }
            }
        }
    }


    internal
    fun symbol(s: String): Parser<Unit> {
        return {
            skipWhitespace()
            if (tokenType == IDENTIFIER && tokenText == s) {
                advance()
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
    val booleanLiteral =
        token(KtTokens.TRUE_KEYWORD) { true } + token(KtTokens.FALSE_KEYWORD) { false }


    /**
     * Can parse regular integers, unsigned integers, longs, unsigned longs, and HEX and BINARY representations as well.
     */
    internal
    val integerLiteral =
        token(KtTokens.INTEGER_LITERAL)


    internal
    val floatLiteral =
        token(KtTokens.FLOAT_LITERAL)


    internal
    val characterLiteral =
        token(KtTokens.CHARACTER_LITERAL)


    internal
    val stringLiteral =
        token(OPEN_QUOTE) *
            token(REGULAR_STRING_PART) { tokenText } *
            token(CLOSING_QUOTE)


    internal
    inline fun <T> paren(crossinline parser: Parser<T>): Parser<T> =
        token(LPAR) * parser * token(RPAR)


    internal
    inline fun <T> bracket(crossinline parser: Parser<T>): Parser<T> =
        token(LBRACKET) * parser * token(RBRACKET)


    internal
    inline fun <T> brace(crossinline parser: Parser<T>): Parser<T> =
        token(LBRACE) * parser * token(RBRACE)


    internal
    fun wsOrNewLine(): Parser<Unit> = {
        skipWhitespace(true)
        unitSuccess
    }


    internal
    fun notWhiteSpace(): Parser<Unit> = {
        when {
            tokenType == WHITE_SPACE -> {
                ParserResult.Failure("Unexpected whitespace")
            }

            tokenType in COMMENTS -> {
                ParserResult.Failure("Unexpected comments")
            }

            else -> {
                // no advancing, we don't want to consume whatever is there
                unitSuccess
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
                    if (ignoresComments) {
                        advance()
                    } else {
                        break
                    }
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
    fun KotlinLexer.skipWhitespace() {
        skipWhitespace(ignoresNewline)
    }

    private
    fun KotlinLexer.skipWhitespace(ignoreNewLine: Boolean) {
        while (tokenType != null) {
            when {
                tokenType == WHITE_SPACE && (ignoreNewLine || !hasNewLine()) -> {
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

    private
    fun KotlinLexer.hasNewLine() =
        '\n' in tokenSequence
}


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
