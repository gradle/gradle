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

import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutationFailureReason.ScopeLocationNotMatched
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutationFailureReason.TargetPropertyNotFound
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer


interface ModelMutationPlan {
    val documentMutations: List<DocumentMutation>
    val unsuccessfulModelMutations: List<UnsuccessfulModelMutation> // TODO: should this remain a list?
}


interface ModelToDocumentMutationPlanner {
    fun planModelMutation(
        document: DeclarativeDocument,
        resolution: DocumentResolutionContainer,
        mutationRequest: ModelMutationRequest,
        mutationArguments: MutationArgumentContainer
    ): ModelMutationPlan
}


data class ModelMutationRequest(
    val location: ScopeLocation,
    val mutation: ModelMutation,
    val ifNotFoundBehavior: IfNotFoundBehavior = IfNotFoundBehavior.FailAndReport
)


sealed interface ModelMutation {

    sealed interface ModelPropertyMutation : ModelMutation {
        val property: DataProperty
    }

    data class SetPropertyValue(
        override val property: DataProperty,
        val newValue: NewValueNodeProvider,
        val ifPresentBehavior: IfPresentBehavior,
    ) : ModelPropertyMutation

    data class AddElement(
        val newElement: DocumentNode.ElementNode,
        val ifPresentBehavior: IfPresentBehavior
    ) : ModelMutation

    data class UnsetProperty(
        override val property: DataProperty
    ) : ModelPropertyMutation

    sealed interface IfPresentBehavior {
        data object Overwrite : IfPresentBehavior
        data object FailAndReport : IfPresentBehavior
        data object Ignore : IfPresentBehavior
    }
}


sealed interface NewDocumentNodeProvider {
    data class Constant(val documentNode: DocumentNode) : NewDocumentNodeProvider
    fun interface ArgumentBased : NewDocumentNodeProvider {
        fun produceDocumentNode(argumentContainer: MutationArgumentContainer): DocumentNode
    }
}


sealed interface NewValueNodeProvider {
    data class Constant(val valueNode: ValueNode) : NewValueNodeProvider
    fun interface ArgumentBased : NewValueNodeProvider {
        fun produceValueNode(argumentContainer: MutationArgumentContainer): ValueNode
    }
}


data class UnsuccessfulModelMutation(
    val mutationRequest: ModelMutationRequest,
    val failureReasons: List<ModelMutationFailureReason> // TODO: should this remain a list?
)


sealed interface ModelMutationFailureReason {
    data object TargetPropertyNotFound : ModelMutationFailureReason

    data object ScopeLocationNotMatched : ModelMutationFailureReason
}


internal
class DefaultModelToDocumentMutationPlanner : ModelToDocumentMutationPlanner {
    override fun planModelMutation(
        document: DeclarativeDocument,
        resolution: DocumentResolutionContainer,
        mutationRequest: ModelMutationRequest,
        mutationArguments: MutationArgumentContainer
    ): ModelMutationPlan =
        toDocumentMutations(document, resolution, mutationRequest, mutationArguments)

    private
    fun toDocumentMutations(
        document: DeclarativeDocument,
        resolution: DocumentResolutionContainer,
        request: ModelMutationRequest,
        mutationArguments: MutationArgumentContainer
    ): ModelMutationPlan =
        when (request.mutation) {
            is ModelMutation.UnsetProperty ->
                withMatchingProperties(request, document, resolution) {
                    DocumentMutation.DocumentNodeTargetedMutation.RemoveNode(it)
                }

            is ModelMutation.SetPropertyValue ->
                withMatchingProperties(request, document, resolution) {
                    DocumentMutation.ValueTargetedMutation.ReplaceValue(
                        it.value,
                        request.mutation.newValue.value(mutationArguments)
                    )
                }

            is ModelMutation.AddElement -> withMatchingScopes(request, document, resolution) { matchingScopes ->
                DefaultModelMutationPlan(
                    matchingScopes
                        .filter { it.elements.isNotEmpty() } // TODO: this is the top level scope, will need special handling
                        .map { scope -> scope.elements.last().elementNodes }
                        .map { elementResolution ->
                            DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock(
                                elementResolution.first,
                                listOf(request.mutation.newElement)
                            )
                        }.toList(),
                    emptyList()
                )
            }
        }

    private
    fun NewValueNodeProvider.value(argumentContainer: MutationArgumentContainer) = when (this) {
        is NewValueNodeProvider.ArgumentBased -> produceValueNode(argumentContainer)
        is NewValueNodeProvider.Constant -> valueNode
    }

    private
    fun withMatchingScopes(
        request: ModelMutationRequest,
        document: DeclarativeDocument,
        resolution: DocumentResolutionContainer,
        mapScopesToMutationPlan: (Set<Scope>) -> ModelMutationPlan
    ): ModelMutationPlan {
        val matchingScopes = request.location.match(document, resolution)
        return when {
            matchingScopes.isEmpty() -> DefaultModelMutationPlan(emptyList(), listOf(UnsuccessfulModelMutation(request, listOf(ScopeLocationNotMatched))))
            else -> mapScopesToMutationPlan(matchingScopes)
        }
    }

    private
    fun withMatchingProperties(
        request: ModelMutationRequest,
        document: DeclarativeDocument,
        resolution: DocumentResolutionContainer,
        mapPropertyToDocumentMutation: (DocumentNode.PropertyNode) -> DocumentMutation
    ): ModelMutationPlan {
        return withMatchingScopes(request, document, resolution) { matchingScopes ->
            val candidatePropertyNodes = matchingScopes
                .filter { it.elements.isNotEmpty() } // TODO: this is the top level scope, will need special handling
                .map { scope -> scope.elements.last() }
                .flatMap { lastScopeElement ->
                    lastScopeElement.elementNodes.first.content.filterIsInstance<DocumentNode.PropertyNode>()
                }
                .toSet()


            val targetProperty = (request.mutation as ModelMutation.ModelPropertyMutation).property
            val matchedPropertyNodes = candidatePropertyNodes
                .filter {
                    val propertyResolution = resolution.data(it) as DocumentResolution.PropertyResolution.PropertyAssignmentResolved // TODO: handle when this cast fails
                    propertyResolution.property === targetProperty // TODO: why does simple equals not work here?
                }

            when {
                matchedPropertyNodes.isEmpty() -> DefaultModelMutationPlan(
                    emptyList(),
                    listOf(UnsuccessfulModelMutation(request, listOf(TargetPropertyNotFound)))
                )

                else -> DefaultModelMutationPlan(matchedPropertyNodes.map(mapPropertyToDocumentMutation), emptyList())
            }
        }
    }
}


internal
class DefaultModelMutationPlan(
    override val documentMutations: List<DocumentMutation>,
    override val unsuccessfulModelMutations: List<UnsuccessfulModelMutation>
) : ModelMutationPlan
