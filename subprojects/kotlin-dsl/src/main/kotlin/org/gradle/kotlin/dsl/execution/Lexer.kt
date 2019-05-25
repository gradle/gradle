/*
 * Copyright 2018 the original author or authors.
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
import org.jetbrains.kotlin.lexer.KtTokens.COMMENTS
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.lexer.KtTokens.LBRACE
import org.jetbrains.kotlin.lexer.KtTokens.RBRACE
import org.jetbrains.kotlin.lexer.KtTokens.WHITE_SPACE


internal
class UnexpectedBlock(val identifier: String, val location: IntRange) : RuntimeException("Unexpected block found.")


private
enum class State {
    SearchingTopLevelBlock,
    SearchingBlockStart,
    SearchingBlockEnd
}


/**
 * Returns the comments and [top-level blocks][topLevelBlocks] found in the given [script].
 */
internal
fun lex(script: String, vararg topLevelBlocks: String): Pair<List<IntRange>, List<TopLevelBlock>> {

    val comments = mutableListOf<IntRange>()
    val tokens = mutableListOf<TopLevelBlock>()

    var state = State.SearchingTopLevelBlock
    var inTopLevelBlock: String? = null
    var blockIdentifier: IntRange? = null
    var blockStart: Int? = null

    var depth = 0

    fun reset() {
        state = State.SearchingTopLevelBlock
        inTopLevelBlock = null
        blockIdentifier = null
        blockStart = null
    }

    fun KotlinLexer.matchTopLevelIdentifier(): Boolean {
        if (depth == 0) {
            val identifier = tokenText
            for (topLevelBlock in topLevelBlocks) {
                if (topLevelBlock == identifier) {
                    state = State.SearchingBlockStart
                    inTopLevelBlock = topLevelBlock
                    blockIdentifier = tokenStart..(tokenEnd - 1)
                    return true
                }
            }
        }
        return false
    }

    KotlinLexer().apply {
        start(script)
        while (tokenType != null) {

            when (tokenType) {

                WHITE_SPACE -> {
                    // ignore
                }

                in COMMENTS -> {
                    comments.add(
                        tokenStart..(tokenEnd - 1)
                    )
                }

                else -> {

                    when (state) {

                        State.SearchingTopLevelBlock -> {

                            when (tokenType) {
                                IDENTIFIER -> matchTopLevelIdentifier()
                                LBRACE -> depth += 1
                                RBRACE -> depth -= 1
                            }
                        }

                        State.SearchingBlockStart -> {

                            when (tokenType) {
                                IDENTIFIER -> if (!matchTopLevelIdentifier()) reset()
                                LBRACE -> {
                                    depth += 1
                                    state = State.SearchingBlockEnd
                                    blockStart = tokenStart
                                }
                                else -> reset()
                            }
                        }

                        State.SearchingBlockEnd -> {

                            when (tokenType) {
                                LBRACE -> depth += 1
                                RBRACE -> {
                                    depth -= 1
                                    if (depth == 0) {
                                        tokens.add(
                                            topLevelBlock(
                                                inTopLevelBlock!!,
                                                blockIdentifier!!,
                                                blockStart!!..tokenStart
                                            )
                                        )
                                        reset()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            advance()
        }
    }
    return comments to tokens
}


internal
fun topLevelBlock(identifier: String, identifierRange: IntRange, blockRange: IntRange) =
    TopLevelBlock(identifier, ScriptSection(identifierRange, blockRange))


internal
data class TopLevelBlock(val identifier: String, val section: ScriptSection) {
    val range: IntRange
        get() = section.wholeRange
}


internal
fun List<TopLevelBlock>.singleBlockSectionOrNull(): ScriptSection? =
    when (size) {
        0 -> null
        1 -> get(0).section
        else -> {
            val unexpectedBlock = get(1)
            throw UnexpectedBlock(unexpectedBlock.identifier, unexpectedBlock.range)
        }
    }
