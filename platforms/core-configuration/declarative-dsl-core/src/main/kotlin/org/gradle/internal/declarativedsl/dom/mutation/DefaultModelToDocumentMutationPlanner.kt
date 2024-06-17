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
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.ref
import org.gradle.internal.declarativedsl.analysis.sameType
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.DefaultElementNode
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.SuccessfulElementResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.PropertyResolution.PropertyAssignmentResolved
import org.gradle.internal.declarativedsl.dom.mutation.common.NewDocumentNodes
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.language.SyntheticallyProduced
import org.jetbrains.kotlin.utils.addToStdlib.zipWithNulls


internal
class DefaultModelToDocumentMutationPlanner : ModelToDocumentMutationPlanner {
    override fun planModelMutation(
        modelSchema: AnalysisSchema,
        documentWithResolution: DocumentWithResolution,
        mutationRequest: ModelMutationRequest,
        mutationArguments: MutationArgumentContainer
    ): ModelMutationPlan {
        val resolution = documentWithResolution.resolutionContainer
        val documentMemberMatcher = DocumentMemberAndTypeMatcher(modelSchema, resolution)
        val scopeLocationMatcher = ScopeLocationMatcher(modelSchema.topLevelReceiverType, documentWithResolution, documentMemberMatcher)

        return when (val mutation = mutationRequest.mutation) {
            is ModelMutation.UnsetProperty ->
                withMatchingProperties(scopeLocationMatcher, documentMemberMatcher, mutationRequest, mutation.property) {
                    DocumentMutation.DocumentNodeTargetedMutation.RemoveNode(it)
                }

            is ModelMutation.SetPropertyValue ->
                withMatchingProperties(scopeLocationMatcher, documentMemberMatcher, mutationRequest, mutation.property) {
                    DocumentMutation.ValueTargetedMutation.ReplaceValue(it.value) { mutation.newValue.value(mutationArguments) }
                }

            is ModelMutation.AddConfiguringBlockIfAbsent -> {
                withMatchingScopes(scopeLocationMatcher, mutationRequest) { matchingScopes ->
                    val insertions = matchingScopes
                        .filter { it.elements.isNotEmpty() } // TODO: this is the top level scope, will need special handling
                        .filter { documentMemberMatcher.findMatchingConfiguringElements(it, mutation.function).isEmpty() }
                        .map {
                            DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock(it.elements.last()) {
                                NewDocumentNodes(
                                    listOf(DefaultElementNode(mutation.function.function.simpleName, SyntheticallyProduced, emptyList(), emptyList()))
                                )
                            }
                        }
                    DefaultModelMutationPlan(insertions, emptyList())
                }
            }

            is ModelMutation.AddNewElement -> withMatchingScopes(scopeLocationMatcher, mutationRequest) { matchingScopes ->
                DefaultModelMutationPlan(
                    matchingScopes
                        .filter { it.elements.isNotEmpty() } // TODO: this is the top level scope, will need special handling
                        .map { scope -> scope.elements.last() }
                        .map { scopeElement ->
                            DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock(scopeElement) {
                                NewDocumentNodes(listOf(mutation.newElement.element(mutationArguments)))
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
        documentMemberMatcher: DocumentMemberAndTypeMatcher,
        request: ModelMutationRequest,
        property: TypedMember.TypedProperty,
        mapPropertyToDocumentMutation: (PropertyNode) -> DocumentMutation
    ): ModelMutationPlan {
        return withMatchingScopes(scopeLocationMatcher, request) { matchingScopes ->
            val matchedPropertyNodes = matchingScopes.flatMap { documentMemberMatcher.findMatchingPropertyNodes(it, property) }

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
class DocumentMemberAndTypeMatcher(
    val schema: AnalysisSchema,
    val resolution: DocumentResolutionContainer
) {
    private
    val typeRefContext = SchemaTypeRefContext(schema)

    fun typeIsSubtypeOf(subtype: DataType, supertype: DataType): Boolean =
        subtype.ref.isEquivalentTo(supertype.ref) ||
            subtype is DataClass && supertype is DataClass && supertype.name in subtype.supertypes

    private
    fun <T> ifScopeMatchesType(scope: Scope, typedMember: TypedMember, then: (ElementNode) -> T): T? {
        val ownerElement = scope.elements.lastOrNull()
            ?: return null

        val ownerType = (resolution.data(ownerElement) as? SuccessfulElementResolution)?.elementType as? DataClass
            ?: return null

        if (!typeIsSubtypeOf(ownerType, typedMember.ownerType)) {
            return null
        }

        return then(ownerElement)
    }

    fun findMatchingPropertyNodes(
        scope: Scope,
        typedProperty: TypedMember.TypedProperty
    ): List<PropertyNode> =
        ifScopeMatchesType(scope, typedProperty) { ownerElement ->
            ownerElement.content
                .filterIsInstance<PropertyNode>()
                .filter { (resolution.data(it) as? PropertyAssignmentResolved)?.property?.matchesOrOverrides(typedProperty.property) == true }
        }.orEmpty()

    fun findMatchingConfiguringElements(
        scope: Scope,
        typedFunction: TypedMember.TypedFunction
    ): List<ElementNode> =
        ifScopeMatchesType(scope, typedFunction) {
            it.content.filterIsInstance<ElementNode>().filter { element ->
                val resolution = resolution.data(element)
                resolution is SuccessfulElementResolution && resolution.elementFactoryFunction.matchesOrOverrides(typedFunction.function)
            }
        }.orEmpty()

    fun isSameFunctionOrOverrides(functionToCheck: TypedMember.TypedFunction, against: TypedMember.TypedFunction) =
        typeIsSubtypeOf(functionToCheck.ownerType, against.ownerType) &&
            functionToCheck.function.matchesOrOverrides(against.function)

    private
    fun DataProperty.matchesOrOverrides(other: DataProperty) =
        name == other.name && valueType.isEquivalentTo(other.valueType)

    private
    fun SchemaMemberFunction.matchesOrOverrides(other: SchemaMemberFunction) =
        simpleName == other.simpleName &&
            parameters.zipWithNulls(other.parameters).all { (a, b) ->
                a != null && b != null &&
                    a.name == b.name &&
                    a.type.isEquivalentTo(b.type) // semantics for the parameters is probably irrelevant
            }

    private
    fun FunctionSemantics.matchesOrOverrides(other: FunctionSemantics) = when (this) {
        is FunctionSemantics.Builder -> other is FunctionSemantics.Builder
        is FunctionSemantics.AccessAndConfigure -> other is FunctionSemantics.AccessAndConfigure && configuredType.isEquivalentTo(other.configuredType)
        is FunctionSemantics.AddAndConfigure -> other is FunctionSemantics.AddAndConfigure && configuredType.isEquivalentTo(other.configuredType)
        is FunctionSemantics.Pure -> other is FunctionSemantics.Pure && typeIsSubtypeOf(typeRefContext.resolveRef(returnValueType), typeRefContext.resolveRef(other.returnValueType))
    }

    private
    fun DataTypeRef.isEquivalentTo(other: DataTypeRef) =
        sameType(typeRefContext.resolveRef(this), typeRefContext.resolveRef(other))
}
