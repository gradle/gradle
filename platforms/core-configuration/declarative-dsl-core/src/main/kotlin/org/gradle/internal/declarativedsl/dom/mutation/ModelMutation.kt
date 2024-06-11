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
        val newElement: NewElementNodeProvider,
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


sealed interface NewElementNodeProvider {
    data class Constant(val elementNode: DocumentNode.ElementNode) : NewElementNodeProvider
    fun interface ArgumentBased : NewElementNodeProvider {
        fun produceElementNode(argumentContainer: MutationArgumentContainer): DocumentNode.ElementNode
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
class DefaultModelMutationPlan(
    override val documentMutations: List<DocumentMutation>,
    override val unsuccessfulModelMutations: List<UnsuccessfulModelMutation>
) : ModelMutationPlan
