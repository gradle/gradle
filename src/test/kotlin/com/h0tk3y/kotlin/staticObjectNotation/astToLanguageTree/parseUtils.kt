package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import org.intellij.lang.annotations.Language

internal fun parse(@Language("kts") code: String): List<ElementResult<*>> {
    val ast = parseToAst(code)
    val defaultLanguageTreeBuilder = DefaultLanguageTreeBuilder()
    return ast.flatMap { defaultLanguageTreeBuilder.build(it).results }
}

internal fun parseToLanguage(@Language("kts") code: String): LanguageTreeResult {
    val ast = parseToAst(code)
    val builder = LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder())
    return builder.build(ast.single())
}