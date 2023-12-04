package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.language.Block
import com.h0tk3y.kotlin.staticObjectNotation.language.DataStatement
import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import kotlinx.ast.common.ast.Ast

interface LanguageTreeBuilder {
    fun build(ast: Ast, sourceIdentifier: SourceIdentifier): LanguageTreeResult
}

class LanguageTreeBuilderWithTopLevelBlock(
    private val delegate: LanguageTreeBuilder
) : LanguageTreeBuilder {
    override fun build(ast: Ast, sourceIdentifier: SourceIdentifier): LanguageTreeResult {
        val result = delegate.build(ast, sourceIdentifier)
        val (topLevelStatements, others) = result.results.partition { it is Element && it.element is DataStatement }
        val topLevelBlock = Block(topLevelStatements.map { (it as Element).element as DataStatement }, ast.sourceData(sourceIdentifier))
        return LanguageTreeResult(others + Element(topLevelBlock))
    }
}

class DefaultLanguageTreeBuilder : LanguageTreeBuilder {
    override fun build(ast: Ast, sourceIdentifier: SourceIdentifier): LanguageTreeResult =
        when (val results = GrammarToTree(sourceIdentifier).script(ast)) {
            is FailingResult -> LanguageTreeResult(results.failures())
            is Syntactic -> LanguageTreeResult(results.value)
        }
}
