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

import org.gradle.kotlin.dsl.support.compilerMessageFor

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens


internal
object ProgramParser {

    fun parse(source: ProgramSource, kind: ProgramKind, target: ProgramTarget): Program = try {
        programFor(source, kind, target)
    } catch (unexpectedBlock: UnexpectedBlock) {
        handleUnexpectedBlock(unexpectedBlock, source.text, source.path)
    }

    private
    fun programFor(source: ProgramSource, kind: ProgramKind, target: ProgramTarget): Program {

        val sourceWithoutComments =
            source.map { it.erase(commentsOf(it.text)) }

        val buildscriptFragment =
            topLevelFragmentFrom(sourceWithoutComments, if (target == ProgramTarget.Gradle) "initscript" else "buildscript")

        val pluginsFragment =
            if (kind == ProgramKind.TopLevel && target == ProgramTarget.Project) topLevelFragmentFrom(sourceWithoutComments, "plugins")
            else null

        val buildscript =
            buildscriptFragment?.takeIf { it.isNotBlank() }?.let(Program::Buildscript)

        val plugins =
            pluginsFragment?.takeIf { it.isNotBlank() }?.let(Program::Plugins)

        val stage1 =
            buildscript?.let { bs ->
                plugins?.let { ps ->
                    Program.Stage1Sequence(bs, ps)
                } ?: bs
            } ?: plugins

        val remainingSource =
            sourceWithoutComments.map {
                it.erase(
                    listOfNotNull(
                        buildscriptFragment?.section?.wholeRange,
                        pluginsFragment?.section?.wholeRange))
            }

        val stage2 = remainingSource
            .takeIf { it.text.isNotBlank() }
            ?.let(Program::Script)

        stage1?.let { s1 ->
            return stage2?.let { s2 ->
                Program.Staged(s1, s2)
            } ?: s1
        }

        return stage2
            ?: Program.Empty
    }

    private
    fun topLevelFragmentFrom(source: ProgramSource, identifier: String): ProgramSourceFragment? =
        extractTopLevelBlock(source.text, identifier)
            ?.let { source.fragment(it) }

    private
    fun ProgramSourceFragment.isNotBlank() =
        source.text.subSequence(section.block.start + 1, section.block.endInclusive).isNotBlank()
}


private
fun commentsOf(script: String): List<IntRange> =
    KotlinLexer().run {
        val comments = mutableListOf<IntRange>()
        start(script)
        while (tokenType != null) {
            if (tokenType in KtTokens.COMMENTS) {
                comments.add(tokenStart..(tokenEnd - 1))
            }
            advance()
        }
        comments
    }


internal
fun handleUnexpectedBlock(unexpectedBlock: UnexpectedBlock, script: String, scriptPath: String): Nothing {
    val (line, column) = script.lineAndColumnFromRange(unexpectedBlock.location)
    val message = compilerMessageFor(scriptPath, line, column, unexpectedBlockMessage(unexpectedBlock))
    throw IllegalStateException(message, unexpectedBlock)
}


private
fun unexpectedBlockMessage(block: UnexpectedBlock) =
    "Unexpected `${block.identifier}` block found. Only one `${block.identifier}` block is allowed per script."
