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
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.DefaultElementNode
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.SuccessfulElementResolution.ConfiguringElementResolved
import org.gradle.internal.declarativedsl.dom.DocumentResolution.PropertyResolution.PropertyAssignmentResolved
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.language.SyntheticallyProduced


internal
class DefaultModelToDocumentMutationPlanner : ModelToDocumentMutationPlanner {
    override fun planModelMutation(
        documentWithResolution: DocumentWithResolution,
        mutationRequest: ModelMutationRequest,
        mutationArguments: MutationArgumentContainer
    ): ModelMutationPlan =
        toDocumentMutations(documentWithResolution, mutationRequest, mutationArguments)

    private
    fun toDocumentMutations(
        documentWithResolution: DocumentWithResolution,
        request: ModelMutationRequest,
        mutationArguments: MutationArgumentContainer
    ): ModelMutationPlan {
        val scopeLocationMatcher = ScopeLocationMatcher(documentWithResolution)
        val resolution = documentWithResolution.resolutionContainer

        return when (val mutation = request.mutation) {
            is ModelMutation.UnsetProperty ->
                withMatchingProperties(scopeLocationMatcher, resolution, request, mutation.property) {
                    DocumentMutation.DocumentNodeTargetedMutation.RemoveNode(it)
                }

            is ModelMutation.SetPropertyValue ->
                withMatchingProperties(scopeLocationMatcher, resolution, request, mutation.property) {
                    DocumentMutation.ValueTargetedMutation.ReplaceValue(it.value) { mutation.newValue.value(mutationArguments) }
                }

            is ModelMutation.AddConfiguringBlockIfAbsent -> {
                withMatchingScopes(scopeLocationMatcher, request) { matchingScopes ->
                    val matchingNodeFinder = MatchingNodeFinder(resolution)
                    val insertions = matchingScopes
                        .filter { it.elements.isNotEmpty() } // TODO: this is the top level scope, will need special handling
                        .filter { matchingNodeFinder.findMatchingConfiguringElements(it, mutation.function).isEmpty() }
                        .map {
                            DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock(it.elements.last()) {
                                listOf(DefaultElementNode(mutation.function.simpleName, SyntheticallyProduced, emptyList(), emptyList()))
                            }
                        }
                    DefaultModelMutationPlan(insertions, emptyList())
                }
            }

            is ModelMutation.AddNewElement -> withMatchingScopes(scopeLocationMatcher, request) { matchingScopes ->
                DefaultModelMutationPlan(
                    matchingScopes
                        .filter { it.elements.isNotEmpty() } // TODO: this is the top level scope, will need special handling
                        .map { scope -> scope.elements.last() }
                        .map { scopeElement ->
                            DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock(scopeElement) {
                                listOf(mutation.newElement.element(mutationArguments))
                            }
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
        property: DataProperty,
        mapPropertyToDocumentMutation: (PropertyNode) -> DocumentMutation
    ): ModelMutationPlan {
        return withMatchingScopes(scopeLocationMatcher, request) { matchingScopes ->
            val matchingNodeFinder = MatchingNodeFinder(resolution)
            val matchedPropertyNodes = matchingScopes.flatMap { matchingNodeFinder.findMatchingPropertyNodes(it, property) }

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


internal
class MatchingNodeFinder(val resolution: DocumentResolutionContainer) {
    fun findMatchingPropertyNodes(
        scope: Scope,
        property: DataProperty
    ): List<PropertyNode> {
        return scope.elements.lastOrNull()?.content.orEmpty()
            .filterIsInstance<PropertyNode>()
            // TODO: subtype's properties should also match – bring a subtyping-aware member matcher here
            .filter { (resolution.data(it) as? PropertyAssignmentResolved)?.property === property }
    }

    fun findMatchingConfiguringElements(
        scope: Scope,
        function: SchemaMemberFunction
    ): List<ElementNode> =
        scope.elements.lastOrNull()?.content.orEmpty()
            .filterIsInstance<ElementNode>()
            .filter { ((resolution.data(it) as? ConfiguringElementResolved)?.elementFactoryFunction === function) }
}
