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


class MapBasedDocumentDataContainer<DNode, DProperty : DNode, DElement : DNode, DError : DNode, DValue, DValueFactory : DValue, DLiteral : DValue>(
    private val propertyData: Map<DeclarativeDocument.DocumentNode.PropertyNode, DProperty>,
    private val elementData: Map<DeclarativeDocument.DocumentNode.ElementNode, DElement>,
    private val errorData: Map<DeclarativeDocument.DocumentNode.ErrorNode, DError>,
    private val valueFactoryData: Map<DeclarativeDocument.ValueNode.ValueFactoryNode, DValueFactory>,
    private val literalData: Map<DeclarativeDocument.ValueNode.LiteralValueNode, DLiteral>
) : DocumentDataContainer<DNode, DElement, DProperty, DError>, ValueDataContainer<DValue, DValueFactory, DLiteral> {
    override fun data(node: DeclarativeDocument.DocumentNode.ElementNode): DElement = elementData.getValue(node)
    override fun data(node: DeclarativeDocument.DocumentNode.PropertyNode): DProperty = propertyData.getValue(node)
    override fun data(node: DeclarativeDocument.DocumentNode.ErrorNode): DError = errorData.getValue(node)
    override fun data(value: DeclarativeDocument.ValueNode.ValueFactoryNode): DValueFactory = valueFactoryData.getValue(value)
    override fun data(value: DeclarativeDocument.ValueNode.LiteralValueNode): DLiteral = literalData.getValue(value)
}
