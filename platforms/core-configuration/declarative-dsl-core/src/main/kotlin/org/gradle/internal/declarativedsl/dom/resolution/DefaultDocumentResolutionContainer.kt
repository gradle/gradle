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

package org.gradle.internal.declarativedsl.dom.resolution

import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.declarative.dsl.evaluation.OperationGenerationId
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.ErrorReason
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionError
import org.gradle.internal.declarativedsl.analysis.ResolutionTrace
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.getDataType
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.dom.AmbiguousName
import org.gradle.internal.declarativedsl.dom.BlockMismatch
import org.gradle.internal.declarativedsl.dom.CrossScopeAccess
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode.ValueFactoryNode
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ErrorResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.PropertyResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ValueNodeResolution.LiteralValueResolved
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ValueNodeResolution.ValueFactoryResolution
import org.gradle.internal.declarativedsl.dom.ElementNotResolvedReason
import org.gradle.internal.declarativedsl.dom.NotAssignable
import org.gradle.internal.declarativedsl.dom.PropertyNotAssignedReason
import org.gradle.internal.declarativedsl.dom.UnresolvedBase
import org.gradle.internal.declarativedsl.dom.UnresolvedName
import org.gradle.internal.declarativedsl.dom.UnresolvedSignature
import org.gradle.internal.declarativedsl.dom.UnresolvedValueUsed
import org.gradle.internal.declarativedsl.dom.ValueFactoryNotResolvedReason
import org.gradle.internal.declarativedsl.dom.ValueTypeMismatch
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.LanguageTreeBackedDocument
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.toDocument
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.LanguageTreeResult


class DocumentWithResolution(
    val document: DeclarativeDocument,
    val resolutionContainer: DocumentResolutionContainer
)


fun documentWithResolution(
    schema: AnalysisSchema,
    languageTreeResult: LanguageTreeResult,
    operationGenerationId: OperationGenerationId,
    analysisStatementFilter: AnalysisStatementFilter,
    strictReceiverChecks: Boolean = true
): DocumentWithResolution {
    val document = languageTreeResult.toDocument()
    return DocumentWithResolution(document, resolutionContainer(
        schema,
        tracingCodeResolver(operationGenerationId, analysisStatementFilter).also { it.resolve(schema, languageTreeResult.imports, languageTreeResult.topLevelBlock) }.trace,
        document,
        strictReceiverChecks
    ))
}


fun resolutionContainer(schema: AnalysisSchema, trace: ResolutionTrace, document: LanguageTreeBackedDocument, strictReceiverChecks: Boolean = true): DocumentResolutionContainer =
    DocumentResolver(trace, SchemaTypeRefContext(schema), strictReceiverChecks).resolutionContainer(document)


internal
class DefaultDocumentResolutionContainer(
    private val elementResolution: Map<ElementNode, ElementResolution>,
    private val propertyResolution: Map<PropertyNode, PropertyResolution>,
    private val valueFactoryResolution: Map<ValueFactoryNode, ValueFactoryResolution>
) : DocumentResolutionContainer {
    override fun data(node: ElementNode): ElementResolution = elementResolution.getValue(node)
    override fun data(node: PropertyNode): PropertyResolution = propertyResolution.getValue(node)
    override fun data(node: DeclarativeDocument.DocumentNode.ErrorNode): ErrorResolution = ErrorResolution
    override fun data(value: ValueFactoryNode): ValueFactoryResolution = valueFactoryResolution.getValue(value)
    override fun data(value: DeclarativeDocument.ValueNode.LiteralValueNode): LiteralValueResolved = LiteralValueResolved(value.value)
}


