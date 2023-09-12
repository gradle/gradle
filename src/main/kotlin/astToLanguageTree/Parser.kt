package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import kotlinx.ast.common.AstSource
import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.ast.astAttachmentsOrNull
import kotlinx.ast.common.print
import kotlinx.ast.grammar.kotlin.common.KotlinGrammarParserType
import kotlinx.ast.grammar.kotlin.common.summary
import kotlinx.ast.grammar.kotlin.target.antlr.kotlin.KotlinGrammarAntlrKotlinParser

fun parseToAst(text: CharSequence): List<Ast> {
    val source = AstSource.String("source text", text.toString())
    val ast = KotlinGrammarAntlrKotlinParser.parse(source, KotlinGrammarParserType.kotlinScript)
    return buildList {
        ast.summary(attachRawAst = false)
            .onSuccess { asts: List<Ast> ->
                addAll(asts)
            }.onFailure { failures ->
                println(failures)
            }
    }
}

fun main() {
    parseToAst("""
        a.b.c.x = 2
    """.trimIndent()).forEach { it.print() }
}