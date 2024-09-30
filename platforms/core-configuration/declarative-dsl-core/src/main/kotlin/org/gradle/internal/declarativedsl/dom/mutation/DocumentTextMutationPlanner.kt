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

package org.gradle.internal.declarativedsl.dom.mutation

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ErrorNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode.LiteralValueNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode.NamedReferenceNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode.ValueFactoryNode
import org.gradle.internal.declarativedsl.dom.DefaultElementNode
import org.gradle.internal.declarativedsl.dom.DefaultValueFactoryNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.ElementNodeCallMutation
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.InsertNodesBeforeNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.PropertyNodeMutation.RenamePropertyNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.ValueTargetedMutation.ValueFactoryNodeMutation.ValueNodeCallMutation
import org.gradle.internal.declarativedsl.dom.mutation.common.NewDocumentNodes
import org.gradle.internal.declarativedsl.dom.writing.MutatedDocumentTextGenerator
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTree
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTreeBuilder


class DocumentTextMutationPlan(
    val newText: String,
    override val unsuccessfulDocumentMutations: List<UnsuccessfulDocumentMutation>
) : DocumentMutationPlan


class DocumentTextMutationPlanner : DocumentMutationPlanner<DocumentTextMutationPlan> {

    /**
     * Translates a series of [mutations] on the [document] content into the text-level mutations and produces a [DocumentTextMutationPlan] with those mutations applied.
     *
     * The mutations are applied as if done in a series, and either succeed if there was no conflict or fail and get reported in [DocumentTextMutationPlan.unsuccessfulDocumentMutations] if
     * there were conflicting mutations. Failed mutations get excluded from the plan, and the resulting [DocumentTextMutationPlan.newText] will show the effect of all the succeeded mutations,
     * even those that appear later in the [mutations] list than the failing ones.
     *
     * * If a mutation removes or replaces a [DeclarativeDocument.Node] altogether (or replaces the node's direct or transitive owner), then any further mutations operating on that node will fail.
     *
     * * If a mutation modifies the parts of a node, such as its name, values (property value or call arguments) or nested content, then further mutations can still operate on the node,
     *   including mutations that remove or replace the node.
     *
     *     * In case earlier mutations become shadowed by a later mutation, they are all still considered successful, as if applied one-by-one. For example, in `a { x = 1 }`
     *       replacing `x`'s value with `2` and removing `a { ... }` will not report any of the mutations as failures and will lead to the removal of `a { ... }`.
     */
    override fun planDocumentMutations(document: DeclarativeDocument, mutations: List<DocumentMutation>): DocumentTextMutationPlan {
        val applicationState = MutationApplicationState()
        val results = mutations.associateWith(applicationState::applyMutation)

        val nameMapper = with(applicationState) { NameMapper(newNamesForProperties, newNamesForElements, newNamesForValueFactories) }
        val nodeRemovalPredicate = NodeRemovalPredicate(applicationState.nodesToRemove)
        val valueMapper = ValueMapper(applicationState.valueReplacement)

        val resultText = MutatedDocumentTextGenerator().generateText(
            TextPreservingTreeBuilder().build(document),
            mapNames = nameMapper,
            removeNodeIf = nodeRemovalPredicate,
            insertNodesBefore = nodeInsertionBeforeOrReplacing(applicationState, valueMapper, nameMapper),
            insertNodesAfter = NodeInsertionProvider(applicationState.insertAfter),
            replaceValue = valueMapper
        )

        return DocumentTextMutationPlan(resultText, results.entries.mapNotNull { (mutation, result) ->
            (result as? MutationApplicationResult.TargetsReplacedOrRemovedNode)?.let { UnsuccessfulDocumentMutation(mutation, listOf(DocumentMutationFailureReason.TargetNotFoundOrSuperseded)) }
        })
    }

    sealed interface MutationApplicationResult {
        data object Applied : MutationApplicationResult
        data object TargetsReplacedOrRemovedNode : MutationApplicationResult
    }

