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

package org.gradle.internal.declarativedsl.dom

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.analysis.ErrorReason
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionError
import org.gradle.internal.declarativedsl.analysis.ResolutionTrace
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.getDataType
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ValueResolution.ValueFactoryResolution
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.SourceIdentifier


fun resolvedDocument(
    schema: AnalysisSchema,
    languageTreeResult: LanguageTreeResult,
    analysisStatementFilter: AnalysisStatementFilter,
    strictReceiverChecks: Boolean = true
): ResolvedDeclarativeDocument {
    val tracingResolver = tracingCodeResolver(analysisStatementFilter)
    tracingResolver.resolve(schema, languageTreeResult.imports, languageTreeResult.topLevelBlock)
    return resolvedDocument(schema, tracingResolver.trace, languageTreeResult.toDocument(), strictReceiverChecks)
}


fun resolvedDocument(
    schema: AnalysisSchema,
    trace: ResolutionTrace,
    document: DeclarativeDocument,
    strictReceiverChecks: Boolean = true,
): ResolvedDeclarativeDocument {
    val resolver = DocumentResolver(trace, SchemaTypeRefContext(schema), strictReceiverChecks)
    return ResolvedDeclarativeDocumentImpl(
        document.content.map(resolver::resolvedNode),
        document.sourceIdentifier
    )
}


