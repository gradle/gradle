package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.language.Block
import com.h0tk3y.kotlin.staticObjectNotation.language.BlockElement
import com.h0tk3y.kotlin.staticObjectNotation.language.DataStatement
import com.h0tk3y.kotlin.staticObjectNotation.language.SourceData
import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import kotlinx.ast.common.ast.Ast

interface LanguageTreeBuilder {
    fun build(ast: Ast, sourceIdentifier: SourceIdentifier): LanguageTreeResult

    fun build(tree: LightTree, sourceCode: String, sourceOffset: Int, sourceIdentifier: SourceIdentifier): LanguageTreeResult
}

class LanguageTreeBuilderWithTopLevelBlock(private val delegate: LanguageTreeBuilder) : LanguageTreeBuilder {
    override fun build(ast: Ast, sourceIdentifier: SourceIdentifier): LanguageTreeResult =
        build(delegate.build(ast, sourceIdentifier), ast.sourceData(sourceIdentifier))

    override fun build(
        tree: LightTree,
        sourceCode: String,
        sourceOffset: Int,
        sourceIdentifier: SourceIdentifier
    ): LanguageTreeResult =
        build(delegate.build(tree, sourceCode, sourceOffset, sourceIdentifier), tree.sourceData(sourceIdentifier, sourceCode, sourceOffset))

    private fun build(result: LanguageTreeResult, sourceData: SourceData): LanguageTreeResult {
        val (topLevelStatements, others) = result.results.partition { it is Element && it.element is BlockElement }
        val topLevelBlock = Block(topLevelStatements.map { (it as Element).element as DataStatement }, sourceData)
        return LanguageTreeResult(others + Element(topLevelBlock))
    }
}

class DefaultLanguageTreeBuilder(private val shouldSurfaceInternalErrors: Boolean = true) : LanguageTreeBuilder {
    override fun build(ast: Ast, sourceIdentifier: SourceIdentifier): LanguageTreeResult {
        if (!shouldSurfaceInternalErrors) error("keeping internal errors inside the tree is not yet supported for the kotlinx.ast parser")
        val results = GrammarToTree(sourceIdentifier).script(ast)
        return LanguageTreeResult(results.value)
    }

    override fun build(
        tree: LightTree,
        sourceCode: String,
        sourceOffset: Int,
        sourceIdentifier: SourceIdentifier
    ): LanguageTreeResult {
        val results = GrammarToLightTree(sourceIdentifier, sourceCode, sourceOffset, shouldSurfaceInternalErrors).script(tree)
        return LanguageTreeResult(results.value)
    }

}
