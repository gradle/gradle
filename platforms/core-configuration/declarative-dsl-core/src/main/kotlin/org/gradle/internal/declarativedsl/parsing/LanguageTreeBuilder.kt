package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier


interface LanguageTreeBuilder {
    fun build(parsedLightTree: ParsedLightTree, sourceIdentifier: SourceIdentifier): LanguageTreeResult
}


class DefaultLanguageTreeBuilder : LanguageTreeBuilder {
    override fun build(
        parsedLightTree: ParsedLightTree,
        sourceIdentifier: SourceIdentifier
    ): LanguageTreeResult =
        GrammarToTree(sourceIdentifier, parsedLightTree.wrappedCode, parsedLightTree.originalCodeOffset, parsedLightTree.suffixLength)
            .script(parsedLightTree.lightTree)
}
