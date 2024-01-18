package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import kotlinx.ast.common.ast.Ast

interface LanguageTreeBuilder {
    fun build(ast: Ast, sourceIdentifier: SourceIdentifier): LanguageTreeResult
    fun build(tree: LightTree, sourceCode: String, sourceOffset: Int, sourceIdentifier: SourceIdentifier): LanguageTreeResult
}

class DefaultLanguageTreeBuilder : LanguageTreeBuilder {
    override fun build(ast: Ast, sourceIdentifier: SourceIdentifier): LanguageTreeResult =
        GrammarToTree(sourceIdentifier).script(ast)

    override fun build(
        tree: LightTree,
        sourceCode: String,
        sourceOffset: Int,
        sourceIdentifier: SourceIdentifier
    ): LanguageTreeResult =
        GrammarToLightTree(sourceIdentifier, sourceCode, sourceOffset).script(tree)
}
