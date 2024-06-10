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

package org.gradle.internal.declarativedsl.dom.data

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument


fun <D, C : NodeData<D>> C.collectToNodeMap(document: DeclarativeDocument): Map<DeclarativeDocument.DocumentNode, D> =
    buildMap {
        fun visit(node: DeclarativeDocument.DocumentNode) {
            put(node, data(node))
            when (node) {
                is DeclarativeDocument.DocumentNode.ElementNode -> node.content.forEach(::visit)
                is DeclarativeDocument.DocumentNode.ErrorNode,
                is DeclarativeDocument.DocumentNode.PropertyNode -> Unit
            }
        }
        document.content.forEach(::visit)
    }


fun <D, C> C.collectToMap(document: DeclarativeDocument): Map<DeclarativeDocument.Node, D> where C : NodeData<out D>, C : ValueData<out D> =
    buildMap {
        fun visit(node: DeclarativeDocument.Node) {
            put(node, data(node))
            when (node) {
                is DeclarativeDocument.DocumentNode.ElementNode -> {
                    node.elementValues.forEach(::visit)
                    node.content.forEach(::visit)
                }

                is DeclarativeDocument.DocumentNode.PropertyNode -> {
                    visit(node.value)
                }

                is DeclarativeDocument.ValueNode.ValueFactoryNode -> {
                    node.values.forEach(::visit)
                }

                is DeclarativeDocument.DocumentNode.ErrorNode,
                is DeclarativeDocument.ValueNode.LiteralValueNode -> Unit
            }
        }
        document.content.forEach(::visit)
    }
