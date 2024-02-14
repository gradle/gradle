package org.gradle.internal.declarativedsl.analysis

import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.Literal
import org.gradle.internal.declarativedsl.language.Null
import org.gradle.internal.declarativedsl.language.PropertyAccess
import org.gradle.internal.declarativedsl.language.This


interface ExpressionResolver {
    fun doResolveExpression(context: AnalysisContext, expr: Expr): ObjectOrigin?
}


class ExpressionResolverImpl(
    private val propertyAccessResolver: PropertyAccessResolver,
    private val functionCallResolver: FunctionCallResolver,
) : ExpressionResolver {
    override fun doResolveExpression(context: AnalysisContext, expr: Expr): ObjectOrigin? = with(context) {
        when (expr) {
            is PropertyAccess -> propertyAccessResolver.doResolvePropertyAccessToObjectOrigin(context, expr)
            is FunctionCall -> functionCallResolver.doResolveFunctionCall(context, expr)
            is Literal<*> -> literalObjectOrigin(expr)
            is Null -> ObjectOrigin.NullObjectOrigin(expr)
            is This -> currentScopes.last().receiver
        }
    }

    private
    fun <T : Any> literalObjectOrigin(literalExpr: Literal<T>): ObjectOrigin =
        ObjectOrigin.ConstantOrigin(literalExpr)
}