private
class DocumentResolver(
    private val trace: ResolutionTrace,
    private val typeRefContext: SchemaTypeRefContext,
    private val strictReceiverChecks: Boolean
) {
    fun resolvedNode(node: DeclarativeDocument.DocumentNode): ResolvedDeclarativeDocumentImpl.ResolvedNode = when (node) {
        is DeclarativeDocument.DocumentNode.ElementNode -> resolvedElement(node)
        is DeclarativeDocument.DocumentNode.PropertyNode -> resolvedProperty(node)
        is DeclarativeDocument.DocumentNode.ErrorNode -> ResolvedDeclarativeDocumentImpl.ResolvedNode.ResolvedError(node, node.sourceData)
    }

    private
    fun resolvedElement(elementNode: DeclarativeDocument.DocumentNode.ElementNode): ResolvedDeclarativeDocumentImpl.ResolvedNode.ResolvedElement {
        val statement = elementNode.blockElement() as FunctionCall
        val elementResolution = when (val callResolution = trace.expressionResolution(statement)) {
            is ResolutionTrace.ResolutionOrErrors.Resolution -> run {
                val functionOrigin = callResolution.result as ObjectOrigin.FunctionOrigin
                val receiver = functionOrigin.receiver
                if (strictReceiverChecks && receiver is ObjectOrigin.ImplicitThisReceiver && !receiver.isCurrentScopeReceiver) {
                    return@run DocumentResolution.ElementResolution.ElementNotResolved(listOf(CrossScopeAccess))
                }
                val function = functionOrigin.function
                when (val semantics = function.semantics) {
                    is FunctionSemanticsInternal.AccessAndConfigure -> {
                        val configuredType = typeRefContext.resolveRef(semantics.accessor.objectType) as DefaultDataClass
                        DocumentResolution.ElementResolution.SuccessfulElementResolution.PropertyConfiguringElementResolved(configuredType)
                    }

                    is FunctionSemanticsInternal.NewObjectFunctionSemantics -> {
                        DocumentResolution.ElementResolution.SuccessfulElementResolution.ContainerElementResolved(
                            typeRefContext.resolveRef((semantics as? FunctionSemanticsInternal.ConfigureSemantics)?.configuredType ?: semantics.returnValueType),
                            function as SchemaMemberFunction,
                            false // TODO: produce proper key markers
                        )
                    }

                    else -> error("unexpected semantics of element function")
                }
            }

            is ResolutionTrace.ResolutionOrErrors.Errors -> DocumentResolution.ElementResolution.ElementNotResolved(mapElementErrors(callResolution.errors))
            ResolutionTrace.ResolutionOrErrors.NoResolution -> DocumentResolution.ElementResolution.ElementNotResolved(listOf(UnresolvedBase))
        }
        val content = elementNode.content.map(::resolvedNode)
        val args = elementNode.elementValues.map(::resolvedValue)
        return ResolvedDeclarativeDocumentImpl.ResolvedNode.ResolvedElement(elementNode.name, content, elementResolution, args, elementNode.sourceData)
    }

    private
    fun resolvedProperty(propertyNode: DeclarativeDocument.DocumentNode.PropertyNode): ResolvedDeclarativeDocumentImpl.ResolvedNode.ResolvedProperty {
        val statement = propertyNode.blockElement() as Assignment
        val resolution = when (val assignment = trace.assignmentResolution(statement)) {
            is ResolutionTrace.ResolutionOrErrors.Resolution -> {
                val receiver = assignment.result.lhs.receiverObject
                if (strictReceiverChecks && receiver is ObjectOrigin.ImplicitThisReceiver && !receiver.isCurrentScopeReceiver) {
                    DocumentResolution.PropertyResolution.PropertyNotAssigned(listOf(CrossScopeAccess))
                } else {
                    DocumentResolution.PropertyResolution.PropertyAssignmentResolved(typeRefContext.getDataType(receiver), assignment.result.lhs.property)
                }
            }

            is ResolutionTrace.ResolutionOrErrors.Errors -> DocumentResolution.PropertyResolution.PropertyNotAssigned(mapPropertyErrors(assignment.errors))
            ResolutionTrace.ResolutionOrErrors.NoResolution -> DocumentResolution.PropertyResolution.PropertyNotAssigned(listOf(UnresolvedBase))
        }
        val value = resolvedValue(propertyNode.value)
        return ResolvedDeclarativeDocumentImpl.ResolvedNode.ResolvedProperty(propertyNode.name, value, resolution, propertyNode.sourceData)
    }

    fun resolvedValue(value: DeclarativeDocument.ValueNode): ResolvedDeclarativeDocumentImpl.ResolvedValue = when (value) {
        is DeclarativeDocument.ValueNode.LiteralValueNode -> ResolvedDeclarativeDocumentImpl.ResolvedValue.ResolvedLiteral(value.value, value.sourceData)
        is DeclarativeDocument.ValueNode.ValueFactoryNode -> {
            val args = value.values.map(::resolvedValue)
            ResolvedDeclarativeDocumentImpl.ResolvedValue.ResolvedValueFactory(value.factoryName, resolveValueFactory(value), args, value.sourceData)
        }
    }

    private
    fun resolveValueFactory(valueFactoryNode: DeclarativeDocument.ValueNode.ValueFactoryNode): ValueFactoryResolution {
        val expr = valueFactoryNode.expr()
        return when (val exprResolution = trace.expressionResolution(expr)) {
            is ResolutionTrace.ResolutionOrErrors.Resolution -> ValueFactoryResolution.ValueFactoryResolved((exprResolution.result as ObjectOrigin.FunctionOrigin).function)
            is ResolutionTrace.ResolutionOrErrors.Errors -> ValueFactoryResolution.ValueFactoryNotResolved(mapValueFactoryErrors(exprResolution.errors))
            ResolutionTrace.ResolutionOrErrors.NoResolution -> ValueFactoryResolution.ValueFactoryNotResolved(listOf(UnresolvedBase))
        }
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


private
class ResolvedDeclarativeDocumentImpl(
    override val content: Collection<ResolvedNode>,
    override val sourceIdentifier: SourceIdentifier
) : ResolvedDeclarativeDocument {
    sealed interface ResolvedNode : ResolvedDeclarativeDocument.ResolvedDocumentNode {
        data class ResolvedProperty(
            override val name: String,
            override val value: ResolvedDeclarativeDocument.ResolvedValueNode,
            override val resolution: DocumentResolution.PropertyResolution,
            override val sourceData: SourceData
        ) : ResolvedDeclarativeDocument.ResolvedDocumentNode.ResolvedPropertyNode, ResolvedNode

        data class ResolvedElement(
            override val name: String,
            override val content: Collection<ResolvedNode>,
            override val resolution: DocumentResolution.ElementResolution,
            override val elementValues: Collection<ResolvedDeclarativeDocument.ResolvedValueNode>,
            override val sourceData: SourceData
        ) : ResolvedDeclarativeDocument.ResolvedDocumentNode.ResolvedElementNode, ResolvedNode

        data class ResolvedError(
            val error: DeclarativeDocument.DocumentNode.ErrorNode,
            override val sourceData: SourceData
        ) : ResolvedDeclarativeDocument.ResolvedDocumentNode.ResolvedErrorNode, ResolvedNode {
            override val errors: Collection<DocumentError>
                get() = error.errors

            override val resolution: DocumentResolution
                get() = DocumentResolution.ErrorResolution
        }
    }

    sealed interface ResolvedValue : ResolvedDeclarativeDocument.ResolvedValueNode {
        data class ResolvedLiteral(
            override val value: Any,
            override val sourceData: SourceData
        ) : ResolvedDeclarativeDocument.ResolvedValueNode.ResolvedLiteralValueNode, ResolvedValue

        data class ResolvedValueFactory(
            override val factoryName: String,
            override val resolution: ValueFactoryResolution,
            override val values: List<ResolvedDeclarativeDocument.ResolvedValueNode>,
            override val sourceData: SourceData
        ) : ResolvedDeclarativeDocument.ResolvedValueNode.ResolvedValueFactoryNode, ResolvedValue
    }
}
