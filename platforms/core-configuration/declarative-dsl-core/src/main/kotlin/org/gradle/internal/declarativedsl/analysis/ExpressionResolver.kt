package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.Literal
import org.gradle.internal.declarativedsl.language.Null
import org.gradle.internal.declarativedsl.language.NamedReference
import org.gradle.internal.declarativedsl.language.This


interface ExpressionResolver {
    fun doResolveExpression(context: AnalysisContext, expr: Expr, expectedType: DataTypeRef?): ObjectOrigin?
}


class ExpressionResolverImpl(
    private val namedReferenceResolver: NamedReferenceResolver,
    private val functionCallResolver: FunctionCallResolver,
) : ExpressionResolver {
    override fun doResolveExpression(context: AnalysisContext, expr: Expr, expectedType: DataTypeRef?): ObjectOrigin? = with(context) {
        when (expr) {
            is NamedReference -> namedReferenceResolver.doResolveNamedReferenceToObjectOrigin(context, expr, expectedType)
            is FunctionCall -> functionCallResolver.doResolveFunctionCall(context, expr, expectedType)
            is Literal<*> -> literalObjectOrigin(expr)
            is Null -> ObjectOrigin.NullObjectOrigin(expr)
            is This -> currentScopes.last().receiver
        }
    }

    private
    fun <T : Any> literalObjectOrigin(literalExpr: Literal<T>): ObjectOrigin =
        ObjectOrigin.ConstantOrigin(literalExpr)
}
