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
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTree.ChildTag
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTree.SubTreeData
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTree.TextTreeNode
import org.gradle.internal.declarativedsl.language.SourceData


/**
 * Provides access to the text-level structure of a document
 */
internal
class TextPreservingTree internal constructor(
    internal val originalText: String,
    internal val root: TextTreeNode
) {
    internal
    class TextTreeNode(
        val range: IntRange,
        val lineRange: IntRange,
        val children: List<SubTreeData>
    )

    internal
    class SubTreeData(val childTag: ChildTag, val subTreeNode: TextTreeNode)

    sealed interface ChildTag {
        sealed interface ValueNodeChildTag : ChildTag {
            val valueNode: DeclarativeDocument.ValueNode
        }

        data object Name : ChildTag
        data class AssignmentRhs(override val valueNode: DeclarativeDocument.ValueNode) : ValueNodeChildTag
        data class CallArgument(val index: Int, override val valueNode: DeclarativeDocument.ValueNode) : ValueNodeChildTag
        data class BlockElement(val index: Int, val documentNode: DeclarativeDocument.DocumentNode) : ChildTag

        // These do not represent parts of the DOM but serve for preserving the original formatting and comments
        data object Indentation : ChildTag
        data object LineBreak : ChildTag
        data object UnstructuredText : ChildTag
    }
}


internal
class TextPreservingTreeBuilder {
    fun build(document: DeclarativeDocument): TextPreservingTree =
        with(TreeBuildingContext(document)) {
            buildTextPreservingTree(document)
        }

    private
    class TreeBuildingContext(
        document: DeclarativeDocument
    ) {
        val originalText: String = document.sourceData.text()
    }

    private
    fun TreeBuildingContext.buildTextPreservingTree(document: DeclarativeDocument) = TextPreservingTree(
        originalText,
        TextTreeNode(
            document.sourceData.indexRange,
            document.sourceData.lineRange,
            completeIntervalsWithTextRanges(
                document.sourceData.indexRange,
                document.sourceData.lineRange,
                document.content.mapIndexed { index, it ->
                    SubTreeData(
                        ChildTag.BlockElement(index, it),
                        nodeForDocumentNode(it)
                    )
                }
            )
        )
    )

    private
    fun TreeBuildingContext.nodeForDocumentNode(node: DeclarativeDocument.DocumentNode): TextTreeNode {
        val meaningfulSubtrees = when (node) {
            is DeclarativeDocument.DocumentNode.ElementNode -> {
                listOf(SubTreeData(ChildTag.Name, TextTreeNode(node.sourceData.nameRange(node.name), node.lines, emptyList()))) +
                    node.elementValues.mapIndexed { index, it -> SubTreeData(ChildTag.CallArgument(index, it), nodeForValueNode(it)) } +
                    node.content.mapIndexed { index, it -> SubTreeData(ChildTag.BlockElement(index, it), nodeForDocumentNode(it)) }
            }

            is DeclarativeDocument.DocumentNode.PropertyNode -> {
                listOf(
                    SubTreeData(ChildTag.Name, TextTreeNode(node.sourceData.nameRange(node.name), node.lines, emptyList())),
                    SubTreeData(ChildTag.AssignmentRhs(node.value), nodeForValueNode(node.value))
                )
            }

            is DeclarativeDocument.DocumentNode.ErrorNode -> listOf(SubTreeData(ChildTag.UnstructuredText, TextTreeNode(node.range, node.lines, emptyList())))
        }

        return TextTreeNode(node.range, node.lines, completeIntervalsWithTextRanges(node.range, node.lines, meaningfulSubtrees))
    }

    private
    fun TreeBuildingContext.nodeForValueNode(node: DeclarativeDocument.ValueNode): TextTreeNode {
        val meaningfulSubtrees = when (node) {
            is DeclarativeDocument.ValueNode.ValueFactoryNode -> listOf(
                SubTreeData(ChildTag.Name, TextTreeNode(node.sourceData.nameRange(node.factoryName), node.sourceData.nameLines(node.factoryName), emptyList())),
            ) + node.values.mapIndexed { index, it -> SubTreeData(ChildTag.CallArgument(index, it), nodeForValueNode(it)) }

            is DeclarativeDocument.ValueNode.NamedReferenceNode,
            is DeclarativeDocument.ValueNode.LiteralValueNode -> listOf(SubTreeData(ChildTag.UnstructuredText, TextTreeNode(node.range, node.lines, emptyList())))
        }

        return TextTreeNode(node.range, node.sourceData.lineRange, completeIntervalsWithTextRanges(node.range, node.lines, meaningfulSubtrees))
    }

