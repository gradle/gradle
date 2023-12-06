package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure

class GrammarToLightTree(private val sourceIdentifier: SourceIdentifier) {

    fun script(tree: LightTree): SyntacticResult<List<ElementResult<*>>> {
        TODO()
    }

}