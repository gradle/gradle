package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier


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
