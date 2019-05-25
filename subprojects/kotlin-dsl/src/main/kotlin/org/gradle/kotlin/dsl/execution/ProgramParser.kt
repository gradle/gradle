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

import com.google.common.annotations.VisibleForTesting


@VisibleForTesting
object ProgramParser {

    fun parse(source: ProgramSource, kind: ProgramKind, target: ProgramTarget): Program = try {
        programFor(source, kind, target)
    } catch (unexpectedBlock: UnexpectedBlock) {
        handleUnexpectedBlock(unexpectedBlock, source.text, source.path)
    }

    private
    fun programFor(source: ProgramSource, kind: ProgramKind, target: ProgramTarget): Program {

        val topLevelBlockIds =
            when (target) {
                ProgramTarget.Project -> arrayOf("buildscript", "plugins")
                ProgramTarget.Settings -> arrayOf("buildscript", "pluginManagement")
                ProgramTarget.Gradle -> arrayOf("initscript")
            }

        val (comments, topLevelBlocks) = lex(source.text, *topLevelBlockIds)

        checkForSingleBlocksOf(topLevelBlockIds, topLevelBlocks)

        val sourceWithoutComments =
            source.map { it.erase(comments) }

        val buildscriptFragment =
            topLevelBlocks
                .singleSectionOf(topLevelBlockIds[0])
                ?.let { sourceWithoutComments.fragment(it) }

        val pluginsFragment =
            topLevelBlocks
                .takeIf { target == ProgramTarget.Project && kind == ProgramKind.TopLevel }
                ?.singleSectionOf("plugins")
                ?.let { sourceWithoutComments.fragment(it) }

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
                        buildscriptFragment?.range,
                        pluginsFragment?.range))
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
    fun checkForSingleBlocksOf(
        topLevelBlockIds: Array<String>,
        topLevelBlocks: List<TopLevelBlock>
    ) {
        topLevelBlockIds.forEach { id ->
            topLevelBlocks
                .filter { it.identifier === id }
                .singleBlockSectionOrNull()
        }
    }

    private
    fun ProgramSourceFragment.isNotBlank() =
        source.text.subSequence(section.block.start + 1, section.block.endInclusive).isNotBlank()
}


private
fun List<TopLevelBlock>.singleSectionOf(topLevelBlockId: String) =
    singleOrNull { it.identifier == topLevelBlockId }?.section


private
fun handleUnexpectedBlock(unexpectedBlock: UnexpectedBlock, script: String, scriptPath: String): Nothing {
    val (line, column) = script.lineAndColumnFromRange(unexpectedBlock.location)
    val message = compilerMessageFor(scriptPath, line, column, unexpectedBlockMessage(unexpectedBlock))
    throw IllegalStateException(message, unexpectedBlock)
}


private
fun unexpectedBlockMessage(block: UnexpectedBlock) =
    "Unexpected `${block.identifier}` block found. Only one `${block.identifier}` block is allowed per script."
