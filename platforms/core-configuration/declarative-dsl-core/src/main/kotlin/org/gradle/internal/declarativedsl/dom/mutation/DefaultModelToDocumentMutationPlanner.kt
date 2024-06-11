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
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution


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
    ): ModelMutationPlan {
        val scopeLocationMatcher = ScopeLocationMatcher(DocumentWithResolution(document, resolution))

        return when (request.mutation) {
            is ModelMutation.UnsetProperty ->
                withMatchingProperties(scopeLocationMatcher, resolution, request) {
                    DocumentMutation.DocumentNodeTargetedMutation.RemoveNode(it)
                }

            is ModelMutation.SetPropertyValue ->
                withMatchingProperties(scopeLocationMatcher, resolution, request) {
                    DocumentMutation.ValueTargetedMutation.ReplaceValue(
                        it.value,
                        request.mutation.newValue.value(mutationArguments)
                    )
                }

            is ModelMutation.AddElement -> withMatchingScopes(scopeLocationMatcher, request) { matchingScopes ->
                DefaultModelMutationPlan(
                    matchingScopes
                        .filter { it.elements.isNotEmpty() } // TODO: this is the top level scope, will need special handling
                        .map { scope -> scope.elements.last() }
                        .map { scopeElement ->
                            DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock(
                                scopeElement,
                                listOf(request.mutation.newElement.element(mutationArguments))
                            )
                        }.toList(),
                    emptyList()
                )
            }
        }
    }

    private
    fun NewValueNodeProvider.value(argumentContainer: MutationArgumentContainer) = when (this) {
        is NewValueNodeProvider.ArgumentBased -> produceValueNode(argumentContainer)
        is NewValueNodeProvider.Constant -> valueNode
    }

    private
    fun NewElementNodeProvider.element(argumentContainer: MutationArgumentContainer) = when (this) {
        is NewElementNodeProvider.Constant -> elementNode
        is NewElementNodeProvider.ArgumentBased -> produceElementNode(argumentContainer)
    }

    private
    fun withMatchingScopes(
        scopeLocationMatcher: ScopeLocationMatcher,
        request: ModelMutationRequest,
        mapScopesToMutationPlan: (Set<Scope>) -> ModelMutationPlan
    ): ModelMutationPlan {
        val matchingScopes = scopeLocationMatcher.match(request.location)
        return when {
            matchingScopes.isEmpty() -> DefaultModelMutationPlan(emptyList(), listOf(UnsuccessfulModelMutation(request, listOf(ModelMutationFailureReason.ScopeLocationNotMatched))))
            else -> mapScopesToMutationPlan(matchingScopes)
        }
    }

    private
    fun withMatchingProperties(
        scopeLocationMatcher: ScopeLocationMatcher,
        resolution: DocumentResolutionContainer,
        request: ModelMutationRequest,
        mapPropertyToDocumentMutation: (DeclarativeDocument.DocumentNode.PropertyNode) -> DocumentMutation
    ): ModelMutationPlan {
        return withMatchingScopes(scopeLocationMatcher, request) { matchingScopes ->
            val candidatePropertyNodes = matchingScopes
                .filter { it.elements.isNotEmpty() } // TODO: this is the top level scope, will need special handling
                .map { scope -> scope.elements.last() }
                .flatMap { lastScopeElement ->
                    lastScopeElement.content.filterIsInstance<DeclarativeDocument.DocumentNode.PropertyNode>()
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
                    listOf(UnsuccessfulModelMutation(request, listOf(ModelMutationFailureReason.TargetPropertyNotFound)))
                )

                else -> DefaultModelMutationPlan(matchedPropertyNodes.map(mapPropertyToDocumentMutation), emptyList())
            }
        }
    }
}
