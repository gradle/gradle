package com.h0tk3y.kotlin.staticObjectNotation

import kotlinx.ast.common.AstSource
import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.ast.astAttachmentsOrNull
import kotlinx.ast.common.print
import kotlinx.ast.grammar.kotlin.common.KotlinGrammarParserType
import kotlinx.ast.grammar.kotlin.common.summary
import kotlinx.ast.grammar.kotlin.target.antlr.kotlin.KotlinGrammarAntlrKotlinParser

fun parseToAst(text: CharSequence) {
    val source = AstSource.String("source text", text.toString())
    val ast = KotlinGrammarAntlrKotlinParser.parse(source, KotlinGrammarParserType.kotlinScript)
    ast.summary(attachRawAst = false)
        .onSuccess { asts: List<Ast> ->  
            asts.forEach { ast ->
                ast.print()
            }
        }.onFailure { failures ->
            println(failures)
        }
}

fun main() {
    parseToAst("""
        val x = 1
        f(x)
        x = 2
    """.trimIndent())
}