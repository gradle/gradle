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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.ExpectedTypeData.ExpectedByProperty
import org.gradle.internal.declarativedsl.analysis.ExpectedTypeData.NoExpectedType
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.AugmentingAssignment
import org.gradle.internal.declarativedsl.language.DataStatement
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.LocalValue
import org.gradle.internal.declarativedsl.language.NamedReference


interface StatementResolver {
    fun doResolveAssignment(context: AnalysisContext, assignment: Assignment): AssignmentRecord?
    fun doResolveAugmentingAssignment(context: AnalysisContext, assignment: AugmentingAssignment): AssignmentRecord?
    fun doResolveLocalValue(context: AnalysisContext, localValue: LocalValue)
    fun doResolveExpressionStatement(context: AnalysisContext, expr: Expr)
}


class StatementResolverImpl(
    private val namedReferenceResolver: NamedReferenceResolver,
    private val functionCallResolver: FunctionCallResolver,
    private val expressionResolver: ExpressionResolver,
    private val errorCollector: ErrorCollector
) : StatementResolver {

    override fun doResolveAssignment(context: AnalysisContext, assignment: Assignment): AssignmentRecord? =
        context.doAnalyzeAssignmentLikeStatement(assignment, assignment.lhs, assignment.rhs) { lhsResolution: PropertyReferenceResolution, rhsResolution: TypedOrigin ->
            context.recordAssignment(lhsResolution, rhsResolution, AssignmentMethod.Property, assignment)
        }

    override fun doResolveAugmentingAssignment(
        context: AnalysisContext,
        assignment: AugmentingAssignment
    ): AssignmentRecord? = context.doAnalyzeAssignmentLikeStatement(assignment, assignment.lhs, assignment.rhs) { lhsResolution: PropertyReferenceResolution, rhsResolution: TypedOrigin ->
        val augmentedProperty = ObjectOrigin.PropertyReference(lhsResolution.receiverObject, lhsResolution.property, assignment.lhs)
        val augmentationCallResult = functionCallResolver.doResolveAugmentation(
            augmentedProperty, rhsResolution, context, assignment.augmentationKind, assignment
        )
        if (augmentationCallResult != null) {
            val augmentedOrigin = ObjectOrigin.AugmentationOrigin(
                augmentedProperty = augmentedProperty,
                augmentationOperand = rhsResolution.objectOrigin,
                assignment.augmentationKind,
                augmentationCallResult.objectOrigin,
                assignment
            )
            context.recordAugmentingAssignment(lhsResolution, augmentedOrigin, assignment)
        } else {
            val propertyType = context.resolveRef(lhsResolution.property.valueType)
            errorCollector.collect(ResolutionError(assignment, ErrorReason.AugmentingAssignmentNotResolved(propertyType)))
            null
        }
    }

    override fun doResolveLocalValue(context: AnalysisContext, localValue: LocalValue) = context.doAnalyzeLocal(localValue)

    override fun doResolveExpressionStatement(context: AnalysisContext, expr: Expr) {
        val resolvedExpr = expressionResolver.doResolveExpression(context, expr, NoExpectedType)

        when (expr) {
            is FunctionCall -> {
                val objectOrigin = resolvedExpr?.objectOrigin
                if (objectOrigin is ObjectOrigin.FunctionOrigin && isDanglingPureCall(objectOrigin))
                    errorCollector.collect(ResolutionError(expr, ErrorReason.DanglingPureExpression))
            }

            else -> errorCollector.collect(ResolutionError(expr, ErrorReason.DanglingPureExpression))
        }
    }

    private fun AnalysisContext.doAnalyzeAssignmentLikeStatement(
        statement: DataStatement,
        lhs: NamedReference,
        rhs: Expr,
        doRecordAssignment: (PropertyReferenceResolution, TypedOrigin) -> AssignmentRecord?
    ): AssignmentRecord? {
        val lhsResolution = namedReferenceResolver.doResolveNamedReferenceToAssignable(this, lhs)

        return if (lhsResolution == null) {
            errorCollector.collect(ResolutionError(lhs, ErrorReason.UnresolvedReference(lhs)))
            errorCollector.collect(ResolutionError(statement, ErrorReason.UnresolvedAssignmentLhs))
            null
        } else {
            var hasErrors = false
            if (lhsResolution.property.isReadOnly) {
                errorCollector.collect(ResolutionError(statement, ErrorReason.ReadOnlyPropertyAssignment(lhsResolution.property)))
                hasErrors = true
            }
            val rhsResolution = expressionResolver.doResolveExpression(this, rhs, ExpectedByProperty(lhsResolution.property.valueType))
            if (rhsResolution == null) {
                errorCollector.collect(ResolutionError(statement, ErrorReason.UnresolvedAssignmentRhs))
                null
            } else {
                val rhsType = rhsResolution.inferredType
                val lhsExpectedType = resolveRef(lhsResolution.property.valueType)
                if (rhsType is DataType.UnitType) {
                    errorCollector.collect(ResolutionError(statement, ErrorReason.UnitAssignment))
                    hasErrors = true
                }
                val typeSubstitution = computeGenericTypeSubstitution(lhsExpectedType.ref, rhsType.ref) ?: emptyMap()
                if (!checkIsAssignable(rhsType, lhsExpectedType, typeSubstitution)) {
                    errorCollector.collect(
                        ResolutionError(statement, ErrorReason.AssignmentTypeMismatch(lhsExpectedType, rhsType))
                    )
                    hasErrors = true
                }

                if (!hasErrors) {
                    doRecordAssignment(lhsResolution, rhsResolution)
                } else null
            }
        }
    }

    private
    fun AnalysisContext.doAnalyzeLocal(localValue: LocalValue) {
        val rhs = expressionResolver.doResolveExpression(this, localValue.rhs, NoExpectedType)
        if (rhs == null) {
            errorCollector.collect(ResolutionError(localValue, ErrorReason.UnresolvedAssignmentRhs))
        } else {
            if (rhs.inferredType is DataType.UnitType) {
                errorCollector.collect(ResolutionError(localValue, ErrorReason.UnitAssignment))
            }
            currentScopes.last().declareLocal(localValue, rhs.objectOrigin, errorCollector)
        }
    }

    // If we can trace the function invocation back to something that is not transient, we consider it not dangling
    private
    fun isDanglingPureCall(obj: ObjectOrigin.FunctionOrigin): Boolean {
        fun isPotentiallyPersistentReceiver(objectOrigin: ObjectOrigin): Boolean = when (objectOrigin) {
            is ObjectOrigin.AccessAndConfigureReceiver -> true
            is ObjectOrigin.ImplicitThisReceiver -> true
            is ObjectOrigin.FromLocalValue -> true // TODO: also check for unused val?
            is ObjectOrigin.DelegatingObjectOrigin -> isPotentiallyPersistentReceiver(objectOrigin.delegate)
            is ObjectOrigin.ConstantOrigin -> false
            is ObjectOrigin.EnumConstantOrigin -> false
            is ObjectOrigin.GroupedVarargValue -> false
            is ObjectOrigin.External -> true
            is ObjectOrigin.FunctionOrigin -> {
                val semantics = objectOrigin.function.semantics
                when (semantics) {
                    is FunctionSemantics.Builder -> error("should be impossible?")
                    is FunctionSemantics.AccessAndConfigure -> true
                    is FunctionSemantics.AddAndConfigure -> true
                    is FunctionSemantics.Pure -> false
                }
            }

            is ObjectOrigin.NullObjectOrigin -> false
            is ObjectOrigin.PropertyReference -> true
            is ObjectOrigin.TopLevelReceiver -> true
            is ObjectOrigin.PropertyDefaultValue -> true
            is ObjectOrigin.CustomConfigureAccessor -> true
        }

        return when {
            obj.function.semantics is FunctionSemantics.Pure -> true
            obj is ObjectOrigin.BuilderReturnedReceiver -> !isPotentiallyPersistentReceiver(obj.receiver)
            else -> false
        }
    }
}
