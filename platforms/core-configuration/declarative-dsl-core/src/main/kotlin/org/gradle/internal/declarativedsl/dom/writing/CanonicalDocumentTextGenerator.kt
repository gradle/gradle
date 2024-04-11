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
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode


class CanonicalDocumentTextGenerator {
    fun generateText(document: DeclarativeDocument): String =
        CanonicalCodeGenerator().generateCode(document.content.toList(), "    "::repeat, true)
}


internal
class CanonicalCodeGenerator {
    fun valueNodeString(node: ValueNode): String = when (node) {
        is ValueNode.LiteralValueNode -> when (val value = node.value) {
            is String -> "\"$value\""
            else -> value.toString()
        }

        is ValueNode.ValueFactoryNode -> "${node.factoryName}(${node.values.joinToString { valueNodeString(it) }})"
    }

    fun generateCode(
        nodes: List<DocumentNode>,
        indentProvider: (Int) -> String,
        isTopLevel: Boolean,
    ) = buildString {
        fun visitNode(node: DocumentNode, depth: Int = 0) {
            fun indent() = indentProvider(depth)

            when (node) {
                is DocumentNode.PropertyNode -> {
                    append("${indent()}${node.name} = ${valueNodeString(node.value)}")
                }

                is DocumentNode.ElementNode -> {
                    append("${indent()}${node.name}")
                    if (node.elementValues.isNotEmpty() || node.content.isEmpty()) {
                        append("(${node.elementValues.joinToString { valueNodeString(it) }})")
                    }
                    if (node.content.isNotEmpty()) {
                        appendLine(" {")
                        node.content.forEach {
                            visitNode(it, depth + 1)
                            appendLine()
                        }
                        append("${indent()}}")
                    }
                }

                is DocumentNode.ErrorNode -> Unit
            }
        }

        nodes.forEachIndexed { index, it ->
            if (index > 0) {
                appendLine()
                if (isTopLevel && (nodes[index - 1] is DocumentNode.ElementNode || it is DocumentNode.ElementNode)) {
                    appendLine()
                }
            }
            visitNode(it)
        }
    }
}
