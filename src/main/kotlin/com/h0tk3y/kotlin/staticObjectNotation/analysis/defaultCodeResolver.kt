package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.language.DataStatement
import com.h0tk3y.kotlin.staticObjectNotation.language.Expr
import com.h0tk3y.kotlin.staticObjectNotation.language.FunctionCall
import com.h0tk3y.kotlin.staticObjectNotation.language.PropertyAccess

fun defaultCodeResolver(elementFilter: AnalysisStatementFilter = analyzeEverything): ResolverImpl {
    return ResolverServicesContainer().run {
        analysisStatementFilter = elementFilter
        codeAnalyzer = CodeAnalyzerImpl(analysisStatementFilter, this, this, this)
        expressionResolver = ExpressionResolverImpl(this, this)
        functionCallResolver = FunctionCallResolverImpl(expressionResolver, codeAnalyzer)
        propertyAccessResolver = PropertyAccessResolverImpl(expressionResolver)

        ResolverImpl(codeAnalyzer)
    }
}

private class ResolverServicesContainer :
    PropertyAccessResolver,
    ExpressionResolver,
    FunctionCallResolver,
    CodeAnalyzer
{
    lateinit var analysisStatementFilter: AnalysisStatementFilter
    lateinit var propertyAccessResolver: PropertyAccessResolver
    lateinit var functionCallResolver: FunctionCallResolver
    lateinit var expressionResolver: ExpressionResolver
    lateinit var codeAnalyzer: CodeAnalyzer

    override fun doResolveExpression(context: AnalysisContext, expr: Expr): ObjectOrigin? =
        expressionResolver.doResolveExpression(context, expr)

    override fun doResolveFunctionCall(
        context: AnalysisContext,
        functionCall: FunctionCall
    ): ObjectOrigin.FunctionOrigin? =
        functionCallResolver.doResolveFunctionCall(context, functionCall)

    override fun analyzeStatementsInProgramOrder(context: AnalysisContext, elements: List<DataStatement>) {
        codeAnalyzer.analyzeStatementsInProgramOrder(context, elements)
    }

    override fun doResolvePropertyAccessToObjectOrigin(
        analysisContext: AnalysisContext,
        propertyAccess: PropertyAccess
    ): ObjectOrigin? =
        propertyAccessResolver.doResolvePropertyAccessToObjectOrigin(analysisContext, propertyAccess)

    override fun doResolvePropertyAccessToAssignable(
        analysisContext: AnalysisContext,
        propertyAccess: PropertyAccess
    ): PropertyReferenceResolution? =
        propertyAccessResolver.doResolvePropertyAccessToAssignable(analysisContext, propertyAccess)
}
