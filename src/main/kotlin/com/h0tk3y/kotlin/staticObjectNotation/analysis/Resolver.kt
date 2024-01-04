package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.language.Block
import com.h0tk3y.kotlin.staticObjectNotation.language.DataStatement
import com.h0tk3y.kotlin.staticObjectNotation.language.Import
import com.h0tk3y.kotlin.staticObjectNotation.language.LanguageTreeElement

interface Resolver {
    fun resolve(schema: AnalysisSchema, trees: List<LanguageTreeElement>): ResolutionResult
}

class ResolverImpl(
    private val codeAnalyzer: CodeAnalyzer,
    private val errorCollector: ErrorCollector
) : Resolver {
    override fun resolve(schema: AnalysisSchema, trees: List<LanguageTreeElement>): ResolutionResult {
        val topLevelBlock = trees.singleOrNull { it is Block } as? Block
        require(trees.none { it is DataStatement } && topLevelBlock != null) { "expected a top-level block" }

        val importContext = AnalysisContext(schema, emptyMap(), errorCollector)
        val importFqnBySimpleName = collectImports(
            trees.filterIsInstance<Import>(), importContext
        ) + schema.defaultImports.associateBy { it.simpleName }

        val topLevelReceiver = ObjectOrigin.TopLevelReceiver(schema.topLevelReceiverType, topLevelBlock)
        val topLevelScope = AnalysisScope(null, topLevelReceiver, topLevelBlock)

        val context = AnalysisContext(schema, importFqnBySimpleName, errorCollector)
        context.withScope(topLevelScope) { codeAnalyzer.analyzeStatementsInProgramOrder(context, topLevelBlock.statements) }

        return ResolutionResult(topLevelReceiver, context.assignments, context.additions, errorCollector.errors)
    }

    fun collectImports(
        trees: List<Import>,
        analysisContext: AnalysisContext
    ): Map<String, FqName> = buildMap {
        trees.forEach { import ->
            val fqn = FqName(
                import.name.nameParts.dropLast(1).joinToString("."), import.name.nameParts.last()
            )

            compute(fqn.simpleName) { _, existing ->
                if (existing != null && existing != fqn) {
                    analysisContext.errorCollector.collect(ResolutionError(import, ErrorReason.AmbiguousImport(fqn)))
                    existing
                } else {
                    fqn
                }
            }
        }
    }
}

/**
 * The only purpose of this class is to expose the resolution [trace] to the consumers.
 */
class TracingResolver(
    private val resolver: Resolver,
    val trace: ResolutionTrace
) : Resolver {
    override fun resolve(schema: AnalysisSchema, trees: List<LanguageTreeElement>): ResolutionResult = resolver.resolve(schema, trees)
}
