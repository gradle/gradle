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
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer


interface ModelMutationPlan {
    val documentMutations: List<DocumentMutation>
    val unsuccessfulModelMutations: List<UnsuccessfulModelMutation>
}


interface ModelToDocumentMutationPlanner {
    fun planModelMutations(document: DeclarativeDocument, resolution: DocumentResolutionContainer, mutationRequests: List<ModelMutationRequest>): ModelMutationPlan
}


data class ModelMutationRequest(
    val location: ScopeLocation,
    val mutation: ModelMutation,
    val ifNotFoundBehavior: IfNotFoundBehavior = IfNotFoundBehavior.FailAndReport
)


sealed interface ModelMutation {
    data class SetPropertyValue(
        val property: DataProperty,
        val newValue: DeclarativeDocument.ValueNode,
        val ifPresentBehavior: IfPresentBehavior,
    ) : ModelMutation

    data class AddElement(
        val newElement: DocumentNode.ElementNode,
        val ifPresentBehavior: IfPresentBehavior
    ) : ModelMutation

    data class UnsetProperty(
        val property: DataProperty
    ) : ModelMutation

    sealed interface IfPresentBehavior {
        data object Overwrite : IfPresentBehavior
        data object FailAndReport : IfPresentBehavior
        data object Ignore : IfPresentBehavior
    }
}


data class UnsuccessfulModelMutation(
    val mutation: DocumentMutation,
    val failureReasons: List<ModelMutationFailureReason>
)


sealed interface ModelMutationFailureReason


internal
class DefaultModelToDocumentMutationPlanner : ModelToDocumentMutationPlanner {
    override fun planModelMutations(document: DeclarativeDocument, resolution: DocumentResolutionContainer, mutationRequests: List<ModelMutationRequest>): ModelMutationPlan =
        DefaultModelMutationPlan(
            mutationRequests.flatMap { toDocumentMutation(document, resolution, it) },
            emptyList() // TODO
        )

    private
    fun toDocumentMutation(document: DeclarativeDocument, resolution: DocumentResolutionContainer, request: ModelMutationRequest): List<DocumentMutation> = when (request.mutation) {
        is ModelMutation.UnsetProperty -> {
            val matchingScopes = request.location.match(document, resolution)
            val candidatePropertyNodes = matchingScopes
                .filter { it.elements.isNotEmpty() } // TODO: this is the top level scope, will need special handling
                .flatMap { scope ->
                    val lastScopeElement = scope.elements.last()
                    lastScopeElement.elementNodes.first.content.filterIsInstance<DocumentNode.PropertyNode>()
                }
                .toSet()

            val targetProperty = request.mutation.property
            candidatePropertyNodes
                .filter {
                    val propertyResolution = resolution.data(it) as DocumentResolution.PropertyResolution.PropertyAssignmentResolved // TODO: handle when this cast fails
                    propertyResolution.property === targetProperty // TODO: why does simple equals not work here?
                }
                .map {
                    DocumentMutation.DocumentNodeTargetedMutation.RemoveNode(it)
                }
        }

        is ModelMutation.AddElement -> TODO()
        is ModelMutation.SetPropertyValue -> TODO()
    }
}


internal
class DefaultModelMutationPlan(
    override val documentMutations: List<DocumentMutation>,
    override val unsuccessfulModelMutations: List<UnsuccessfulModelMutation>
) : ModelMutationPlan
