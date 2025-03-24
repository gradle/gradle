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
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode.PropertyAugmentation.None
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode.PropertyAugmentation.Plus
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode
import org.gradle.internal.declarativedsl.dom.mutation.common.NewDocumentNodes


class CanonicalDocumentTextGenerator {
    fun generateText(
        document: DeclarativeDocument
    ): String = CanonicalCodeGenerator().generateCode(NewDocumentNodes(document.content.toList()), "    "::repeat, true)
}


internal
class CanonicalCodeGenerator {
    fun valueNodeString(node: ValueNode): String = when (node) {
        is ValueNode.LiteralValueNode -> when (val value = node.value) {
            is String -> "\"$value\""
            else -> value.toString()
        }

        is ValueNode.ValueFactoryNode -> "${node.factoryName}(${node.values.joinToString { valueNodeString(it) }})"

        is ValueNode.NamedReferenceNode -> node.referenceName
    }

    fun generateCode(
        nodes: NewDocumentNodes,
        indentProvider: (Int) -> String,
        isTopLevel: Boolean,
    ) = buildString {
        fun visitNode(node: DocumentNode, depth: Int = 0) {
            fun indent() = indentProvider(depth)

            when (node) {
                is DocumentNode.PropertyNode -> {
                    val operator = when (node.augmentation) {
                        None -> "="
                        Plus -> "+="
                    }
                    append("${indent()}${node.name} $operator ${valueNodeString(node.value)}")
                }

                is DocumentNode.ElementNode -> {
                    val representation = nodes.representationFlags.data(node)
                    append("${indent()}${node.name}")
                    if (node.elementValues.isNotEmpty() || node.content.isEmpty() && !representation.forceEmptyBlock) {
                        append("(${node.elementValues.joinToString { valueNodeString(it) }})")
                    }
                    if (node.content.isNotEmpty()) {
                        appendLine(" {")
                        node.content.forEach {
                            visitNode(it, depth + 1)
                            appendLine()
                        }
                        append("${indent()}}")
                    } else {
                        if (representation.forceEmptyBlock) {
                            append(" { }")
                        }
                    }
                }

                is DocumentNode.ErrorNode -> Unit
            }
        }

        nodes.nodes.forEachIndexed { index, it ->
            if (index > 0) {
                appendLine()
                if (isTopLevel && (nodes.nodes[index - 1] is DocumentNode.ElementNode || it is DocumentNode.ElementNode)) {
                    appendLine()
                }
            }
            visitNode(it)
        }
    }
}
