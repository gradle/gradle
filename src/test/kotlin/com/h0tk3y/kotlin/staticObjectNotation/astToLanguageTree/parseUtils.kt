package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.language.AstSourceIdentifier
import org.intellij.lang.annotations.Language

internal fun parse(@Language("kts") code: String): List<ElementResult<*>> {
    val ast = parseToAst(code)
    val defaultLanguageTreeBuilder = DefaultLanguageTreeBuilder()
    return ast.flatMap { defaultLanguageTreeBuilder.build(it, AstSourceIdentifier(it, "test")).results }
}
