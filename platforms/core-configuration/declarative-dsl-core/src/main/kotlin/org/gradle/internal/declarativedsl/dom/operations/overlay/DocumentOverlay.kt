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

import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ErrorNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode
import org.gradle.internal.declarativedsl.dom.DefaultElementNode
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.DocumentNodeResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ErrorResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.PropertyResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ValueNodeResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ValueNodeResolution.LiteralValueResolved
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ValueNodeResolution.ValueFactoryResolution
import org.gradle.internal.declarativedsl.dom.data.NodeDataContainer
import org.gradle.internal.declarativedsl.dom.data.ValueData
import org.gradle.internal.declarativedsl.dom.data.ValueDataContainer
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.CopiedOrigin
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.FromOverlay
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.FromUnderlay
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.OverlayValueOrigin
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.language.SourceIdentifier


object DocumentOverlay {
    /**
     * Produces a new document by merging the sources, namely [underlay] and [overlay], in a way that:
     * * configuring elements are combined, as if the block content of [underlay] goes before the block content of [overlay]
     * * elements that create a new object are merged together; the elements from [underlay] will appear first;
     * * if a property appears in both sources, then the property in [overlay] overrides ("shadow") the matching property in [underlay]
     * * properties that appear only one of the sources get copied to the result together; the ones from [underlay] appear first.
     */
    fun overlayResolvedDocuments(
        underlay: DocumentWithResolution,
        overlay: DocumentWithResolution
    ): DocumentOverlayResult =
        overlayResolvedDocuments(underlay.document, underlay.resolutionContainer, overlay.document, overlay.resolutionContainer)

    /**
     * @see [overlayResolvedDocuments]
     */
    fun overlayResolvedDocuments(
        underlayDocument: DeclarativeDocument,
        underlayDocumentResolution: DocumentResolutionContainer,
        overlayDocument: DeclarativeDocument,
        overlayDocumentResolution: DocumentResolutionContainer
    ): DocumentOverlayResult {
        val context = documentOverlayContextByResolutionResults(underlayDocumentResolution, overlayDocumentResolution)
        val resultContent = context.mergeRecursively(underlayDocument.content, overlayDocument.content)
        val resultDocument = object : DeclarativeDocument {
            override val content: Collection<DeclarativeDocument.DocumentNode>
                get() = resultContent
            override val sourceIdentifier: SourceIdentifier
                get() = overlayDocument.sourceIdentifier
        }
        val overlayOriginContainer = context.overlayOriginContainer
        val overlayResolutionContainer = OverlayResolutionContainer(overlayOriginContainer, underlayDocumentResolution, overlayDocumentResolution)

        return DocumentOverlayResult(resultDocument, overlayOriginContainer, overlayResolutionContainer)
    }
}


/**
 * Represents the results of overlaying declarative documents.
 * * [document] is the resulting document content;
 * * [overlayNodeOriginContainer] tells where a node comes from – overlay, underlay, or combined.
 * * [overlayResolutionContainer] can be used to query [DocumentResolution] for the [document].
 *   Note: the original [DocumentResolutionContainer]s cannot be reused for this purpose.
 */
data class DocumentOverlayResult(
    val document: DeclarativeDocument,
    val overlayNodeOriginContainer: OverlayOriginContainer,
    val overlayResolutionContainer: DocumentResolutionContainer
)


interface OverlayOriginContainer :
    NodeDataContainer<OverlayNodeOrigin, OverlayNodeOrigin.OverlayElementOrigin, OverlayNodeOrigin.OverlayPropertyOrigin, OverlayNodeOrigin.OverlayErrorOrigin>,
    ValueData<OverlayValueOrigin>