    private
    fun nodeInsertionBeforeOrReplacing(
        applicationState: MutationApplicationState,
        valueMapper: ValueMapper,
        nameMapper: NameMapper,
    ) = NodeInsertionProvider(
        insertNodesByMutation = (applicationState.insertBefore.keys + applicationState.insertContentIntoEmptyNodes.keys)
            .associateWith { node ->
                applicationState.insertBefore[node].orEmpty() +
                    if (node is ElementNode) {
                        applicationState.insertContentIntoEmptyNodes[node]?.let { content ->
                            listOf(
                                NodeInsertion.ByReplacingElementInAddingContent.createFrom(
                                    node,
                                    valueMapper,
                                    nameMapper,
                                    NewDocumentNodes.composite(content)
                                )
                            )
                        }.orEmpty()
                    } else emptyList()
            })

    private
    class MutationApplicationState {
        /**
         * Maintains a set of nodes that are affected by some mutation and thus can no longer be a target of another mutation.
         * As mutations focus on nodes individually, tainting a node should also recursively taint all of its values and content.
         */
        val removedOrReplaced: MutableSet<DeclarativeDocument.Node> = mutableSetOf()

        val insertBefore = mutableMapOf<DocumentNode, MutableList<NodeInsertion>>()
        val insertAfter = mutableMapOf<DocumentNode, MutableList<NodeInsertion>>()
        val insertContentIntoEmptyNodes = mutableMapOf<ElementNode, MutableList<NewDocumentNodes>>()
        val valueReplacement = mutableMapOf<DeclarativeDocument.ValueNode, DeclarativeDocument.ValueNode>()
        val newNamesForProperties: MutableMap<PropertyNode, String> = mutableMapOf()
        val newNamesForElements: MutableMap<ElementNode, String> = mutableMapOf()
        val newNamesForValueFactories: MutableMap<ValueFactoryNode, String> = mutableMapOf()
        val nodesToRemove: MutableSet<DocumentNode> = mutableSetOf()

        private
        fun registerRemovedOrReplacedNode(node: DeclarativeDocument.Node) {
            if (removedOrReplaced.add(node)) {
                when (node) {
                    is ElementNode -> {
                        node.elementValues.forEach(::registerRemovedOrReplacedNode)
                        node.content.forEach(::registerRemovedOrReplacedNode)
                    }

                    is PropertyNode -> {
                        registerRemovedOrReplacedNode(node.value)
                    }

                    is ValueFactoryNode -> {
                        node.values.forEach(::registerRemovedOrReplacedNode)
                    }

                    is LiteralValueNode,
                    is NamedReferenceNode,
                    is ErrorNode -> Unit
                }
            }
        }

        fun applyMutation(mutation: DocumentMutation): MutationApplicationResult {
            if (mutation is DocumentMutation.DocumentNodeTargetedMutation && mutation.targetNode in removedOrReplaced) {
                return MutationApplicationResult.TargetsReplacedOrRemovedNode
            }
            if (mutation is DocumentMutation.ValueTargetedMutation && mutation.targetValue in removedOrReplaced) {
                return MutationApplicationResult.TargetsReplacedOrRemovedNode
            }

            return recordMutationEffects(mutation)
        }

