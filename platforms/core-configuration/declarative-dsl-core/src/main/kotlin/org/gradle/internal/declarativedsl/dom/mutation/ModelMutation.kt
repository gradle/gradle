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
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution


interface ModelMutationPlan {
    val documentMutations: List<DocumentMutation>
    val modelMutationIssues: List<ModelMutationIssue>
}


interface ModelToDocumentMutationPlanner {
    fun planModelMutation(
        modelSchema: AnalysisSchema,
        documentWithResolution: DocumentWithResolution,
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
        val property: TypedMember.TypedProperty
    }

    data class SetPropertyValue(
        override val property: TypedMember.TypedProperty,
        val newValue: NewValueNodeProvider,
    ) : ModelPropertyMutation

    data class AddNewElement(
        val newElement: NewElementNodeProvider
    ) : ModelMutation

    data class AddConfiguringBlockIfAbsent(
        val function: TypedMember.TypedFunction
    ) : ModelMutation {
        init {
            require(function.function.semantics is FunctionSemantics.AccessAndConfigure) {
                "only configuring functions are allowed in ${this::class.simpleName} mutations, got $function"
            }
            require(function.function.parameters.isEmpty()) {
                "only functions with no value parameters are allowed in ${this::class.simpleName} mutations, got $function"
            }
        }
    }

    data class UnsetProperty(
        override val property: TypedMember.TypedProperty
    ) : ModelPropertyMutation
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


data class ModelMutationIssue(
    val reason: ModelMutationIssueReason
)


sealed interface ModelMutationIssueReason {
    data object TargetPropertyNotFound : ModelMutationIssueReason
    data object ScopeLocationNotMatched : ModelMutationIssueReason
}


internal
class DefaultModelMutationPlan(
    override val documentMutations: List<DocumentMutation>,
    override val modelMutationIssues: List<ModelMutationIssue>
) : ModelMutationPlan
