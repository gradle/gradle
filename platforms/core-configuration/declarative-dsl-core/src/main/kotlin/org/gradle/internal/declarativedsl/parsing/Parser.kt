package org.gradle.internal.declarativedsl.parsing

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderFactoryImpl
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.parsing.KotlinLightParser
import org.jetbrains.kotlin.parsing.KotlinParserDefinition


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


data class ParsedLightTree(
    val lightTree: LightTree,
    val wrappedCode: String,
    val originalCodeOffset: Int,
    val suffixLength: Int,
)


fun parse(@Language("kts") code: String): ParsedLightTree {
    val (wrappedCode, codeOffset, suffixLength) = wrapScriptIntoClassInitializerBlock(code)
    return ParsedLightTree(
        KotlinLightParser.parse(psiBuilderFactory.createBuilder(parserDefinition, lexer, wrappedCode)),
        wrappedCode,
        codeOffset,
        suffixLength
    )
}


fun main() {
    parse(
        """
            #!/usr/bin/env kscript
        a = 1""".trimIndent()
    ).lightTree.print()
}


private
fun wrapScriptIntoClassInitializerBlock(@Language("kts") code: String): Triple<String, Int, Int> {
    val packageStatements = mutableListOf<String>()
    val importStatements = mutableListOf<String>()
    val codeStatements = mutableListOf<String>()

    var isAfterImportLine = false

    code.lines().forEach { line ->
        when {
            line.startsWith("import") -> {
                importStatements.add(line)
                isAfterImportLine = true
            }

            line.startsWith("package") -> {
                packageStatements.add(line)
                isAfterImportLine = false
            }

            line.isBlank() -> {
                if (!isAfterImportLine) codeStatements.add(line)
            }

            else -> {
                codeStatements.add(line)
                isAfterImportLine = false
            }
        } // TODO: ugly, brittle hack...
    }

    if (packageStatements.size > 2) error("Multiple package statements")

    fun addNewlineIfNotBlank(it: String) = when {
        it.isNotBlank() -> it + "\n"
        else -> it
    }

    val packageSection = packageStatements.joinToString("") { addNewlineIfNotBlank(it) }
    val importSection = importStatements.joinToString("") { addNewlineIfNotBlank(it) }
    val codeSection = codeStatements.joinToString("") { "$it\n" }

    val prefix = "${packageSection}${importSection}class Script {init {"
    val suffix = "}}"
    val codeOffset = prefix.length
    return Triple("$prefix$codeSection$suffix", codeOffset, suffix.length)
}
