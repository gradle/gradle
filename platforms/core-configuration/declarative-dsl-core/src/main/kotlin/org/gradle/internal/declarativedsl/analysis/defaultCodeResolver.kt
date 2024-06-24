package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.declarative.dsl.evaluation.OperationGenerationId
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.DataStatement
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.LocalValue
import org.gradle.internal.declarativedsl.language.PropertyAccess


fun defaultCodeResolver(generationId: OperationGenerationId = DefaultOperationGenerationId.finalEvaluation, elementFilter: AnalysisStatementFilter = analyzeEverything): ResolverImpl {
    return ResolverServicesContainer().run {
        analysisStatementFilter = elementFilter
        functionCallResolver = FunctionCallResolverImpl(this, this)
        expressionResolver = ExpressionResolverImpl(this, functionCallResolver)
        propertyAccessResolver = PropertyAccessResolverImpl(expressionResolver)
        errorCollector = ErrorCollectorImpl()
        statementResolver = StatementResolverImpl(propertyAccessResolver, expressionResolver, errorCollector)
        codeAnalyzer = CodeAnalyzerImpl(analysisStatementFilter, statementResolver)

        ResolverImpl(codeAnalyzer, errorCollector, generationId)
    }
}


fun tracingCodeResolver(generationId: OperationGenerationId = DefaultOperationGenerationId.finalEvaluation, elementFilter: AnalysisStatementFilter = analyzeEverything): TracingResolver {
    return ResolverServicesContainer().run {
        analysisStatementFilter = elementFilter
        functionCallResolver = FunctionCallResolverImpl(this, this)
        propertyAccessResolver = PropertyAccessResolverImpl(this)

        val tracer = ResolutionTracer(
            ExpressionResolverImpl(this, functionCallResolver),
            StatementResolverImpl(propertyAccessResolver, this, this),
            ErrorCollectorImpl()
        )
        statementResolver = tracer
        expressionResolver = tracer
        errorCollector = tracer

        codeAnalyzer = CodeAnalyzerImpl(analysisStatementFilter, statementResolver)

        TracingResolver(ResolverImpl(codeAnalyzer, errorCollector, generationId), tracer)
    }
}


private
class ResolverServicesContainer : StatementResolver, PropertyAccessResolver, ExpressionResolver, CodeAnalyzer, ErrorCollector {
    lateinit var analysisStatementFilter: AnalysisStatementFilter
    lateinit var statementResolver: StatementResolver
    lateinit var propertyAccessResolver: PropertyAccessResolver
    lateinit var functionCallResolver: FunctionCallResolver
    lateinit var expressionResolver: ExpressionResolver
    lateinit var codeAnalyzer: CodeAnalyzer
    lateinit var errorCollector: ErrorCollector

    override fun doResolveExpression(context: AnalysisContext, expr: Expr): ObjectOrigin? =
        expressionResolver.doResolveExpression(context, expr)

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

    override fun doResolveAssignment(context: AnalysisContext, assignment: Assignment): AssignmentRecord? =
        statementResolver.doResolveAssignment(context, assignment)

    override fun doResolveLocalValue(context: AnalysisContext, localValue: LocalValue) =
        statementResolver.doResolveLocalValue(context, localValue)

    override fun doResolveExpressionStatement(context: AnalysisContext, expr: Expr) =
        statementResolver.doResolveExpressionStatement(context, expr)

    override fun collect(error: ResolutionError) = errorCollector.collect(error)

    override val errors: List<ResolutionError>
        get() = errorCollector.errors
}
