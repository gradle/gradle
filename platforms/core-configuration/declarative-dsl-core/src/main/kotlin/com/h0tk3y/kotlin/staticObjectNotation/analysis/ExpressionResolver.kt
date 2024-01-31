package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.language.*

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

    private fun <T : Any> literalObjectOrigin(literalExpr: Literal<T>): ObjectOrigin =
        ObjectOrigin.ConstantOrigin(literalExpr)
}