private
class DocumentOverlayContext(
    private val underlayKeyMapper: MergeKeyMapper,
    private val overlayKeyMapper: MergeKeyMapper,
) {
    private
    val overlayPropertyOrigin: MutableMap<PropertyNode, OverlayNodeOrigin.OverlayPropertyOrigin> = mutableMapOf()

    private
    val overlayElementOrigin: MutableMap<ElementNode, OverlayNodeOrigin.OverlayElementOrigin> = mutableMapOf()

    private
    val overlayErrorOrigin: MutableMap<ErrorNode, OverlayNodeOrigin.OverlayErrorOrigin> = mutableMapOf()

    private
    val overlayValueOrigin: MutableMap<ValueNode, OverlayValueOrigin> = mutableMapOf()

    val overlayOriginContainer = object : OverlayOriginContainer {
        override fun data(node: ElementNode): OverlayNodeOrigin.OverlayElementOrigin = overlayElementOrigin.getValue(node)
        override fun data(node: PropertyNode): OverlayNodeOrigin.OverlayPropertyOrigin = overlayPropertyOrigin.getValue(node)
        override fun data(node: ErrorNode): OverlayNodeOrigin.OverlayErrorOrigin = overlayErrorOrigin.getValue(node)
        override fun data(value: ValueNode.ValueFactoryNode): OverlayValueOrigin = overlayValueOrigin.getValue(value)
        override fun data(value: ValueNode.LiteralValueNode): OverlayValueOrigin = overlayValueOrigin.getValue(value)
    }

    fun mergeRecursively(
        underlay: Collection<DeclarativeDocument.DocumentNode>,
        overlay: Collection<DeclarativeDocument.DocumentNode>
    ): Collection<DeclarativeDocument.DocumentNode> {
        val underlayNodesByMergeKey: MutableMap<MergeKey, List<DeclarativeDocument.DocumentNode>> =
            underlay.groupBy(underlayKeyMapper::mapNodeToMergeKey).toMutableMap()

        val overlayMergeKeys = overlay.mapTo(mutableSetOf(), overlayKeyMapper::mapNodeToMergeKey)

        val result = mutableListOf<DeclarativeDocument.DocumentNode>()

        // First, add the underlay items that have no matching merge key in the overlay:
        underlayNodesByMergeKey.entries.toList().forEach { (underlayMergeKey, underlayNodes) ->
            if (underlayMergeKey is MergeKey.CannotMerge || underlayMergeKey !in overlayMergeKeys) {
                underlayNodes.forEach {
                    result.add(it)
                    recordAsCopiedRecursively(it, ::FromUnderlay)
                }
                underlayNodesByMergeKey.remove(underlayMergeKey)
            }
        }

        // Then for each overlay item, merge it with the matching underlay items, if any:
        overlay.forEach { overlayItem ->
            val overlayMergeKey = overlayKeyMapper.mapNodeToMergeKey(overlayItem)

            when (overlayItem) {
                is PropertyNode -> {
                    // Either there is no underlay property or there is one, but we have ignored it in the underlay traversal
                    // above because it had a matching overlay merge key. In any case, even if there was a matching underlay
                    // property, we are no longer interested in it because the overlay property wins.
                    result.add(overlayItem)
                    recordValueOriginRecursively(overlayItem.value, FromOverlay(overlayItem))

                    // However, if there was a matching underlay property, we want to record that in the overlay origins:
                    underlayNodesByMergeKey[overlayMergeKey]?.also { underlayItems ->
                        val underlayProperty = underlayItems.last() as? PropertyNode ?: error("cannot merge a property $overlayItem with non-properties $underlayItems")
                        overlayPropertyOrigin[overlayItem] = OverlayNodeOrigin.ShadowedProperty(underlayProperty, overlayItem)
                    } ?: run {
                        overlayPropertyOrigin[overlayItem] = FromOverlay(overlayItem)
                    }
                }

                is ErrorNode -> {
                    // We want to keep the errors in the document anyway.
                    result.add(overlayItem)
                    recordAsCopiedRecursively(overlayItem, ::FromOverlay)
                }

                is ElementNode -> {
                    // We always record the arguments as copied from the overlay, for simplicity:
                    overlayItem.elementValues.forEach { recordValueOriginRecursively(it, FromOverlay(overlayItem)) }

                    // If the overlay has more than one item that match the merge key, we want only the first one to be
                    // actually merged, so we remove the key from the underlay map:
                    val underlayItems = underlayNodesByMergeKey.remove(overlayMergeKey)

                    if (underlayItems == null) {
                        result.add(overlayItem)
                        recordAsCopiedRecursively(overlayItem, ::FromOverlay)
                    } else {
                        val underlayElements = underlayItems.map { (it as? ElementNode) ?: error("cannot merge an element $overlayItem with non-elements $underlayItems") }
                        val underlayContent = underlayElements.flatMap(ElementNode::content)

                        val mergedResult = DefaultElementNode(overlayItem.name, overlayItem.sourceData, overlayItem.elementValues, mergeRecursively(underlayContent, overlayItem.content))
                        result.add(mergedResult)
                        overlayElementOrigin[mergedResult] = OverlayNodeOrigin.MergedElements(
                            // TODO: handle the case with multiple underlay elements?
                            underlayElements.last(),
                            overlayItem
                        )
                    }
                }
            }
        }

        return result
    }


    private
    fun recordAsCopiedRecursively(node: DeclarativeDocument.DocumentNode, originFactory: (DeclarativeDocument.DocumentNode) -> CopiedOrigin) {
        val origin = originFactory(node)
        when (node) {
            is ElementNode -> {
                overlayElementOrigin[node] = origin
                node.elementValues.forEach { recordValueOriginRecursively(it, origin) }
                node.content.forEach { recordAsCopiedRecursively(it, originFactory) }
            }

            is ErrorNode -> {
                overlayErrorOrigin[node] = origin
            }

            is PropertyNode -> {
                overlayPropertyOrigin[node] = origin
                recordValueOriginRecursively(node.value, origin)
            }
        }
    }

    private
    fun recordValueOriginRecursively(value: ValueNode, origin: OverlayValueOrigin) {
        overlayValueOrigin[value] = origin
        when (value) {
            is ValueNode.ValueFactoryNode -> value.values.forEach { recordValueOriginRecursively(it, origin) }
            is ValueNode.LiteralValueNode -> Unit
        }
    }

    /**
     * A key for merging document nodes.
     * The scope of this key is a particular object's configuring block.
     *
     * Keys from the configuring blocks of different objects should never get matched against each other.
     */
    sealed interface MergeKey {
        /**
         * Nodes having this key are unique and never get merged with any other nodes.
         * Some examples are: elements produced by adding functions; error nodes.
         */
        data object CannotMerge : MergeKey

        /**
         * The key for properties that can get merged by shadowing.
         */
        data class CanMergeProperty(
            val propertyName: String
        ) : MergeKey

        /**
         * The key for element blocks that configure the same nested objects (like configuring functions do).
         *
         * TODO: once we have identity-aware configuring functions, include the arguments in this key.
         */
        data class CanMergeBlock(
            val functionName: String,
            val configuredTypeName: FqName
        ) : MergeKey
    }

    fun interface MergeKeyMapper {
        fun mapNodeToMergeKey(node: DeclarativeDocument.DocumentNode): MergeKey
    }
}