    /**
     * Given a list of [intervals] covering the [range], completes the list with new sub-intervals
     * covering the indices in the range which were not covered.
     *
     * New intervals are produced by taking the non-covered intervals of the [range] and then:
     * - splitting each into lines,
     * - for each of the lines:
     *   - inserting the line's blank prefix with [ChildTag.Indentation]
     *     (except that the first line would not have it, as we assume its indentation has already been handled)
     *   - inserting the non-blank reaminder of the line as [ChildTag.UnstructuredText]
     *   - inserting a single-character [ChildTag.LineBreak] if this is not the last line.
     */
    private
    fun TreeBuildingContext.completeIntervalsWithTextRanges(
        range: IntRange,
        lineRange: IntRange,
        intervals: List<SubTreeData>
    ): List<SubTreeData> = buildList {
        /** The first of the range's indices that we have not covered yet. */
        var unhandledSuffixStartIndex = range.first
        var unhandledSuffixStartLine = lineRange.first

        intervals.forEach { interval ->
            if (interval.subTreeNode.range.first > unhandledSuffixStartIndex) {
                addAll(lineWithIndentationAndLineBreak(unhandledSuffixStartIndex, interval.subTreeNode.range.first, unhandledSuffixStartLine))
                // no need to update the indices here, as we are going to insert the original interval right below
            }
            add(interval)
            unhandledSuffixStartLine = interval.subTreeNode.lineRange.last
            unhandledSuffixStartIndex = interval.subTreeNode.range.last + 1
        }
        if (unhandledSuffixStartIndex <= range.last) {
            addAll(
                lineWithIndentationAndLineBreak(
                    unhandledSuffixStartIndex,
                    range.last + 1,
                    unhandledSuffixStartLine
                )
            )
        }
    }

    private
    fun TreeBuildingContext.lineWithIndentationAndLineBreak(
        fromIndex: Int,
        untilIndexExclusive: Int,
        fromLine: Int,
    ): List<SubTreeData> = buildList {
        var unhandledFrom = fromIndex
        var unhandledLineFrom = fromLine

        val lines = originalText.substring(fromIndex until untilIndexExclusive).split('\n')
        lines.forEachIndexed { lineIndex, line ->
            var lineRemainderLength = line.length
            if (lineIndex != 0) {
                val indentation = line.takeWhile(Char::isWhitespace)
                add(
                    SubTreeData(
                        ChildTag.Indentation,
                        TextTreeNode(
                            unhandledFrom until (unhandledFrom + indentation.length), unhandledLineFrom..unhandledLineFrom, emptyList()
                        )
                    )
                )
                unhandledFrom += indentation.length
                lineRemainderLength -= indentation.length
            }
            add(
                SubTreeData(
                    ChildTag.UnstructuredText,
                    TextTreeNode(
                        unhandledFrom until (unhandledFrom + lineRemainderLength),
                        unhandledLineFrom..unhandledLineFrom,
                        emptyList()
                    )
                )
            )
            unhandledFrom += lineRemainderLength
            if (lineIndex != lines.lastIndex) {
                add(
                    SubTreeData(
                        ChildTag.LineBreak,
                        TextTreeNode(
                            unhandledFrom..unhandledFrom,
                            unhandledLineFrom..(unhandledLineFrom + 1),
                            emptyList()
                        )
                    )
                )
                unhandledFrom += 1
                unhandledLineFrom += 1
            }
        }
    }

    // TODO: this is a workaround, we should get the name location from the original tree instead
    private
    fun SourceData.nameRange(name: String): IntRange {
        val rangeStart = indexRange.first + text().indexOf(name)
            .also { require(it != -1) { "the name of the document element '$name' is not found in the text '${text()}'" } }
        return rangeStart until (rangeStart + name.length)
    }

    private
    fun SourceData.nameLines(name: String) = (lineRange.first + text().substring(text().indexOf(name)).count { it == '\n' }).let { it..it }

    private
    val DeclarativeDocument.ValueNode.range: IntRange
        get() = sourceData.indexRange

    private
    val DeclarativeDocument.ValueNode.lines: IntRange
        get() = sourceData.lineRange

    private
    val DeclarativeDocument.DocumentNode.range: IntRange
        get() = sourceData.indexRange

    private
    val DeclarativeDocument.DocumentNode.lines: IntRange
        get() = sourceData.lineRange
}
