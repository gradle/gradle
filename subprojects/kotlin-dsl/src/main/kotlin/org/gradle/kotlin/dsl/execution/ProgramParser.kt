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

    fun parse(source: ProgramSource, kind: ProgramKind, target: ProgramTarget): Packaged<Program> = try {
        programFor(source, kind, target)
    } catch (unexpectedBlock: UnexpectedBlock) {
        handleUnexpectedBlock(unexpectedBlock, source.text, source.path)
    }

    private
    fun programFor(source: ProgramSource, kind: ProgramKind, target: ProgramTarget): Packaged<Program> {

        val topLevelBlockIds = TopLevelBlockId.topLevelBlockIdFor(target)

        return lex(source.text, *topLevelBlockIds).map { (comments, topLevelBlocks) ->

            checkForSingleBlocksOf(topLevelBlockIds, topLevelBlocks)

            checkForTopLevelBlockOrder(topLevelBlocks)

            val sourceWithoutComments =
                source.map { it.erase(comments) }

            val buildscriptFragment =
                topLevelBlocks
                    .singleSectionOf(TopLevelBlockId.buildscriptIdFor(target))
                    ?.let { sourceWithoutComments.fragment(it) }

            val pluginManagementFragment =
                topLevelBlocks
                    .singleSectionOf(TopLevelBlockId.pluginManagement)
                    ?.let { sourceWithoutComments.fragment(it) }

            val pluginsFragment =
                topLevelBlocks
                    .takeIf { topLevelBlockIds.contains(TopLevelBlockId.plugins) && kind == ProgramKind.TopLevel }
                    ?.singleSectionOf(TopLevelBlockId.plugins)
                    ?.let { sourceWithoutComments.fragment(it) }

            val buildscript =
                buildscriptFragment?.takeIf { it.isNotBlank() }?.let(Program::Buildscript)

            val pluginManagement =
                pluginManagementFragment?.takeIf { it.isNotBlank() }?.let(Program::PluginManagement)

            val plugins =
                pluginsFragment?.takeIf { it.isNotBlank() }?.let(Program::Plugins)

            val stage1Components =
                listOfNotNull<Program.Stage1>(pluginManagement, buildscript, plugins)

            val stage1 = when {
                stage1Components.isEmpty() -> null
                stage1Components.size == 1 -> stage1Components.first()
                else -> Program.Stage1Sequence(pluginManagement, buildscript, plugins)
            }

            val remainingSource =
                sourceWithoutComments.map {
                    it.erase(
                        listOfNotNull(
                            buildscriptFragment?.range,
                            pluginManagementFragment?.range,
                            pluginsFragment?.range
                        )
                    )
                }

            val stage2 = remainingSource
                .takeIf { it.text.isNotBlank() }
                ?.let(Program::Script)

            stage1?.let { s1 ->
                stage2?.let { s2 ->
                    Program.Staged(s1, s2)
                } ?: s1
            }
                ?: stage2
                ?: Program.Empty
        }
    }

    private
    fun checkForSingleBlocksOf(
        topLevelBlockIds: Array<TopLevelBlockId>,
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
        source.text.subSequence(section.block.first + 1, section.block.last).isNotBlank()
}


internal
fun checkForTopLevelBlockOrder(
    topLevelBlocks: List<TopLevelBlock>
) {
    val pluginManagementBlock = topLevelBlocks.find { it.identifier == TopLevelBlockId.pluginManagement } ?: return
    val firstTopLevelBlock = topLevelBlocks.first()
    if (firstTopLevelBlock.identifier != TopLevelBlockId.pluginManagement) {
        throw UnexpectedBlockOrder(firstTopLevelBlock.identifier, firstTopLevelBlock.range, pluginManagementBlock.identifier)
    }
}


private
fun List<TopLevelBlock>.singleSectionOf(topLevelBlockId: TopLevelBlockId) =
    singleOrNull { it.identifier == topLevelBlockId }?.section


private
fun handleUnexpectedBlock(unexpectedBlock: UnexpectedBlock, script: String, scriptPath: String): Nothing {
    val (line, column) = script.lineAndColumnFromRange(unexpectedBlock.location)
    val message = compilerMessageFor(scriptPath, line, column, unexpectedBlock.message!!)
    throw IllegalStateException(message, unexpectedBlock)
}
