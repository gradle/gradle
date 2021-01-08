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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.COMMENTS
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.lexer.KtTokens.LBRACE
import org.jetbrains.kotlin.lexer.KtTokens.PACKAGE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.RBRACE
import org.jetbrains.kotlin.lexer.KtTokens.WHITE_SPACE


internal
abstract class UnexpectedBlock(message: String) : RuntimeException(message) {
    abstract val location: IntRange
}


internal
class UnexpectedDuplicateBlock(val identifier: TopLevelBlockId, override val location: IntRange) :
    UnexpectedBlock("Unexpected `$identifier` block found. Only one `$identifier` block is allowed per script.")


internal
class UnexpectedBlockOrder(val identifier: TopLevelBlockId, override val location: IntRange, expectedFirstIdentifier: TopLevelBlockId) :
    UnexpectedBlock("Unexpected `$identifier` block found. `$identifier` can not appear before `$expectedFirstIdentifier`.")


data class Packaged<T>(
    val packageName: String?,
    val document: T
) {
    fun <U> map(transform: (T) -> U): Packaged<U> = Packaged(
        packageName,
        document = transform(document)
    )
}


internal
data class LexedScript(
    val comments: List<IntRange>,
    val topLevelBlocks: List<TopLevelBlock>
)


/**
 * Returns the comments and [top-level blocks][topLevelBlockIds] found in the given [script].
 */
internal
fun lex(script: String, vararg topLevelBlockIds: TopLevelBlockId): Packaged<LexedScript> {

    var packageName: String? = null
    val comments = mutableListOf<IntRange>()
    val topLevelBlocks = mutableListOf<TopLevelBlock>()

    var state = State.SearchingTopLevelBlock
    var inTopLevelBlock: TopLevelBlockId? = null
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
            for (topLevelBlock in topLevelBlockIds) {
                if (topLevelBlock.tokenText == identifier) {
                    state = State.SearchingBlockStart
                    inTopLevelBlock = topLevelBlock
                    blockIdentifier = tokenStart until tokenEnd
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
                        tokenStart until tokenEnd
                    )
                }

                else -> {

                    when (state) {

                        State.SearchingTopLevelBlock -> {

                            when (tokenType) {
                                PACKAGE_KEYWORD -> {
                                    advance()
                                    skipWhiteSpaceAndComments()
                                    packageName = parseQualifiedName()
                                }
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
                                        topLevelBlocks.add(
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
    return Packaged(
        packageName,
        LexedScript(comments, topLevelBlocks)
    )
}


private
enum class State {
    SearchingTopLevelBlock,
    SearchingBlockStart,
    SearchingBlockEnd
}


private
fun KotlinLexer.parseQualifiedName(): String =
    StringBuilder().run {
        while (tokenType == IDENTIFIER || tokenType == KtTokens.DOT) {
            append(tokenText)
            advance()
        }
        toString()
    }


private
fun KotlinLexer.skipWhiteSpaceAndComments() {
    while (tokenType in KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET) {
        advance()
    }
}


internal
fun topLevelBlock(identifier: TopLevelBlockId, identifierRange: IntRange, blockRange: IntRange) =
    TopLevelBlock(identifier, ScriptSection(identifierRange, blockRange))


internal
data class TopLevelBlock(val identifier: TopLevelBlockId, val section: ScriptSection) {
    val range: IntRange
        get() = section.wholeRange
}


@Suppress("EnumEntryName")
internal
enum class TopLevelBlockId {
    buildscript,
    plugins,
    pluginManagement,
    initscript;

    val tokenText: String
        get() = name

    companion object {

        fun topLevelBlockIdFor(target: ProgramTarget) = when (target) {
            ProgramTarget.Project -> arrayOf(buildscript, plugins)
            ProgramTarget.Settings -> arrayOf(buildscript, pluginManagement, plugins)
            ProgramTarget.Gradle -> arrayOf(initscript)
        }

        fun buildscriptIdFor(target: ProgramTarget) = when (target) {
            ProgramTarget.Gradle -> initscript
            ProgramTarget.Settings -> buildscript
            ProgramTarget.Project -> buildscript
        }
    }
}


internal
fun List<TopLevelBlock>.singleBlockSectionOrNull(): ScriptSection? =
    when (size) {
        0 -> null
        1 -> get(0).section
        else -> {
            val unexpectedBlock = get(1)
            throw UnexpectedDuplicateBlock(unexpectedBlock.identifier, unexpectedBlock.range)
        }
    }
