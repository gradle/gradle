package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.language.Block
import com.h0tk3y.kotlin.staticObjectNotation.language.DataStatement
import com.h0tk3y.kotlin.staticObjectNotation.language.Import
import com.h0tk3y.kotlin.staticObjectNotation.language.LanguageTreeElement

interface Resolver {
    fun resolve(schema: AnalysisSchema, trees: List<LanguageTreeElement>): ResolutionResult
}

class ResolverImpl(
    internal val codeAnalyzer: CodeAnalyzer
) : Resolver {
    override fun resolve(schema: AnalysisSchema, trees: List<LanguageTreeElement>): ResolutionResult {
        val topLevelBlock = trees.singleOrNull { it is Block } as? Block
        require(trees.none { it is DataStatement } && topLevelBlock != null) { "expected a top-level block" }

        val errors = mutableListOf<ResolutionError>()
        val errorCollector: (ResolutionError) -> Unit = { errors.add(it) }

        val importContext = AnalysisContext(schema, emptyMap(), errorCollector)
        val importFqnBySimpleName = collectImports(
            trees.filterIsInstance<Import>(), importContext
        ) + schema.defaultImports.associateBy { it.simpleName }

        with(AnalysisContext(schema, importFqnBySimpleName, errorCollector)) {
            val topLevelReceiver = ObjectOrigin.TopLevelReceiver(schema.topLevelReceiverType, topLevelBlock)
            val topLevelScope = AnalysisScope(null, topLevelReceiver, topLevelBlock)
            withScope(topLevelScope) {
                codeAnalyzer.analyzeCodeInProgramOrder(this, topLevelBlock.statements)
            }
            return ResolutionResult(topLevelReceiver, assignments, additions, errors)
        }
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
                    analysisContext.errorCollector(ResolutionError(import, ErrorReason.AmbiguousImport(fqn)))
                    existing
                } else {
                    fqn
                }
            }
        }
    }
}
