package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderFactoryImpl
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderImpl
import org.jetbrains.kotlin.com.intellij.openapi.util.Ref
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure
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

fun parseToLightTree(text: CharSequence): LightTree {
    return KotlinLightParser.parse(psiBuilderFactory.createBuilder(parserDefinition, lexer, text))
}

fun main() {
    parseToLightTree(
        """
        a = 1
    """.trimIndent()
    ).print()
}