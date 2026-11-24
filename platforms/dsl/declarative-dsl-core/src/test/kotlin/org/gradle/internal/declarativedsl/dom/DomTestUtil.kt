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

package org.gradle.internal.declarativedsl.dom

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode


object DomTestUtil {
    fun printDomByTraversal(
        document: DeclarativeDocument,
        nodeFormatter: (DocumentNode) -> String,
        valueFormatter: (ValueNode) -> String
    ): String = buildString {
        fun visitValue(valueNode: ValueNode, indent: Int) {
            append(" ".repeat(indent * 4))
            append(valueFormatter(valueNode))
        }

        fun visitDocumentNode(documentNode: DocumentNode, indent: Int) {
            append(" ".repeat(indent * 4))
            append(nodeFormatter(documentNode))
            when (documentNode) {
                is DocumentNode.ElementNode -> {
                    documentNode.elementValues.forEach { value ->
                        appendLine()
                        visitValue(value, indent + 1)
                    }
                    if (documentNode.content.isNotEmpty()) {
                        appendLine()
                        documentNode.content.forEach { visitDocumentNode(it, indent + 1) }
                    } else {
                        appendLine()
                    }
                }

                is DocumentNode.ErrorNode -> appendLine()
                is DocumentNode.PropertyNode -> {
                    appendLine()
                    visitValue(documentNode.value, indent + 1)
                    appendLine()
                }
            }
        }
        document.content.forEach { visitDocumentNode(it, 0) }
    }
}