        private
        fun recordMutationEffects(mutation: DocumentMutation): MutationApplicationResult {
            when (mutation) {
                is DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock -> {
                    if (mutation.targetNode.content.isNotEmpty()) {
                        insertAfter.getOrPut(mutation.targetNode.content.last(), ::mutableListOf).add(NodeInsertion.ByInsertNodesToEnd(mutation))
                    } else {
                        // We do not use insertBefore or insertAfter because further mutations can add more items to them, and the order will become incorrect.
                        insertContentIntoEmptyNodes.getOrPut(mutation.targetNode, ::mutableListOf).add(mutation.nodes())
                        nodesToRemove.add(mutation.targetNode)
                        // No need to mark the element as tainted: its content is empty, and its values are clean, we can still mutate them.
                    }
                }

                is DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToStartOfBlock -> {
                    if (mutation.targetNode.content.isNotEmpty()) {
                        insertBefore.getOrPut(mutation.targetNode.content.first(), ::mutableListOf).add(0, NodeInsertion.ByInsertNodesToStart(mutation))
                    } else {
                        insertContentIntoEmptyNodes.getOrPut(mutation.targetNode, ::mutableListOf).add(0, mutation.nodes())
                        nodesToRemove.add(mutation.targetNode)
                    }
                }

                is ElementNodeCallMutation -> {
                    if (mutation.callMutation is CallMutation.RenameCall) {
                        newNamesForElements[mutation.targetNode] = mutation.callMutation.newName()
                    }
                }

                is DocumentMutation.DocumentNodeTargetedMutation.InsertNodesAfterNode -> {
                    // Every new insertion has to happen right after the target node, so insert the mutation to the beginning of the list.
                    insertAfter.getOrPut(mutation.targetNode, ::mutableListOf).add(0, NodeInsertion.ByInsertNodesAfter(mutation))
                }

                is InsertNodesBeforeNode -> {
                    insertBefore.getOrPut(mutation.targetNode, ::mutableListOf).add(NodeInsertion.ByInsertNodesBefore(mutation))
                }

                is RenamePropertyNode -> {
                    newNamesForProperties[mutation.targetNode] = mutation.newName()
                }

                is DocumentMutation.DocumentNodeTargetedMutation.RemoveNode -> {
                    registerRemovedOrReplacedNode(mutation.targetNode)
                    nodesToRemove.add(mutation.targetNode)
                }

                is DocumentMutation.DocumentNodeTargetedMutation.ReplaceNode -> {
                    registerRemovedOrReplacedNode(mutation.targetNode)
                    nodesToRemove.add(mutation.targetNode)
                    insertAfter.getOrPut(mutation.targetNode, ::mutableListOf).add(0, NodeInsertion.ByReplaceNode(mutation))
                }

                is ValueNodeCallMutation -> {
                    when (mutation.callMutation) {
                        is CallMutation.RenameCall ->
                            newNamesForValueFactories[mutation.targetValue] = mutation.callMutation.newName()

                        is CallMutation.ReplaceCallArgumentMutation ->
                            recordValueReplacement(mutation.targetValue.values[mutation.callMutation.argumentAtIndex], mutation.callMutation.replaceWithValue())
                    }
                }

                is DocumentMutation.ValueTargetedMutation.ReplaceValue -> {
                    recordValueReplacement(mutation.targetValue, mutation.replaceWithValue())
                }
            }

            return MutationApplicationResult.Applied
        }

        private
        fun recordValueReplacement(
            targetValue: DeclarativeDocument.ValueNode,
            replacementValue: DeclarativeDocument.ValueNode,
        ) {
            registerRemovedOrReplacedNode(targetValue)
            valueReplacement[targetValue] = replacementValue
        }
    }


    private
    sealed interface NodeInsertion {
        fun getNewNodes(): NewDocumentNodes

        class ByInsertNodesBefore(
            val mutation: InsertNodesBeforeNode,
        ) : NodeInsertion {

            override fun getNewNodes() = mutation.nodes()
        }

        class ByInsertNodesToStart(
            val mutation: DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToStartOfBlock,
        ) : NodeInsertion {
            override fun getNewNodes() = mutation.nodes()
        }

        class ByInsertNodesAfter(
            val mutation: DocumentMutation.DocumentNodeTargetedMutation.InsertNodesAfterNode,
        ) : NodeInsertion {
            override fun getNewNodes() = mutation.nodes()
        }

        class ByInsertNodesToEnd(
            val mutation: DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock,
        ) : NodeInsertion {
            override fun getNewNodes() = mutation.nodes()
        }

        class ByReplaceNode(
            val mutation: DocumentMutation.DocumentNodeTargetedMutation.ReplaceNode,
        ) : NodeInsertion {
            override fun getNewNodes() = mutation.replaceWithNodes()
        }

