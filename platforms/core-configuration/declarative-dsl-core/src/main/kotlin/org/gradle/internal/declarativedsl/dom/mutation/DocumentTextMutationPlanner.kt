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
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode.ValueFactoryNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.ElementNodeCallMutation
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.PropertyNodeMutation.RenamePropertyNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.ValueTargetedMutation.ValueFactoryNodeMutation.ValueNodeCallMutation
import org.gradle.internal.declarativedsl.dom.writing.MutatedDocumentTextGenerator
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTree
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTreeBuilder


class DocumentTextMutationPlanner : DocumentMutationPlanner<DocumentTextMutationPlan> {
    override fun planDocumentMutations(document: DeclarativeDocument, mutations: List<DocumentMutation>): DocumentTextMutationPlan {
        val confirmationTracker = MutationConfirmationTracker()
        val nameMapper = buildNameMapper(mutations, confirmationTracker)
        val nodeFilter = buildNodeRemovalPredicate(mutations, confirmationTracker)

        val resultText = MutatedDocumentTextGenerator().generateText(
            TextPreservingTreeBuilder().build(document),
            mapNames = nameMapper,
            removeNodeIf = nodeFilter
        )

        val nonAppliedMutations = mutations.filterNot(confirmationTracker.confirmedMutations::contains)

        return DocumentTextMutationPlan(resultText, nonAppliedMutations.map { UnsuccessfulDocumentMutation(it, listOf(DocumentMutationFailureReason.TargetNotFoundOrSuperseded)) })
    }

    private
    class MutationConfirmationTracker {
        private
        val confirmed = mutableSetOf<DocumentMutation>()

        val confirmedMutations: Set<DocumentMutation>
            get() = confirmed

        fun confirmMutation(documentMutation: DocumentMutation) {
            this.confirmed.add(documentMutation)
        }
    }

    sealed interface NewNameProviderFromMutation {
        val mutation: DocumentMutation
        val name: String

        class FromPropertyRename(override val mutation: RenamePropertyNode) : NewNameProviderFromMutation {
            override val name: String
                get() = mutation.newName
        }

        class FromCallMutation(override val mutation: DocumentMutation.HasCallMutation, private val renameCall: CallMutation.RenameCall) : NewNameProviderFromMutation {
            init {
                require(mutation.callMutation == renameCall)
            }

            override val name: String
                get() = renameCall.newName
        }
    }

    private
    class NameMapper(
        val newNamesForProperties: Map<PropertyNode, NewNameProviderFromMutation.FromPropertyRename>,
        val newNamesForElements: MutableMap<ElementNode, NewNameProviderFromMutation.FromCallMutation>,
        val newNamesForValueFactories: MutableMap<ValueFactoryNode, NewNameProviderFromMutation.FromCallMutation>,
        val confirmationTracker: MutationConfirmationTracker
    ) : (TextPreservingTree.ChildTag, String) -> String {
        override fun invoke(ownerTag: TextPreservingTree.ChildTag, oldName: String): String {
            val newNameFromMutation = when (ownerTag) {
                is TextPreservingTree.ChildTag.BlockElement -> when (ownerTag.documentNode) {
                    is PropertyNode -> newNamesForProperties[ownerTag.documentNode]
                    is ElementNode -> newNamesForElements[ownerTag.documentNode]
                    is DeclarativeDocument.DocumentNode.ErrorNode -> null
                }

                is TextPreservingTree.ChildTag.ValueNodeChildTag -> if (ownerTag.valueNode is ValueFactoryNode) {
                    newNamesForValueFactories[ownerTag.valueNode]
                } else null

                else -> null
            }
            return if (newNameFromMutation != null) {
                confirmationTracker.confirmMutation(newNameFromMutation.mutation)
                newNameFromMutation.name
            } else oldName
        }
    }

    private
    fun buildNameMapper(
        mutations: List<DocumentMutation>,
        confirmationTracker: MutationConfirmationTracker,
    ): NameMapper {

        val newNamesForProperties: MutableMap<PropertyNode, NewNameProviderFromMutation.FromPropertyRename> = mutableMapOf()
        val newNamesForElements: MutableMap<ElementNode, NewNameProviderFromMutation.FromCallMutation> = mutableMapOf()
        val newNamesForValueFactories: MutableMap<ValueFactoryNode, NewNameProviderFromMutation.FromCallMutation> = mutableMapOf()

        mutations.forEach { mutation ->
            when (mutation) {
                is RenamePropertyNode -> newNamesForProperties[mutation.targetNode] = NewNameProviderFromMutation.FromPropertyRename(mutation)
                is ElementNodeCallMutation ->
                    if (mutation.callMutation is CallMutation.RenameCall) newNamesForElements[mutation.targetNode] = NewNameProviderFromMutation.FromCallMutation(mutation, mutation.callMutation)

                is ValueNodeCallMutation ->
                    if (mutation.callMutation is CallMutation.RenameCall) newNamesForValueFactories[mutation.targetValue] =
                        NewNameProviderFromMutation.FromCallMutation(mutation, mutation.callMutation)

                else -> Unit
            }
        }

        return NameMapper(newNamesForProperties, newNamesForElements, newNamesForValueFactories, confirmationTracker)
    }

    private
    class NodeRemovalPredicate(
        val nodeToRemoveByMutation: Map<DeclarativeDocument.DocumentNode, DocumentMutation>,
        val confirmationTracker: MutationConfirmationTracker
    ) : (TextPreservingTree.ChildTag) -> Boolean {
        override fun invoke(childTag: TextPreservingTree.ChildTag): Boolean {
            if (childTag is TextPreservingTree.ChildTag.BlockElement) {
                val mutation = nodeToRemoveByMutation[childTag.documentNode]
                if (mutation != null) {
                    confirmationTracker.confirmMutation(mutation)
                    return true
                }
            }

            return false
        }
    }

    private
    fun buildNodeRemovalPredicate(
        mutations: List<DocumentMutation>,
        mutationConfirmationTracker: MutationConfirmationTracker
    ): NodeRemovalPredicate = NodeRemovalPredicate(
        buildMap {
            mutations.forEach { mutation ->
                when (mutation) {
                    is DocumentMutation.DocumentNodeTargetedMutation.RemoveNode,
                    is DocumentMutation.DocumentNodeTargetedMutation.ReplaceNode -> put((mutation as DocumentMutation.DocumentNodeTargetedMutation).targetNode, mutation)

                    else -> Unit
                }
            }
        }, mutationConfirmationTracker
    )
}


class DocumentTextMutationPlan(
    val newText: String,
    override val unsuccessfulDocumentMutations: List<UnsuccessfulDocumentMutation>
) : DocumentMutationPlan
