package org.gradle.internal.declarativedsl.analysis


import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.DataStatement
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.LocalValue


interface CodeAnalyzer {
    fun analyzeStatementsInProgramOrder(context: AnalysisContext, elements: List<DataStatement>)
}


class CodeAnalyzerImpl(
    private val analysisStatementFilter: AnalysisStatementFilter,
    private val statementResolver: StatementResolver
) : CodeAnalyzer {
    override fun analyzeStatementsInProgramOrder(
        context: AnalysisContext,
        elements: List<DataStatement>
    ) {
        for (element in elements) {
            if (analysisStatementFilter.shouldAnalyzeStatement(element, context.currentScopes)) {
                doResolveStatement(context, element)
            }
        }
    }

    private
    fun doResolveStatement(context: AnalysisContext, statement: DataStatement) {
        when (statement) {
            is Assignment -> statementResolver.doResolveAssignment(context, statement)
            is LocalValue -> statementResolver.doResolveLocalValue(context, statement)
            is Expr -> statementResolver.doResolveExpressionStatement(context, statement)
        }
    }
}
