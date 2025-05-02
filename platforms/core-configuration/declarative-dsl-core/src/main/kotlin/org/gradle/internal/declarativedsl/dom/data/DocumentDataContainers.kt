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


interface NodeDataContainer<out DNode, out DElement : DNode, out DProperty : DNode, out DError : DNode> {
    fun data(node: DeclarativeDocument.DocumentNode): DNode = when (node) {
        is DeclarativeDocument.DocumentNode.ElementNode -> data(node)
        is DeclarativeDocument.DocumentNode.PropertyNode -> data(node)
        is DeclarativeDocument.DocumentNode.ErrorNode -> data(node)
    }
    fun data(node: DeclarativeDocument.DocumentNode.ElementNode): DElement
    fun data(node: DeclarativeDocument.DocumentNode.PropertyNode): DProperty
    fun data(node: DeclarativeDocument.DocumentNode.ErrorNode): DError
}


typealias NodeData<DNode> = NodeDataContainer<DNode, DNode, DNode, DNode>


interface ValueDataContainer<out DValue, out DValueFactory : DValue, out DLiteral : DValue, out DNamedReference : DValue> {
    fun data(node: DeclarativeDocument.ValueNode): DValue = when (node) {
        is DeclarativeDocument.ValueNode.ValueFactoryNode -> data(node)
        is DeclarativeDocument.ValueNode.LiteralValueNode -> data(node)
        is DeclarativeDocument.ValueNode.NamedReferenceNode -> data(node)
    }

    fun data(node: DeclarativeDocument.ValueNode.ValueFactoryNode): DValueFactory
    fun data(node: DeclarativeDocument.ValueNode.LiteralValueNode): DLiteral
    fun data(node: DeclarativeDocument.ValueNode.NamedReferenceNode): DNamedReference
}


typealias ValueData<DValue> = ValueDataContainer<DValue, DValue, DValue, DValue>


fun <C, D> C.data(node: DeclarativeDocument.Node): D where C : NodeData<out D>, C : ValueData<out D> = when (node) {
    is DeclarativeDocument.DocumentNode -> data(node)
    is DeclarativeDocument.ValueNode -> data(node)
}
