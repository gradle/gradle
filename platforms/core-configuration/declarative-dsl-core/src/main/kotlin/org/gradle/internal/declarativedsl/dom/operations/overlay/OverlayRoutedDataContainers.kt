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

package org.gradle.internal.declarativedsl.dom.operations.overlay

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode
import org.gradle.internal.declarativedsl.dom.data.NodeDataContainer
import org.gradle.internal.declarativedsl.dom.data.ValueDataContainer
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.FromOverlay
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.FromUnderlay
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.MergedElements


internal
class OverlayRoutedNodeDataContainer<DNode, DElement : DNode, DProperty : DNode, DError : DNode>(
    private val overlayOriginContainer: OverlayOriginContainer,
    private val underlay: NodeDataContainer<DNode, DElement, DProperty, DError>,
    private val overlay: NodeDataContainer<DNode, DElement, DProperty, DError>
) : NodeDataContainer<DNode, DElement, DProperty, DError> {
    override fun data(node: DeclarativeDocument.DocumentNode.ElementNode): DElement = when (val from = overlayOriginContainer.data(node)) {
        is FromUnderlay -> underlay.data(node)
        is FromOverlay -> overlay.data(node)
        is MergedElements -> overlay.data(from.overlayElement)
    }

    override fun data(node: DeclarativeDocument.DocumentNode.PropertyNode): DProperty = when (val from = overlayOriginContainer.data(node)) {
        is FromUnderlay -> underlay.data(node)
        is FromOverlay -> overlay.data(node)
        is OverlayNodeOrigin.ShadowedProperty -> overlay.data(from.overlayProperty)
    }

    override fun data(node: DeclarativeDocument.DocumentNode.ErrorNode): DError = when (overlayOriginContainer.data(node)) {
        is FromUnderlay -> underlay.data(node)
        is FromOverlay -> overlay.data(node)
    }
}


internal
class OverlayRoutedValueDataContainer<DValue, DValueFactory : DValue, DLiteral : DValue>(
    private val overlayOriginContainer: OverlayOriginContainer,
    private val underlay: ValueDataContainer<DValue, DValueFactory, DLiteral>,
    private val overlay: ValueDataContainer<DValue, DValueFactory, DLiteral>
) : ValueDataContainer<DValue, DValueFactory, DLiteral> {
    override fun data(value: ValueNode.ValueFactoryNode): DValueFactory = when (overlayOriginContainer.data(value)) {
        is FromOverlay -> overlay.data(value)
        is FromUnderlay -> underlay.data(value)
    }

    override fun data(value: ValueNode.LiteralValueNode): DLiteral = when (overlayOriginContainer.data(value)) {
        is FromOverlay -> overlay.data(value)
        is FromUnderlay -> underlay.data(value)
    }
}
