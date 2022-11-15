/*
 * Copyright 2022 the original author or authors.
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
import org.jetbrains.kotlin.lexer.KtTokens.CLOSING_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.DOT
import org.jetbrains.kotlin.lexer.KtTokens.FALSE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.lexer.KtTokens.LBRACE
import org.jetbrains.kotlin.lexer.KtTokens.LPAR
import org.jetbrains.kotlin.lexer.KtTokens.OPEN_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.RBRACE
import org.jetbrains.kotlin.lexer.KtTokens.REGULAR_STRING_PART
import org.jetbrains.kotlin.lexer.KtTokens.RPAR
import org.jetbrains.kotlin.lexer.KtTokens.SEMICOLON
import org.jetbrains.kotlin.lexer.KtTokens.TRUE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET


internal
sealed class PluginsBlockInterpretation {

    /**
     * The `plugins` block applies exactly the given list of [plugins].
     */
    data class Static(val plugins: List<ResidualProgram.PluginRequestSpec>) : PluginsBlockInterpretation()

    /**
     * The `plugins` block cannot be interpreted because of [reason].
     */
    data class Dynamic(val reason: String) : PluginsBlockInterpretation()
}


internal
fun interpret(program: Program.Plugins): PluginsBlockInterpretation = KotlinLexer().run {
    start(program.fragment.blockString)

    val pluginRequests = mutableListOf<ResidualProgram.PluginRequestSpec>()
    var pluginId = ""
    var version: String? = null
    var apply: Boolean? = null
    var state = InterpreterState.START
    fun newStatement() {
        pluginRequests.add(ResidualProgram.PluginRequestSpec(pluginId, version, apply ?: true))
        pluginId = ""
        version = null
        apply = null
        state = InterpreterState.ID
    }
    while (tokenType != null) {
        when (tokenType) {
            in WHITE_SPACE_OR_COMMENT_BIT_SET -> {
                // detect newline separators between statements
                if (state == InterpreterState.AFTER_ID && tokenSequence.contains('\n')) {
                    newStatement()
                }
            }

            else -> {
                when (state) {
                    InterpreterState.START -> when (tokenType) {
                        LBRACE -> state = InterpreterState.ID
                        else -> expecting("{")
                    }

                    InterpreterState.ID -> when {
                        tokenType == RBRACE -> state = InterpreterState.END
                        tokenType == SEMICOLON -> state = InterpreterState.ID
                        tokenType == IDENTIFIER && tokenText == "id" -> state = InterpreterState.ID_OPEN_CALL
                        else -> return expecting("id")
                    }

                    InterpreterState.AFTER_ID -> {
                        if (tokenType == DOT) {
                            advance()
                        }
                        when {
                            tokenType == IDENTIFIER && tokenText == "version" -> state = InterpreterState.VERSION_OPEN_CALL
                            tokenType == IDENTIFIER && tokenText == "apply" -> state = InterpreterState.APPLY_OPEN_CALL
                            tokenType == SEMICOLON -> newStatement()
                            tokenType == RBRACE -> {
                                newStatement()
                                state = InterpreterState.END
                            }

                            else -> return expecting(";")
                        }
                    }

                    InterpreterState.ID_OPEN_CALL -> when (tokenType) {
                        LPAR -> state = InterpreterState.PLUGIN_ID_START
                        else -> expecting("(")
                    }

                    InterpreterState.PLUGIN_ID_START -> when (tokenType) {
                        OPEN_QUOTE -> state = InterpreterState.PLUGIN_ID_STRING
                        else -> expecting("<plugin id string>")
                    }

                    InterpreterState.PLUGIN_ID_STRING -> when {
                        tokenType != REGULAR_STRING_PART -> return expecting("<plugin id string>")
                        else -> {
                            pluginId = tokenText
                            state = InterpreterState.PLUGIN_ID_END
                        }
                    }

                    InterpreterState.PLUGIN_ID_END -> when (tokenType) {
                        CLOSING_QUOTE -> state = InterpreterState.ID_CLOSE_CALL
                        else -> return expecting("<plugin id string>")
                    }

                    InterpreterState.ID_CLOSE_CALL -> when {
                        tokenType != RPAR -> return expecting(")")
                        else -> state = InterpreterState.AFTER_ID
                    }

                    InterpreterState.APPLY_OPEN_CALL -> when (tokenType) {
                        FALSE_KEYWORD -> {
                            apply = false
                            state = InterpreterState.AFTER_ID
                        }

                        TRUE_KEYWORD -> {
                            apply = true
                            state = InterpreterState.AFTER_ID
                        }

                        LPAR -> state = InterpreterState.APPLY_BOOL_CALL
                        else -> return expecting("(")
                    }

                    InterpreterState.APPLY_BOOL_CALL -> when (tokenType) {
                        FALSE_KEYWORD -> {
                            apply = false
                            state = InterpreterState.APPLY_CLOSE_CALL
                        }

                        TRUE_KEYWORD -> {
                            apply = true
                            state = InterpreterState.APPLY_CLOSE_CALL
                        }

                        else -> return expecting("true or false")
                    }

                    InterpreterState.APPLY_CLOSE_CALL -> when (tokenType) {
                        RPAR -> state = InterpreterState.AFTER_ID
                        else -> return expecting(")")
                    }

                    InterpreterState.VERSION_OPEN_CALL -> when (tokenType) {
                        OPEN_QUOTE -> state = InterpreterState.VERSION_INFIX_STRING
                        LPAR -> state = InterpreterState.VERSION_START
                        else -> return expecting("(")
                    }

                    InterpreterState.VERSION_START -> when (tokenType) {
                        OPEN_QUOTE -> state = InterpreterState.VERSION_STRING
                        else -> expecting("<version string>")
                    }

                    InterpreterState.VERSION_STRING -> when (tokenType) {
                        REGULAR_STRING_PART -> {
                            version = tokenText
                            state = InterpreterState.VERSION_END
                        }

                        else -> return expecting("<version string>")
                    }

                    InterpreterState.VERSION_END -> when (tokenType) {
                        CLOSING_QUOTE -> state = InterpreterState.VERSION_CLOSE_CALL
                        else -> expecting("<version string>")
                    }

                    InterpreterState.VERSION_CLOSE_CALL -> when (tokenType) {
                        RPAR -> state = InterpreterState.AFTER_ID
                        else -> return expecting(")")
                    }

                    InterpreterState.VERSION_INFIX_STRING -> when (tokenType) {
                        REGULAR_STRING_PART -> {
                            version = tokenText
                            state = InterpreterState.VERSION_INFIX_END
                        }

                        else -> return expecting("<version string>")
                    }

                    InterpreterState.VERSION_INFIX_END -> when (tokenType) {
                        CLOSING_QUOTE -> state = InterpreterState.AFTER_ID
                        else -> expecting("<version string>")
                    }

                    InterpreterState.END -> {
                        return unknown("Unexpected token '$tokenText'")
                    }
                }
            }
        }
        advance()
    }
    if (state != InterpreterState.END) {
        return unknown("Incomplete `plugins` block")
    }

    PluginsBlockInterpretation.Static(pluginRequests)
}


private
enum class InterpreterState {
    START,
    ID,
    ID_OPEN_CALL,
    PLUGIN_ID_START,
    PLUGIN_ID_STRING,
    PLUGIN_ID_END,
    ID_CLOSE_CALL,
    AFTER_ID,
    APPLY_OPEN_CALL,
    APPLY_BOOL_CALL,
    APPLY_CLOSE_CALL,
    VERSION_OPEN_CALL,
    VERSION_START,
    VERSION_STRING,
    VERSION_END,
    VERSION_INFIX_STRING,
    VERSION_INFIX_END,
    VERSION_CLOSE_CALL,
    END,
}


private
fun KotlinLexer.expecting(expected: String): PluginsBlockInterpretation =
    unknown("Expecting $expected, got '$tokenText'")


private
fun KotlinLexer.unknown(reason: String): PluginsBlockInterpretation.Dynamic =
    PluginsBlockInterpretation.Dynamic(reason)