        class ByReplacingElementInAddingContent(
            private val newDocumentNodes: NewDocumentNodes
        ) : NodeInsertion {
            override fun getNewNodes() = newDocumentNodes

            companion object {
                fun createFrom(targetNode: ElementNode, valueMapper: ValueMapper, nameMapper: NameMapper, newContent: NewDocumentNodes): ByReplacingElementInAddingContent =
                    ByReplacingElementInAddingContent(
                        NewDocumentNodes(
                            listOf(
                                DefaultElementNode(
                                    nameMapper.newNamesForElements[targetNode] ?: targetNode.name,
                                    targetNode.sourceData,
                                    targetNode.elementValues.map {
                                        applyValueMutations(it, nameMapper, valueMapper)
                                    },
                                    newContent.nodes
                                )
                            ),
                            newContent.representationFlags
                        )
                    )

                private
                fun applyValueMutations(valueNode: DeclarativeDocument.ValueNode, nameMapper: NameMapper, valueMapper: ValueMapper): DeclarativeDocument.ValueNode =
                    valueMapper.valueReplacement[valueNode]
                        ?: when (valueNode) {
                            is LiteralValueNode -> valueNode
                            is ValueFactoryNode -> DefaultValueFactoryNode(
                                nameMapper.newNamesForValueFactories[valueNode] ?: valueNode.factoryName,
                                valueNode.sourceData,
                                valueNode.values.map { applyValueMutations(it, nameMapper, valueMapper) })

                            is NamedReferenceNode -> error("named references not allowed as function arguments")
                        }
            }
        }
    }

    private
    class NameMapper(
        val newNamesForProperties: MutableMap<PropertyNode, String>,
        val newNamesForElements: MutableMap<ElementNode, String>,
        val newNamesForValueFactories: MutableMap<ValueFactoryNode, String>,
    ) : (TextPreservingTree.ChildTag, String) -> String {
        override fun invoke(ownerTag: TextPreservingTree.ChildTag, oldName: String): String = when (ownerTag) {
            is TextPreservingTree.ChildTag.BlockElement -> when (ownerTag.documentNode) {
                is PropertyNode -> newNamesForProperties[ownerTag.documentNode]
                is ElementNode -> newNamesForElements[ownerTag.documentNode]
                is ErrorNode -> null
            }

            is TextPreservingTree.ChildTag.ValueNodeChildTag -> if (ownerTag.valueNode is ValueFactoryNode) {
                newNamesForValueFactories[ownerTag.valueNode]
            } else null

            else -> null
        } ?: oldName
    }

    private
    class ValueMapper(
        val valueReplacement: Map<DeclarativeDocument.ValueNode, DeclarativeDocument.ValueNode>
    ) : (TextPreservingTree.ChildTag.ValueNodeChildTag) -> DeclarativeDocument.ValueNode? {
        override fun invoke(tag: TextPreservingTree.ChildTag.ValueNodeChildTag): DeclarativeDocument.ValueNode? =
            valueReplacement[tag.valueNode]
    }

    private
    class NodeRemovalPredicate(
        val nodeToRemoveByMutation: Set<DocumentNode>,
    ) : (TextPreservingTree.ChildTag) -> Boolean {
        override fun invoke(childTag: TextPreservingTree.ChildTag): Boolean =
            childTag is TextPreservingTree.ChildTag.BlockElement &&
                childTag.documentNode in nodeToRemoveByMutation
    }

    private
    class NodeInsertionProvider(
        val insertNodesByMutation: Map<DocumentNode, List<NodeInsertion>>,
    ) : (TextPreservingTree.ChildTag) -> NewDocumentNodes {
        override fun invoke(tag: TextPreservingTree.ChildTag): NewDocumentNodes = when (tag) {
            is TextPreservingTree.ChildTag.BlockElement ->
                insertNodesByMutation[tag.documentNode]
                    ?.let { insertions -> NewDocumentNodes.composite(insertions.map(NodeInsertion::getNewNodes)) }
                    ?: NewDocumentNodes.empty

            else -> NewDocumentNodes.empty
        }
    }
}
