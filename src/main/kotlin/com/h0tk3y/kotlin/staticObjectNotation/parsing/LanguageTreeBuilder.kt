package com.h0tk3y.kotlin.staticObjectNotation.parsing

import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier

interface LanguageTreeBuilder {
    fun build(tree: LightTree, sourceCode: String, sourceOffset: Int, sourceIdentifier: SourceIdentifier): LanguageTreeResult
}

class DefaultLanguageTreeBuilder : LanguageTreeBuilder {
    override fun build(
        tree: LightTree,
        sourceCode: String,
        sourceOffset: Int,
        sourceIdentifier: SourceIdentifier
    ): LanguageTreeResult =
        GrammarToTree(sourceIdentifier, sourceCode, sourceOffset).script(tree)
}
