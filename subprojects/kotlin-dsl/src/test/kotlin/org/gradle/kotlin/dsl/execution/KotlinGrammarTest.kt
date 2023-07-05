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
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.junit.Test


class KotlinGrammarTest {

    @Test
    fun `will not parse empty annotation`() {
        kotlinGrammar.annotation.failToConsumeFrom("""@""")
        kotlinGrammar.annotation.failToConsumeFrom("""@ \n """)
    }

    @Test
    fun `can parse single annotation`() {
        assertAnnotationConsumed("""@Suppress("unused_variable")""")
        assertAnnotationConsumed("""@Suppress ( "unused_variable" )""")

        assertAnnotationConsumed("""@DisableCachingByDefault(because="Not worth caching")""")
        assertAnnotationConsumed("""@DisableCachingByDefault ( because = "Not worth caching" )""")

        assertAnnotationConsumed("""@Deprecated(message="Use rem(other) instead",replaceWith=ReplaceWith("rem(other)"))""")
        assertAnnotationConsumed("""@Deprecated ( message = "Use rem(other) instead" , replaceWith = ReplaceWith ( "rem(other)" ) )""")

        assertAnnotationConsumed("""@Throws(IOException::class)""")
        assertAnnotationConsumed("""@Throws ( IOException :: class )""")

        assertAnnotationConsumed("""@Throws(exceptionClasses=arrayOf(IOException::class,IllegalArgumentException::class))""")
        assertAnnotationConsumed("""@Throws ( exceptionClasses = arrayOf ( IOException :: class , IllegalArgumentException :: class ) )""")

        assertAnnotationConsumed("""@Throws(exceptionClasses=[IOException::class,IllegalArgumentException::class])""")
        assertAnnotationConsumed("""@Throws ( exceptionClasses = [ IOException :: class , IllegalArgumentException :: class ] )""")

        assertAnnotationConsumed("""@receiver:Fancy""")
        assertAnnotationConsumed("""@receiver : Fancy""")

        assertAnnotationConsumed("""@field:Ann""")
        assertAnnotationConsumed("""@field : Ann""")

        assertAnnotationConsumed("""@get:Ann""")
        assertAnnotationConsumed("""@get : Ann""")

        assertAnnotationConsumed("""@param:Ann""")
        assertAnnotationConsumed("""@param : Ann""")

        assertAnnotationConsumed("""@Ann(1,1.toByte())""")
        assertAnnotationConsumed("""@Ann ( 1 , 1 . toByte ( ) )""")
    }

    @Test
    fun `can parse multi annotation`() {
        assertAnnotationConsumed("""
            @set:[
                Inject
                VisibleForTesting
            ]""".trimIndent())

        assertAnnotationConsumed("""
            @Deprecated (
                "Use something instead." ,
                ReplaceWith ( "method(param)" )
            )""".trimIndent())

        assertAnnotationConsumed("""
            @get:
                VisibleForTesting""".trimIndent())
    }

    private
    fun assertAnnotationConsumed(input: String) {
        assertParserConsumes(kotlinGrammar.annotation, input)
    }

    @Test
    fun `can parse file annotation`() {
        assertFileAnnotationConsumed("""@file:Suppress("UnstableApiUsage")""")
        assertFileAnnotationConsumed("""@file : JvmName ( "Foo" )""")

        assertFileAnnotationConsumed("""@file:[SuppressWarnings Incubating Suppress("unused","nothing_to_inline")]""")
        assertFileAnnotationConsumed("""@file : [ SuppressWarnings Incubating Suppress ( "unused" , "nothing_to_inline" ) ]""")
    }


    @Test
    fun `can parse multi file annotation`() {
        assertFileAnnotationConsumed("""
            @file
            :
            [
                SuppressWarnings
                Incubating
                Suppress
                (
                    "unused"
                    ,
                    "nothing_to_inline"
                    )
            ]""".trimIndent())
    }

    private
    fun assertFileAnnotationConsumed(input: String) {
        assertParserConsumes(kotlinGrammar.fileAnnotation, input)
    }

    private
    fun assertParserConsumes(parser: Parser<Any>, input: String) {
        val whitespace = "   "
        assertThat(
            parser.consumeFrom(input + whitespace + "something more"),
            equalTo(input)
        )

        assertThat(
            parser.consumeFrom(input + "\nsomething"),
            equalTo(input)
        )
    }
}


private
fun <T> Parser<T>.consumeFrom(input: String): String {
    val kotlinLexer = KotlinLexer()
    val result = kotlinLexer.let { lexer ->
        lexer.start(input)
        this(lexer)
    }
    if (result is ParserResult.Failure) {
        throw Exception(result.reason)
    }
    return input.substring(0, kotlinLexer.currentPosition.offset)
}


private
fun <T> Parser<T>.failToConsumeFrom(input: String) {
    val kotlinLexer = KotlinLexer()
    val result = kotlinLexer.let { lexer ->
        lexer.start(input)
        this(lexer)
    }
    if (result !is ParserResult.Failure) {
        throw Exception("Parsing did not fail, as expected")
    }
}
