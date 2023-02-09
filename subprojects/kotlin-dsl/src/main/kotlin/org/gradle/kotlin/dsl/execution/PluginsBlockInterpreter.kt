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

import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens.BLOCK_COMMENT
import org.jetbrains.kotlin.lexer.KtTokens.CLOSING_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.DOC_COMMENT
import org.jetbrains.kotlin.lexer.KtTokens.DOT
import org.jetbrains.kotlin.lexer.KtTokens.EOL_COMMENT
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
import org.jetbrains.kotlin.lexer.KtTokens.WHITESPACES


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

    var previousTokenType: IElementType? = null

    val pluginRequests = mutableListOf<ResidualProgram.PluginRequestSpec>()
    var seenStatementSeparator = false
    var isInfix = false
    var pluginId: String? = null
    var version: String? = null
    var apply: Boolean? = null
    var state = InterpreterState.START

    fun newPluginRequest(): InterpreterState {
        require(pluginId != null) { "newPluginRequest() invoked without a pluginId set" }
        pluginRequests.add(ResidualProgram.PluginRequestSpec(pluginId!!, version, apply ?: true))
        seenStatementSeparator = false
        pluginId = null
        version = null
        apply = null
        state = InterpreterState.ID
        return state
    }

    while (tokenType != null) {
        when (tokenType) {

            BLOCK_COMMENT -> Unit
            DOC_COMMENT -> Unit
            EOL_COMMENT -> seenStatementSeparator = true
            in WHITESPACES -> if (tokenSequence.contains('\n')) seenStatementSeparator = true

            else -> {
                when (state) {

                    InterpreterState.START -> state = when (tokenType) {
                        LBRACE -> InterpreterState.ID
                        else -> return expecting("{")
                    }

                    InterpreterState.ID -> state = when (tokenType) {
                        RBRACE -> InterpreterState.END
                        SEMICOLON -> InterpreterState.ID.also { if (pluginId != null) newPluginRequest() }
                        IDENTIFIER -> {
                            if (pluginId != null && seenStatementSeparator) newPluginRequest()
                            when (tokenText) {
                                "id" -> InterpreterState.ID_OPEN_CALL
                                "kotlin" -> InterpreterState.KOTLIN_ID_OPEN_CALL
                                else -> return expecting("id or kotlin")
                            }
                        }

                        else -> return expecting("plugin spec")
                    }

                    InterpreterState.AFTER_ID -> state = when (tokenType) {
                        DOT -> {
                            if (previousTokenType == DOT) return expecting("version or apply")
                            InterpreterState.AFTER_ID
                        }

                        SEMICOLON -> newPluginRequest()
                        RBRACE -> InterpreterState.END.also { newPluginRequest() }
                        IDENTIFIER -> {
                            when (tokenText) {
                                in listOf("version", "apply") -> {
                                    isInfix = previousTokenType != DOT
                                    when (tokenText) {
                                        "version" -> InterpreterState.VERSION_OPEN_CALL
                                        "apply" -> InterpreterState.APPLY_OPEN_CALL
                                        else -> return expecting("version or apply")
                                    }
                                }

                                in listOf("id", "kotlin") -> {
                                    if (!seenStatementSeparator) return expecting("<statement separator>")
                                    if (pluginId != null) newPluginRequest()
                                    when (tokenText) {
                                        "id" -> InterpreterState.ID_OPEN_CALL
                                        "kotlin" -> InterpreterState.KOTLIN_ID_OPEN_CALL
                                        else -> return expecting("id or kotlin")
                                    }
                                }

                                else -> return expecting("version or apply")
                            }
                        }

                        else -> return expecting(";")
                    }

                    InterpreterState.ID_OPEN_CALL -> state = when (tokenType) {
                        LPAR -> InterpreterState.PLUGIN_ID_START
                        else -> return expecting("(")
                    }

                    InterpreterState.PLUGIN_ID_START -> state = when (tokenType) {
                        OPEN_QUOTE -> InterpreterState.PLUGIN_ID_STRING
                        else -> return expecting("<plugin id string>")
                    }

                    InterpreterState.PLUGIN_ID_STRING -> state = when (tokenType) {
                        REGULAR_STRING_PART -> InterpreterState.PLUGIN_ID_END.also { pluginId = tokenText }
                        else -> return expecting("<plugin id string>")
                    }

                    InterpreterState.PLUGIN_ID_END -> state = when (tokenType) {
                        CLOSING_QUOTE -> InterpreterState.ID_CLOSE_CALL
                        else -> return expecting("<plugin id string>")
                    }

                    InterpreterState.ID_CLOSE_CALL -> state = when (tokenType) {
                        RPAR -> InterpreterState.AFTER_ID
                        else -> return expecting(")")
                    }

                    InterpreterState.KOTLIN_ID_OPEN_CALL -> state = when (tokenType) {
                        LPAR -> InterpreterState.KOTLIN_ID_START
                        else -> return expecting("(")
                    }

                    InterpreterState.KOTLIN_ID_START -> state = when (tokenType) {
                        OPEN_QUOTE -> InterpreterState.KOTLIN_ID_STRING
                        else -> return expecting("<kotlin plugin module string>")
                    }

                    InterpreterState.KOTLIN_ID_STRING -> state = when (tokenType) {
                        REGULAR_STRING_PART -> InterpreterState.KOTLIN_ID_END.also { pluginId = "org.jetbrains.kotlin.$tokenText" }
                        else -> return expecting("<kotlin plugin module string>")
                    }

                    InterpreterState.KOTLIN_ID_END -> state = when (tokenType) {
                        CLOSING_QUOTE -> InterpreterState.ID_CLOSE_CALL
                        else -> return expecting("<kotlin plugin module string>")
                    }

                    InterpreterState.APPLY_OPEN_CALL -> state = when (tokenType) {
                        LPAR -> InterpreterState.APPLY_BOOL_CALL
                        FALSE_KEYWORD -> {
                            if (!isInfix) return expecting("(")
                            InterpreterState.AFTER_ID.also { apply = false }
                        }

                        TRUE_KEYWORD -> {
                            if (!isInfix) return expecting("(")
                            InterpreterState.AFTER_ID.also { apply = true }
                        }

                        else -> return expecting("(")
                    }

                    InterpreterState.APPLY_BOOL_CALL -> state = when (tokenType) {
                        FALSE_KEYWORD -> InterpreterState.APPLY_CLOSE_CALL.also { apply = false }
                        TRUE_KEYWORD -> InterpreterState.APPLY_CLOSE_CALL.also { apply = true }
                        else -> return expecting("true or false")
                    }

                    InterpreterState.APPLY_CLOSE_CALL -> state = when (tokenType) {
                        RPAR -> InterpreterState.AFTER_ID
                        else -> return expecting(")")
                    }

                    InterpreterState.VERSION_OPEN_CALL -> state = when (tokenType) {
                        LPAR -> InterpreterState.VERSION_START
                        OPEN_QUOTE -> {
                            if (!isInfix) return expecting("(")
                            InterpreterState.VERSION_INFIX_STRING
                        }

                        else -> return expecting("(")
                    }

                    InterpreterState.VERSION_START -> state = when (tokenType) {
                        OPEN_QUOTE -> InterpreterState.VERSION_STRING
                        else -> return expecting("<version string>")
                    }

                    InterpreterState.VERSION_STRING -> state = when (tokenType) {
                        REGULAR_STRING_PART -> InterpreterState.VERSION_END.also { version = tokenText }
                        else -> return expecting("<version string>")
                    }

                    InterpreterState.VERSION_END -> state = when (tokenType) {
                        CLOSING_QUOTE -> InterpreterState.VERSION_CLOSE_CALL
                        else -> return expecting("<version string>")
                    }

                    InterpreterState.VERSION_CLOSE_CALL -> state = when (tokenType) {
                        RPAR -> InterpreterState.AFTER_ID
                        else -> return expecting(")")
                    }

                    InterpreterState.VERSION_INFIX_STRING -> state = when (tokenType) {
                        REGULAR_STRING_PART -> InterpreterState.VERSION_INFIX_END.also { version = tokenText }
                        else -> return expecting("<version string>")
                    }

                    InterpreterState.VERSION_INFIX_END -> state = when (tokenType) {
                        CLOSING_QUOTE -> InterpreterState.AFTER_ID.also { isInfix = false }
                        else -> return expecting("<version string>")
                    }

                    InterpreterState.END -> return unknown("Unexpected token '$tokenText'")
                }
                previousTokenType = tokenType
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
    KOTLIN_ID_OPEN_CALL,
    KOTLIN_ID_START,
    KOTLIN_ID_STRING,
    KOTLIN_ID_END,
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
