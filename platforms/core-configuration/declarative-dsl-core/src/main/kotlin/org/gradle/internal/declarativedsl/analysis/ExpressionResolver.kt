package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.Literal
import org.gradle.internal.declarativedsl.language.NamedReference
import org.gradle.internal.declarativedsl.language.Null
import org.gradle.internal.declarativedsl.language.This


interface ExpressionResolver {
    fun doResolveExpression(context: AnalysisContext, expr: Expr, expectedType: ExpectedTypeData): TypedOrigin?
}

data class TypedOrigin(
    val objectOrigin: ObjectOrigin,
    val inferredType: DataType
)


sealed interface ExpectedTypeData {
    data object NoExpectedType : ExpectedTypeData

    sealed interface HasExpectedType : ExpectedTypeData {
        val type: DataTypeRef
    }

    data class ExpectedByProperty(override val type: DataTypeRef) : HasExpectedType
    data class ExpectedByParameter(override val type: DataTypeRef) : HasExpectedType
}


class ExpressionResolverImpl(
    private val namedReferenceResolver: NamedReferenceResolver,
    private val functionCallResolver: FunctionCallResolver,
) : ExpressionResolver {
    override fun doResolveExpression(context: AnalysisContext, expr: Expr, expectedType: ExpectedTypeData): TypedOrigin? = with(context) {
        when (expr) {
            is NamedReference -> namedReferenceResolver.doResolveNamedReferenceToObjectOrigin(context, expr, expectedType)
            is FunctionCall -> functionCallResolver.doResolveFunctionCall(context, expr, expectedType)
            is Literal<*> -> literalObjectOrigin(expr)
            is Null -> TypedOrigin(ObjectOrigin.NullObjectOrigin(expr), DataTypeInternal.DefaultNullType)
            is This -> currentScopes.last().receiver.let { TypedOrigin(it, getDataType(it)) }
        }
    }

    private
    fun <T : Any> literalObjectOrigin(literalExpr: Literal<T>): TypedOrigin =
        TypedOrigin(ObjectOrigin.ConstantOrigin(literalExpr), literalExpr.type)
}
