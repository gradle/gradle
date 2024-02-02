package org.gradle.internal.declarativedsl.parsing

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderFactoryImpl
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.parsing.KotlinLightParser
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.utils.doNothing


private
val lexer by lazy {
    KotlinLexer()
}


private
val parserDefinition by lazy {
    KotlinParserDefinition()
}


private
val psiBuilderFactory by lazy {
    PsiBuilderFactoryImpl()
}


fun parse(@Language("kts") code: String): Triple<LightTree, String, Int> {
    val (wrappedCode, codeOffset) = wrapScriptIntoClassInitializerBlock(code)
    return Triple(
        KotlinLightParser.parse(psiBuilderFactory.createBuilder(parserDefinition, lexer, wrappedCode)),
        wrappedCode,
        codeOffset
    )
}


fun main() {
    parse(
        """
            #!/usr/bin/env kscript
        a = 1""".trimIndent()
    ).first.print()
}


private
fun wrapScriptIntoClassInitializerBlock(@Language("kts") code: String): Pair<String, Int> {
    val packageStatements = mutableListOf<String>()
    val importStatements = mutableListOf<String>()
    val codeStatements = mutableListOf<String>()

    code.lines().forEach { line ->
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

    val prefix = "${packageSection}${importSection}class Script {init {"
    val codeOffset = prefix.length
    return "$prefix$codeSection}}" to codeOffset
}
