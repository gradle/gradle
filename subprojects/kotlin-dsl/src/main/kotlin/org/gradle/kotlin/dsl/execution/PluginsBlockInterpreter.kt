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
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.lexer.KtTokens.LBRACE
import org.jetbrains.kotlin.lexer.KtTokens.LPAR
import org.jetbrains.kotlin.lexer.KtTokens.OPEN_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.RBRACE
import org.jetbrains.kotlin.lexer.KtTokens.REGULAR_STRING_PART
import org.jetbrains.kotlin.lexer.KtTokens.RPAR
import org.jetbrains.kotlin.lexer.KtTokens.SEMICOLON
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
    var state = InterpreterState.START
    fun newStatement() {
        pluginId = ""
        state = InterpreterState.ID
    }
    while (tokenType != null) {
        when (tokenType) {
            in WHITE_SPACE_OR_COMMENT_BIT_SET -> {
                // detect newline separators between statements
                if (state == InterpreterState.NEXT_ID && tokenSequence.contains('\n')) {
                    newStatement()
                }
            }

            else -> {
                when (state) {
                    InterpreterState.START -> {
                        if (tokenType != LBRACE) {
                            return expecting("{")
                        }
                        state = InterpreterState.ID
                    }

                    InterpreterState.ID -> {
                        state = when {
                            tokenType == RBRACE -> InterpreterState.END
                            tokenType == SEMICOLON -> InterpreterState.ID
                            tokenType != IDENTIFIER || tokenText != "id" -> return expecting("id")
                            else -> InterpreterState.OPEN_CALL
                        }
                    }

                    InterpreterState.NEXT_ID -> {
                        when (tokenType) {
                            RBRACE -> state = InterpreterState.END
                            SEMICOLON -> newStatement()
                            else -> return expecting(";")
                        }
                    }

                    InterpreterState.OPEN_CALL -> {
                        if (tokenType != LPAR) {
                            return expecting("(")
                        }
                        state = InterpreterState.PLUGIN_ID_START
                    }

                    InterpreterState.PLUGIN_ID_START -> {
                        if (tokenType != OPEN_QUOTE) {
                            return expecting("<plugin id string>")
                        }
                        state = InterpreterState.PLUGIN_ID_STRING
                    }

                    InterpreterState.PLUGIN_ID_STRING -> {
                        when {
                            tokenType != REGULAR_STRING_PART -> {
                                return expecting("<plugin id string>")
                            }

                            else -> {
                                pluginId = tokenText
                                state = InterpreterState.PLUGIN_ID_END
                            }
                        }
                    }

                    InterpreterState.PLUGIN_ID_END -> {
                        if (tokenType != CLOSING_QUOTE) {
                            return expecting("<plugin id string>")
                        }
                        pluginRequests.add(ResidualProgram.PluginRequestSpec(pluginId))
                        state = InterpreterState.CLOSE_CALL
                    }

                    InterpreterState.CLOSE_CALL -> {
                        if (tokenType != RPAR) {
                            return expecting(")")
                        }
                        state = InterpreterState.NEXT_ID
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
    OPEN_CALL,
    PLUGIN_ID_START,
    PLUGIN_ID_STRING,
    PLUGIN_ID_END,
    CLOSE_CALL,
    NEXT_ID,
    END,
}


private
fun KotlinLexer.expecting(expected: String): PluginsBlockInterpretation =
    unknown("Expecting $expected, got '$tokenText'")


private
fun KotlinLexer.unknown(reason: String): PluginsBlockInterpretation.Dynamic =
    PluginsBlockInterpretation.Dynamic(reason)