private
class DocumentResolver(
    private val trace: ResolutionTrace,
    private val typeRefContext: SchemaTypeRefContext,
    private val strictReceiverChecks: Boolean
) {
    fun resolutionContainer(document: LanguageTreeBackedDocument): DefaultDocumentResolutionContainer {
        val elementResolution = mutableMapOf<ElementNode, ElementResolution>()
        val propertyResolution = mutableMapOf<PropertyNode, PropertyResolution>()
        val valueFactoryResolution = mutableMapOf<ValueFactoryNode, ValueFactoryResolution>()

        fun resolveValueFactory(valueFactoryNode: ValueFactoryNode): ValueFactoryResolution {
            val expr = document.languageTreeMappingContainer.data(valueFactoryNode)
            return when (val exprResolution = trace.expressionResolution(expr)) {
                is ResolutionTrace.ResolutionOrErrors.Resolution -> ValueFactoryResolution.ValueFactoryResolved((exprResolution.result as ObjectOrigin.FunctionOrigin).function)
                is ResolutionTrace.ResolutionOrErrors.Errors -> ValueFactoryResolution.ValueFactoryNotResolved(mapValueFactoryErrors(exprResolution.errors))
                ResolutionTrace.ResolutionOrErrors.NoResolution -> ValueFactoryResolution.ValueFactoryNotResolved(listOf(UnresolvedBase))
            }
        }

        fun visitValue(value: DeclarativeDocument.ValueNode) {
            when (value) {
                is ValueFactoryNode -> {
                    valueFactoryResolution[value] = resolveValueFactory(value)
                    value.values.forEach(::visitValue)
                }
                is DeclarativeDocument.ValueNode.LiteralValueNode -> Unit
            }
        }

        fun visitNode(node: DeclarativeDocument.DocumentNode) {
            when (node) {
                is ElementNode -> {
                    elementResolution[node] = elementResolution(document.languageTreeMappingContainer.data(node) as FunctionCall)
                    node.elementValues.forEach(::visitValue)
                    node.content.forEach(::visitNode)
                }

                is PropertyNode -> {
                    propertyResolution[node] = propertyResolution(document.languageTreeMappingContainer.data(node) as Assignment)
                    visitValue(node.value)
                }

                is DeclarativeDocument.DocumentNode.ErrorNode -> Unit
            }
        }

        document.content.forEach(::visitNode)

        return DefaultDocumentResolutionContainer(elementResolution, propertyResolution, valueFactoryResolution)
    }

    private
    fun elementResolution(statement: FunctionCall) = when (val callResolution = trace.expressionResolution(statement)) {
        is ResolutionTrace.ResolutionOrErrors.Resolution -> run {
            val functionOrigin = callResolution.result as ObjectOrigin.FunctionOrigin
            val receiver = functionOrigin.receiver
            if (strictReceiverChecks && receiver is ObjectOrigin.ImplicitThisReceiver && !receiver.isCurrentScopeReceiver) {
                return@run ElementResolution.ElementNotResolved(listOf(CrossScopeAccess))
            }
            val function = functionOrigin.function
            when (val semantics = function.semantics) {
                is FunctionSemantics.AccessAndConfigure -> {
                    val configuredType = typeRefContext.resolveRef(semantics.accessor.objectType) as DataClass
                    ElementResolution.SuccessfulElementResolution.ConfiguringElementResolved(configuredType)
                }

                is FunctionSemantics.NewObjectFunctionSemantics -> {
                    ElementResolution.SuccessfulElementResolution.ContainerElementResolved(
                        typeRefContext.resolveRef((semantics as? FunctionSemantics.ConfigureSemantics)?.configuredType ?: semantics.returnValueType),
                        function as SchemaMemberFunction,
                        false // TODO: produce proper key markers
                    )
                }

                else -> error("unexpected semantics of element function")
            }
        }

        is ResolutionTrace.ResolutionOrErrors.Errors -> ElementResolution.ElementNotResolved(mapElementErrors(callResolution.errors))
        ResolutionTrace.ResolutionOrErrors.NoResolution -> ElementResolution.ElementNotResolved(listOf(UnresolvedBase))
    }

    private
    fun propertyResolution(statement: Assignment) = when (val assignment = trace.assignmentResolution(statement)) {
        is ResolutionTrace.ResolutionOrErrors.Resolution -> {
            val receiver = assignment.result.lhs.receiverObject
            if (strictReceiverChecks && receiver is ObjectOrigin.ImplicitThisReceiver && !receiver.isCurrentScopeReceiver) {
                PropertyResolution.PropertyNotAssigned(listOf(CrossScopeAccess))
            } else {
                PropertyResolution.PropertyAssignmentResolved(typeRefContext.getDataType(receiver), assignment.result.lhs.property)
            }
        }

        is ResolutionTrace.ResolutionOrErrors.Errors -> PropertyResolution.PropertyNotAssigned(mapPropertyErrors(assignment.errors))
        ResolutionTrace.ResolutionOrErrors.NoResolution -> PropertyResolution.PropertyNotAssigned(listOf(UnresolvedBase))
    }


    private
    fun mapValueFactoryErrors(errors: Iterable<ResolutionError>): List<ValueFactoryNotResolvedReason> =
        mapElementErrors(errors).map { it as ValueFactoryNotResolvedReason } // maybe handle value factory errors separately?

    private
    fun mapPropertyErrors(errors: Iterable<ResolutionError>): List<PropertyNotAssignedReason> = errors.map {
        when (it.errorReason) {
            is ErrorReason.ExternalReassignment,
            ErrorReason.UnresolvedAssignmentLhs -> UnresolvedName

            is ErrorReason.UnresolvedReference -> UnresolvedName
            is ErrorReason.AssignmentTypeMismatch -> ValueTypeMismatch
            is ErrorReason.ReadOnlyPropertyAssignment -> NotAssignable
            ErrorReason.UnresolvedAssignmentRhs -> UnresolvedValueUsed

            ErrorReason.MissingConfigureLambda,
            ErrorReason.UnusedConfigureLambda,
            is ErrorReason.UnresolvedFunctionCallArguments,
            is ErrorReason.UnresolvedFunctionCallReceiver,
            is ErrorReason.UnresolvedFunctionCallSignature,
            is ErrorReason.DuplicateLocalValue,
            is ErrorReason.ValReassignment,
            is ErrorReason.AmbiguousFunctions,
            is ErrorReason.AmbiguousImport,
            ErrorReason.DanglingPureExpression,
            is ErrorReason.NonReadableProperty,
            ErrorReason.UnitAssignment, // TODO: should we still check for this?
            ErrorReason.AccessOnCurrentReceiverOnlyViolation -> error("not expected here")
        }
    }.distinct()

    private
    fun mapElementErrors(errors: Iterable<ResolutionError>): List<ElementNotResolvedReason> = errors.map {
        when (it.errorReason) {
            is ErrorReason.AmbiguousFunctions -> AmbiguousName

            ErrorReason.MissingConfigureLambda,
            ErrorReason.UnusedConfigureLambda -> BlockMismatch

            is ErrorReason.UnresolvedFunctionCallArguments,
            is ErrorReason.UnresolvedFunctionCallSignature -> UnresolvedSignature

            is ErrorReason.UnresolvedFunctionCallReceiver -> UnresolvedBase

            is ErrorReason.UnresolvedReference -> UnresolvedName

            is ErrorReason.ReadOnlyPropertyAssignment,
            ErrorReason.UnitAssignment,
            ErrorReason.UnresolvedAssignmentLhs,
            ErrorReason.UnresolvedAssignmentRhs,
            is ErrorReason.ExternalReassignment,
            is ErrorReason.DuplicateLocalValue,
            ErrorReason.DanglingPureExpression,
            is ErrorReason.AssignmentTypeMismatch,
            is ErrorReason.ValReassignment,
            ErrorReason.AccessOnCurrentReceiverOnlyViolation,
            is ErrorReason.NonReadableProperty,
            is ErrorReason.AmbiguousImport -> error("not expected here")
        }
    }.distinct()
}
