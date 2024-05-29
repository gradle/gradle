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


/**
 * In a document overlay, tells where a node comes from.
 */
sealed interface OverlayNodeOrigin {
    sealed interface OverlayPropertyOrigin : OverlayNodeOrigin
    sealed interface OverlayElementOrigin : OverlayNodeOrigin
    sealed interface OverlayErrorOrigin : OverlayNodeOrigin
    sealed interface OverlayValueOrigin : OverlayNodeOrigin
    sealed interface CopiedOrigin : OverlayPropertyOrigin, OverlayElementOrigin, OverlayErrorOrigin, OverlayValueOrigin

    /**
     * The node is copied as-is from the underlay. The [documentNode] is the node itself.
     */
    data class FromUnderlay(val documentNode: DeclarativeDocument.DocumentNode) : CopiedOrigin

    /**
     * The node is copied as-is from the overlay. The [documentNode] is the node itself.
     */
    data class FromOverlay(val documentNode: DeclarativeDocument.DocumentNode) : CopiedOrigin

    /**
     * The node is an element that is a combination of [underlayElement] and [overlayElement] with
     * their [DeclarativeDocument.DocumentNode.ElementNode.content]s merged together.
     */
    data class MergedElements(
        val underlayElement: DeclarativeDocument.DocumentNode.ElementNode,
        val overlayElement: DeclarativeDocument.DocumentNode.ElementNode
    ) : OverlayElementOrigin

    /**
     * The node is an [overlayProperty] that overrides ("shadows") an [underlayProperty].
     */
    data class ShadowedProperty(
        val underlayProperty: DeclarativeDocument.DocumentNode.PropertyNode,
        val overlayProperty: DeclarativeDocument.DocumentNode.PropertyNode
    ) : OverlayPropertyOrigin
}
