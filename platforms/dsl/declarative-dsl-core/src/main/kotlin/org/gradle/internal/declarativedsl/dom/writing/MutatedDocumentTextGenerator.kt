/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.dom.writing

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.mutation.common.NewDocumentNodes
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTree.ChildTag
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTree.TextTreeNode
import org.jetbrains.kotlin.it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap


internal
class MutatedDocumentTextGenerator {
    private
    val canonicalCodeGenerator = CanonicalCodeGenerator()

    @Suppress("NestedBlockDepth")
    fun generateText(
        tree: TextPreservingTree,
        mapNames: (ownerTag: ChildTag, name: String) -> String = { _, name -> name },
        removeNodeIf: (ownerTag: ChildTag) -> Boolean = { false },
        insertNodesBefore: (ownerTag: ChildTag) -> NewDocumentNodes = { NewDocumentNodes.empty },
        insertNodesAfter: (ownerTag: ChildTag) -> NewDocumentNodes = { NewDocumentNodes.empty },
        replaceValue: (ownerTag: ChildTag.ValueNodeChildTag) -> DeclarativeDocument.ValueNode? = { null },
    ): String {
        val textBuilder = TrackingCodeTextBuilder()

        fun visit(parentTag: ChildTag?, ownerTag: ChildTag, textTreeNode: TextTreeNode, isTopLevel: Boolean) {
            insertNodesBefore(ownerTag).takeIf { it.nodes.isNotEmpty() }?.let { nodesBefore ->
                insertSyntheticNodes(textBuilder, nodesBefore, isTopLevel, textTreeNode.lineRange.first, needsSeparationBefore = false, needsSeparationAfter = !removeNodeIf(ownerTag))
            }
            if (!removeNodeIf(ownerTag)) {
                when (ownerTag) {
                    ChildTag.Name -> textBuilder.append(mapNames(checkNotNull(parentTag), tree.originalText.slice(textTreeNode.range)), textTreeNode.lineRange.last)
                    ChildTag.Indentation -> textBuilder.appendIndent(tree.originalText.slice(textTreeNode.range))
                    ChildTag.UnstructuredText, ChildTag.LineBreak -> textBuilder.append(tree.originalText.substring(textTreeNode.range), textTreeNode.lineRange.last)

                    is ChildTag.AssignmentRhs,
                    is ChildTag.BlockElement,
                    is ChildTag.CallArgument -> {
                        (ownerTag as? ChildTag.ValueNodeChildTag)?.let(replaceValue)?.let { insertSyntheticValue(textBuilder, it, textTreeNode.lineRange.last) }
                            ?: run {
                                for (child in textTreeNode.children) {
                                    visit(ownerTag, child.childTag, child.subTreeNode, false)
                                }
                            }
                    }
                }
            }
            insertNodesAfter(ownerTag).takeIf(NewDocumentNodes::isNotEmpty)?.let { nodesAfter ->
                val needsSeparationAfter = isTopLevel && nodesAfter.nodes.last() is ElementNode
                insertSyntheticNodes(
                    textBuilder, nodesAfter, isTopLevel, textTreeNode.lineRange.last,
                    needsSeparationBefore = !removeNodeIf(ownerTag),
                    needsSeparationAfter
                )
            }
        }

        tree.root.children.forEach {
            visit(null, it.childTag, it.subTreeNode, isTopLevel = true)
        }

        return fixLineBreaksAndBlankLines(
            tree.originalText,
            textBuilder.text(),
            textBuilder.originalLinesMappedToResultLines,
            textBuilder.flexibleLineBreaksBeforeLines
        )
    }

