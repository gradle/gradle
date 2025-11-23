package org.gradle.internal.declarativedsl.parsing

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderFactoryImpl
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.parsing.KotlinLightParser
import org.jetbrains.kotlin.parsing.KotlinParserDefinition


private
val parserDefinition by lazy {
    KotlinParserDefinition()
}


private
val psiBuilderFactory by lazy {
    PsiBuilderFactoryImpl()
}


data class ParsedLightTree(
    val lightTree: LightTree,
    val code: String,
)


fun parse(@Language("dcl") code: String): ParsedLightTree {
    val lexer = KotlinLexer()
    return ParsedLightTree(
        KotlinLightParser.parse(psiBuilderFactory.createBuilder(parserDefinition, lexer, code), isScript = true),
        code,
    )
}
