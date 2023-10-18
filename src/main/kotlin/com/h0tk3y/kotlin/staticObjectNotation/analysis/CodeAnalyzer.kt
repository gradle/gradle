package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.language.*

interface CodeAnalyzer {
    fun analyzeCodeInProgramOrder(context: AnalysisContext, elements: List<LanguageTreeElement>)
}

class CodeAnalyzerImpl(
    private val expressionResolver: ExpressionResolver,
    private val propertyAccessResolver: PropertyAccessResolver,
    // TODO: get rid of this in favor of just expressionResolver?
    private val functionCallResolver: FunctionCallResolver
) : CodeAnalyzer {
    
    override fun analyzeCodeInProgramOrder(
        context: AnalysisContext, 
        elements: List<LanguageTreeElement>
    ) = with(context) {
        for (element in elements) {
            when (element) {
                is Assignment -> doAnalyzeAssignment(element)
                is FunctionCall -> functionCallResolver.doResolveFunctionCall(context, element).also { result ->
                    if (result != null && isDanglingPureExpression(result))
                        errorCollector(ResolutionError(element, ErrorReason.DanglingPureExpression))
                }

                is LocalValue -> doAnalyzeLocal(element)
                is PropertyAccess, is Literal<*>, is Block -> errorCollector(
                    ResolutionError(
                        element,
                        ErrorReason.DanglingPureExpression
                    )
                )

                is Expr -> {
                    errorCollector(ResolutionError(element, ErrorReason.DanglingPureExpression))
                }

                is Import -> error("unexpected import in program code")
                is FunctionArgument -> error("function arguments should not appear in top-level trees")
            }
        }
    }

    private fun AnalysisContext.doAnalyzeAssignment(assignment: Assignment) {
        val lhsResolution =
            propertyAccessResolver.doResolvePropertyAccessToAssignable(this, assignment.lhs)
        if (lhsResolution == null) {
            errorCollector(ResolutionError(assignment.lhs, ErrorReason.UnresolvedAssignmentLhs))
        } else {
            var hasErrors = false
            if (lhsResolution.property.isReadOnly) {
                errorCollector(ResolutionError(assignment.rhs, ErrorReason.ReadOnlyPropertyAssignment))
                hasErrors = true
            }
            val rhsResolution = expressionResolver.doResolveExpression(this, assignment.rhs)
            if (rhsResolution == null) {
                errorCollector(ResolutionError(assignment.rhs, ErrorReason.UnresolvedAssignmentRhs))
            } else {
                val rhsType = getDataType(rhsResolution)
                val lhsExpectedType = resolveRef(lhsResolution.property.type)
                if (rhsType == DataType.UnitType) {
                    errorCollector(ResolutionError(assignment, ErrorReason.UnitAssignment))
                    hasErrors = true
                }
                if (!checkIsAssignable(rhsType, lhsExpectedType)) {
                    errorCollector(
                        ResolutionError(assignment, ErrorReason.AssignmentTypeMismatch(lhsExpectedType, rhsType))
                    )
                    hasErrors = true
                }

                if (!hasErrors) {
                    recordAssignment(lhsResolution, rhsResolution)
                }
            }
        }
    }

    private fun AnalysisContext.doAnalyzeLocal(localValue: LocalValue) {
        val rhs = expressionResolver.doResolveExpression(this, localValue.rhs)
        if (rhs == null) {
            errorCollector(ResolutionError(localValue, ErrorReason.UnresolvedAssignmentRhs))
        } else {
            if (getDataType(rhs) == DataType.UnitType) {
                errorCollector(ResolutionError(localValue, ErrorReason.UnitAssignment))
            }
            currentScopes.last().declareLocal(localValue, rhs, errorCollector)
        }
    }

    // If we can trace the function invocation back to something that is not transient, we consider it not dangling
    private fun isDanglingPureExpression(obj: ObjectOrigin.FunctionInvocationOrigin): Boolean {
        fun isPotentiallyPersistentReceiver(objectOrigin: ObjectOrigin): Boolean = when (objectOrigin) {
            is ObjectOrigin.ConfigureReceiver -> true
            is ObjectOrigin.ConstantOrigin -> false
            is ObjectOrigin.External -> true
            is ObjectOrigin.NewObjectFromFunctionInvocation -> {
                val semantics = objectOrigin.function.semantics
                when (semantics) {
                    is FunctionSemantics.Builder -> error("should be impossible?")
                    is FunctionSemantics.AccessAndConfigure -> true
                    is FunctionSemantics.AddAndConfigure -> true
                    is FunctionSemantics.Pure -> false
                }
            }

            is ObjectOrigin.FromLocalValue -> true // TODO: also check for unused val?
            is ObjectOrigin.BuilderReturnedReceiver -> isPotentiallyPersistentReceiver(objectOrigin.receiverObject)
            is ObjectOrigin.NullObjectOrigin -> false
            is ObjectOrigin.PropertyReference -> true
            is ObjectOrigin.TopLevelReceiver -> true
            is ObjectOrigin.PropertyDefaultValue -> true
        }

        return when {
            obj.function.semantics is FunctionSemantics.Pure -> true
            obj is ObjectOrigin.BuilderReturnedReceiver -> !isPotentiallyPersistentReceiver(obj.receiverObject)
            else -> false
        }
    }
}