    private
    fun insertSyntheticNodes(
        textBuilder: TrackingCodeTextBuilder,
        syntheticNodes: NewDocumentNodes,
        isTopLevel: Boolean,
        endAtOriginalLine: Int,
        needsSeparationBefore: Boolean,
        needsSeparationAfter: Boolean,
    ) {
        fun lineBreakIndent() {
            if (isTopLevel) {
                textBuilder.appendFlexibleLinebreak()
            } else {
                textBuilder.append("\n", endAtOriginalLine)
            }
            textBuilder.appendIndent(" ".repeat(textBuilder.indentLength))
        }

        val indents: (Int) -> String = indentationProviderForSyntheticCode(textBuilder.indentLength)

        if (needsSeparationBefore) {
            lineBreakIndent()
        }
        textBuilder.append(
            canonicalCodeGenerator.generateCode(syntheticNodes, indents, isTopLevel),
            endAtOriginalLine
        )
        if (needsSeparationAfter) {
            lineBreakIndent()
        }
    }

    private
    fun insertSyntheticValue(
        textBuilder: TrackingCodeTextBuilder,
        syntheticValue: DeclarativeDocument.ValueNode,
        endAtOriginalLine: Int
    ) {
        textBuilder.append(canonicalCodeGenerator.valueNodeString(syntheticValue), endAtOriginalLine)
    }

    private
    fun fixLineBreaksAndBlankLines(
        originalText: String,
        builtText: String,
        lineMapping: Map<Int, Int>,
        flexibleLineBreaksBeforeLines: Set<Int>
    ): String {
        /** For each of the result lines, tells which original lines contributed to it */
        val reverseLineMapping = lineMapping.entries.groupBy({ it.value }, valueTransform = { it.key })
        val originalLines = originalText.lines()

        return buildString {
            var blankLinesCount = 0
            var hasFlexibleLineBreak = false

            builtText.lines().forEachIndexed { index, line ->
                if (index in flexibleLineBreaksBeforeLines) {
                    hasFlexibleLineBreak = true
                }
                if (line.isBlank()) {
                    // If the result line is blank, we keep it only if it corresponds to a blank line (or more than one) in the original text;
                    // otherwise, it corresponds to some original content that we intend to "kill", so no need to keep the blank line either:
                    val isLineToPreserve = reverseLineMapping[index]?.let { originalLineIndices -> originalLineIndices.all { originalLines[it].isBlank() } } != false

                    if (isLineToPreserve) {
                        blankLinesCount++
                    }
                } else {
                    if (hasFlexibleLineBreak && blankLinesCount != 1) {
                        blankLinesCount = 1
                    }
                    append("\n".repeat(blankLinesCount))
                    blankLinesCount = 0
                    hasFlexibleLineBreak = false
                    appendLine(line)
                }
            }
        }
    }

    private
    fun indentationProviderForSyntheticCode(outerIndentLength: Int): (Int) -> String {
        // This is a workaround: when synthetic code is generated, we put it after an existing indentation,
        // so no need to generate another indentation right at the start.
        var isFirstIndent = true
        return {
            if (isFirstIndent.also { isFirstIndent = false })
                ""
            else
                " ".repeat(outerIndentLength) + "    ".repeat(it)
        }
    }
}


private
class TrackingCodeTextBuilder {
    // region state
    private
    val builder = StringBuilder()

    private
    val lineMapping = Int2IntOpenHashMap()

    private
    var lastIndentLength = 0

    private
    var currentResultLine = 0

    private
    var flexibleLineBreaks = HashSet<Int>()

    // endregion
    // region API

    /**
     * Tells to which line an original line is mapped to in the resulting text.
     * Assumes that original lines are not split into multiple lines.
     */
    val originalLinesMappedToResultLines: Map<Int, Int>
        get() = lineMapping

    val flexibleLineBreaksBeforeLines: Set<Int>
        get() = flexibleLineBreaks

    fun appendIndent(string: String) {
        require(string.isBlank() && string.indexOf('\n') == -1)
        builder.append(string)
        lastIndentLength = string.length
    }

    fun text() = builder.toString()

    fun append(string: String, endAtOriginalLine: Int) {
        builder.append(string)
        if ('\n' !in string) {
            lineMapping.put(endAtOriginalLine - 1, currentResultLine)
        }
        currentResultLine += string.count { it == '\n' }
    }

    fun appendFlexibleLinebreak() {
        builder.append('\n')
        currentResultLine++
        flexibleLineBreaks.add(currentResultLine)
    }

    val indentLength: Int
        get() = lastIndentLength
    //endregion
}
