package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.declarative.dsl.evaluation.OperationGenerationId
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.DataStatement
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.LocalValue
import org.gradle.internal.declarativedsl.language.NamedReference


fun defaultCodeResolver(generationId: OperationGenerationId = DefaultOperationGenerationId.finalEvaluation, elementFilter: AnalysisStatementFilter = analyzeEverything): ResolverImpl {
    return ResolverServicesContainer().run {
        analysisStatementFilter = elementFilter
        functionCallResolver = FunctionCallResolverImpl(this, this)
        expressionResolver = ExpressionResolverImpl(this, functionCallResolver)
        namedReferenceResolver = NamedReferenceResolverImpl(expressionResolver)
        errorCollector = ErrorCollectorImpl()
        statementResolver = StatementResolverImpl(namedReferenceResolver, expressionResolver, errorCollector)
        codeAnalyzer = CodeAnalyzerImpl(analysisStatementFilter, statementResolver)

        ResolverImpl(codeAnalyzer, errorCollector, generationId)
    }
}


fun tracingCodeResolver(generationId: OperationGenerationId = DefaultOperationGenerationId.finalEvaluation, elementFilter: AnalysisStatementFilter = analyzeEverything): TracingResolver {
    return ResolverServicesContainer().run {
        analysisStatementFilter = elementFilter
        functionCallResolver = FunctionCallResolverImpl(this, this)
        namedReferenceResolver = NamedReferenceResolverImpl(this)

        val tracer = ResolutionTracer(
            ExpressionResolverImpl(this, functionCallResolver),
            StatementResolverImpl(namedReferenceResolver, this, this),
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
class ResolverServicesContainer : StatementResolver, NamedReferenceResolver, ExpressionResolver, CodeAnalyzer, ErrorCollector {
    lateinit var analysisStatementFilter: AnalysisStatementFilter
    lateinit var statementResolver: StatementResolver
    lateinit var namedReferenceResolver: NamedReferenceResolver
    lateinit var functionCallResolver: FunctionCallResolver
    lateinit var expressionResolver: ExpressionResolver
    lateinit var codeAnalyzer: CodeAnalyzer
    lateinit var errorCollector: ErrorCollector

    override fun doResolveExpression(context: AnalysisContext, expr: Expr, expectedType: DataTypeRef?): ObjectOrigin? =
        expressionResolver.doResolveExpression(context, expr, expectedType)

    override fun analyzeStatementsInProgramOrder(context: AnalysisContext, elements: List<DataStatement>) {
        codeAnalyzer.analyzeStatementsInProgramOrder(context, elements)
    }

    override fun doResolveNamedReferenceToObjectOrigin(
        analysisContext: AnalysisContext,
        namedReference: NamedReference,
        expectedType: DataTypeRef?
    ): ObjectOrigin? =
        namedReferenceResolver.doResolveNamedReferenceToObjectOrigin(analysisContext, namedReference, expectedType)

    override fun doResolveNamedReferenceToAssignable(
        analysisContext: AnalysisContext,
        namedReference: NamedReference
    ): PropertyReferenceResolution? =
        namedReferenceResolver.doResolveNamedReferenceToAssignable(analysisContext, namedReference)

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
