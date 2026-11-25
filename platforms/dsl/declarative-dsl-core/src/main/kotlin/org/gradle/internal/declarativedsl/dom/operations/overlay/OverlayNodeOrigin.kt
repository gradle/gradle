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
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode


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
     * The node is copied as-is from the underlay and represents the actual data. The [documentNode] is the node itself.
     */
    data class FromUnderlay(val documentNode: DocumentNode) : CopiedOrigin

    /**
     * The node is copied as-is from the overlay. The [documentNode] is the node itself.
     */
    data class FromOverlay(val documentNode: DocumentNode) : CopiedOrigin

    /**
     * The node is an element that is a combination of [underlayElement] and [overlayElement] with
     * their [DeclarativeDocument.DocumentNode.ElementNode.content]s merged together.
     */
    data class MergedElements(
        val underlayElement: ElementNode,
        val overlayElement: ElementNode
    ) : OverlayElementOrigin

    /**
     * The node is one of the set of property nodes assigning or augmenting the same property.
     *
     * The [shadowedPropertiesFromUnderlay] and [shadowedPropertiesFromOverlay] are property nodes shadowed by some reassignment.
     * The [effectivePropertiesFromUnderlay] and [effectivePropertiesFromOverlay] are property nodes that effectively contribute to the property value.
     *
     * If [shadowedPropertiesFromOverlay] set is not empty, then all underlay property must be in [shadowedPropertiesFromUnderlay], too.
     * If there are [effectivePropertiesFromUnderlay], then no nodes are in [shadowedPropertiesFromOverlay].
     *
     * For example:
     *
     * ```
     * // underlay:
     * foo += bar() // shadowed
     * foo = baz()  // effective
     *
     * // overlay:
     * foo += baq() // effective
     * ```
     *
     * or:
     *
     * ```
     * // underlay:
     * foo = foo()  // shadowed
     * foo += bar() // shadowed
     *
     * // overlay:
     * foo += baq() // shadowed
     * foo = qux()  // effective
     * ```
     */
    data class MergedProperties(
        val shadowedPropertiesFromUnderlay: Set<PropertyNode>,
        val effectivePropertiesFromUnderlay: Set<PropertyNode>,
        val shadowedPropertiesFromOverlay: Set<PropertyNode>,
        val effectivePropertiesFromOverlay: Set<PropertyNode>
    ) : OverlayPropertyOrigin
}
