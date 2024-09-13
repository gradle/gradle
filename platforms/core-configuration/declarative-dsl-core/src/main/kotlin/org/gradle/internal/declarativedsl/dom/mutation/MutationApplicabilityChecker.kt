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

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ErrorNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.data.NodeData
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToStartOfBlock
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.ElementNodeCallMutation
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.InsertNodesAfterNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.InsertNodesBeforeNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.PropertyNodeMutation.RenamePropertyNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.RemoveNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ReplaceNode
import org.gradle.internal.declarativedsl.dom.mutation.MutationApplicability.AffectedNode
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution


class MutationApplicabilityChecker(
    private val modelSchema: AnalysisSchema,
    private val documentWithResolution: DocumentWithResolution
) {
    private
    val planner: ModelToDocumentMutationPlanner = DefaultModelToDocumentMutationPlanner()

    fun checkApplicability(
        mutationDefinition: MutationDefinition
    ): List<MutationApplicability> {
        if (!mutationDefinition.isCompatibleWithSchema(modelSchema)) {
            return emptyList()
        }
        val modelMutations = mutationDefinition.defineModelMutationSequence(modelSchema)
        return modelMutations.flatMap { mutationRequest ->
            val documentMutations = planner.planModelMutation(modelSchema, documentWithResolution, mutationRequest, mutationArguments { /* none */ }).documentMutations
            mutationApplicabilityFromDocumentMutations(documentMutations)
        }
    }

    private
    val valueOwnerNode = buildMap {
        fun visitValue(value: DeclarativeDocument.ValueNode, owner: DocumentNode) {
            put(value, owner)
            when (value) {
                is DeclarativeDocument.ValueNode.ValueFactoryNode -> value.values.forEach { visitValue(it, owner) }
                is DeclarativeDocument.ValueNode.LiteralValueNode -> Unit
            }
        }

        fun visitDocumentNode(node: DocumentNode) {
            when (node) {
                is ElementNode -> {
                    node.elementValues.forEach { visitValue(it, node) }
                    node.content.forEach(::visitDocumentNode)
                }
                is PropertyNode -> visitValue(node.value, node)
                is ErrorNode -> Unit
            }
        }

        documentWithResolution.document.content.forEach(::visitDocumentNode)
    }

    private
    fun mutationApplicabilityFromDocumentMutations(
        documentMutation: List<DocumentMutation>,
    ): List<MutationApplicability> = documentMutation.map { mutation ->
        when (mutation) {
            is DocumentMutation.DocumentNodeTargetedMutation -> when (mutation) {
                is DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation -> when (mutation) {
                    is AddChildrenToEndOfBlock,
                    is AddChildrenToStartOfBlock -> MutationApplicability.ScopeWithoutAffectedNodes(mutation.targetNode)
                    is ElementNodeCallMutation -> AffectedNode(mutation.targetNode)
                }

                is InsertNodesAfterNode, // this and the one below are questionable -- should it be the outer container instead?
                is InsertNodesBeforeNode,
                is RenamePropertyNode,
                is RemoveNode,
                is ReplaceNode -> AffectedNode(mutation.targetNode)
            }

            is DocumentMutation.ValueTargetedMutation -> when (mutation) {
                is DocumentMutation.ValueTargetedMutation.ValueFactoryNodeMutation.ValueNodeCallMutation,
                is DocumentMutation.ValueTargetedMutation.ReplaceValue -> AffectedNode(valueOwnerNode.getValue(mutation.targetValue))
            }
        }
    }
}


sealed interface MutationApplicability {
    data class ScopeWithoutAffectedNodes(val scope: ElementNode) : MutationApplicability
    data class AffectedNode(val node: DocumentNode) : MutationApplicability
}


fun MutationDefinitionCatalog.applicabilityFor(
    documentSchema: AnalysisSchema,
    document: DocumentWithResolution
): NodeData<List<ApplicableMutation>> {
    val allMutations = mutationDefinitionsById.values
    val applicabilityChecker = MutationApplicabilityChecker(documentSchema, document)
    val applicabilityListsByMutation = allMutations.associateWith(applicabilityChecker::checkApplicability)

    val data = buildMap<DocumentNode, MutableList<ApplicableMutation>> {
        applicabilityListsByMutation.forEach { (mutation, applicabilityList) ->
            applicabilityList.forEach { applicability ->
                when (applicability) {
                    is AffectedNode -> getOrPut(applicability.node, ::mutableListOf).add(ApplicableMutation(mutation, applicability))
                    is MutationApplicability.ScopeWithoutAffectedNodes -> getOrPut(applicability.scope, ::mutableListOf).add(ApplicableMutation(mutation, applicability))
                }
            }
        }
    }

    return object : NodeData<List<ApplicableMutation>> {
        override fun data(node: ElementNode): List<ApplicableMutation> = data[node].orEmpty()
        override fun data(node: PropertyNode): List<ApplicableMutation> = data[node].orEmpty()
        override fun data(node: ErrorNode): List<ApplicableMutation> = data[node].orEmpty()
    }
}


data class ApplicableMutation(
    val mutationDefinition: MutationDefinition,
    val applicability: MutationApplicability
)
