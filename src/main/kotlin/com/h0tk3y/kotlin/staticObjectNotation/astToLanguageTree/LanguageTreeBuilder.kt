package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.language.Block
import com.h0tk3y.kotlin.staticObjectNotation.language.DataStatement
import kotlinx.ast.common.ast.Ast

interface LanguageTreeBuilder {
    fun build(ast: Ast): LanguageTreeResult
}

class LanguageTreeBuilderWithTopLevelBlock(
    private val delegate: LanguageTreeBuilder
) : LanguageTreeBuilder {
    override fun build(ast: Ast): LanguageTreeResult {
        val result = delegate.build(ast)
        val (topLevelStatements, others) = result.results.partition { it is Element && it.element is DataStatement }
        val topLevelBlock = Block(topLevelStatements.map { (it as Element).element as DataStatement }, ast)
        return LanguageTreeResult(others + Element(topLevelBlock))
    }
}

class DefaultLanguageTreeBuilder : LanguageTreeBuilder {
    override fun build(ast: Ast): LanguageTreeResult = LanguageTreeResult(GrammarToTree.script(ast))
}