private
fun documentOverlayContextByResolutionResults(
    underlayResolutionContainer: DocumentResolutionContainer,
    overlayResolutionContainer: DocumentResolutionContainer
) = DocumentOverlayContext(resolutionContainerMergeKeyMapper(underlayResolutionContainer), resolutionContainerMergeKeyMapper(overlayResolutionContainer))


private
fun resolutionContainerMergeKeyMapper(
    resolutionContainer: DocumentResolutionContainer
) = DocumentOverlayContext.MergeKeyMapper { node ->
    when (val nodeResolution = resolutionContainer.data(node)) {
        is ElementResolution.SuccessfulElementResolution.ContainerElementResolved ->
            // TODO: this will need adjustment once access-and-configure semantics get replaced with ensure-exists-and-configure with literal key arguments
            DocumentOverlayContext.MergeKey.CannotMerge

        is ElementResolution.SuccessfulElementResolution.ConfiguringElementResolved -> {
            val name = (node as ElementNode).name
            DocumentOverlayContext.MergeKey.CanMergeBlock(name, nodeResolution.elementType.name)
        }

        is PropertyResolution.PropertyAssignmentResolved ->
            DocumentOverlayContext.MergeKey.CanMergeProperty(nodeResolution.property.name)

        is ElementResolution.ElementNotResolved,
        is PropertyResolution.PropertyNotAssigned,
        ErrorResolution -> DocumentOverlayContext.MergeKey.CannotMerge
    }
}


private
class OverlayResolutionContainer(
    private val overlayOriginContainer: OverlayOriginContainer,
    private val underlay: DocumentResolutionContainer,
    private val overlay: DocumentResolutionContainer
) : DocumentResolutionContainer,
    NodeDataContainer<DocumentNodeResolution, ElementResolution, PropertyResolution, ErrorResolution> by
    OverlayRoutedNodeDataContainer(overlayOriginContainer, underlay, overlay),
    ValueDataContainer<ValueNodeResolution, ValueFactoryResolution, LiteralValueResolved> by
    OverlayRoutedValueDataContainer(overlayOriginContainer, underlay, overlay)
