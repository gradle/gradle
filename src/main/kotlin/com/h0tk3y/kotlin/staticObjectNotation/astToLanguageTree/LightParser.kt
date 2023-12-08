package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderFactoryImpl
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.parsing.KotlinLightParser
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

private val lexer by lazy {
    KotlinLexer()
}

private val parserDefinition by lazy {
    KotlinParserDefinition()
}

private val psiBuilderFactory by lazy {
    PsiBuilderFactoryImpl()
}

fun parseToLightTree(@Language("kts") code: String): LightTree {
    val wrappedCode = wrapScriptIntoClassInitializerBlock(code)
    return KotlinLightParser.parse(psiBuilderFactory.createBuilder(parserDefinition, lexer, wrappedCode))
}

fun main() {
    parseToLightTree(
        """
            #!/usr/bin/env kscript
        a = 1
    """.trimIndent()
    ).print()
}

private fun wrapScriptIntoClassInitializerBlock(@Language("kts") code: String) =
    "class Script {init {$code}}" // TODO: this will not work for import and package statements