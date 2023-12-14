package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderFactoryImpl
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.parsing.KotlinLightParser
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.utils.doNothing

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

private fun wrapScriptIntoClassInitializerBlock(@Language("kts") code: String): String {
    val packageStatements = mutableListOf<String>()
    val importStatements = mutableListOf<String>()
    val codeStatements = mutableListOf<String>()

    code.lines().forEach {line ->
        when {
            line.startsWith("import") -> importStatements.add(line)
            line.startsWith("package") -> packageStatements.add(line)
            line.isBlank() -> doNothing()
            else -> codeStatements.add(line)
        } // TODO: ugly, brittle hack...
    }

    if (packageStatements.size > 2) error("Multiple package statements")

    fun addNewlineIfNotBlank(it: String) = when {
        it.isNotBlank() -> it + "\n"
        else -> it
    }

    val packageSection = packageStatements.joinToString("") { addNewlineIfNotBlank(it) }
    val importSection = importStatements.joinToString("") { addNewlineIfNotBlank(it) }
    val codeSection = codeStatements.joinToString("") { addNewlineIfNotBlank(it) }

    return "${packageSection}${importSection}class Script {init {$codeSection}}"
}

