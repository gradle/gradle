package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.language.SourceData
import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.ast.astInfoOrNull
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderImpl
import org.jetbrains.kotlin.com.intellij.openapi.util.Ref
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure

typealias LightTree = FlyweightCapableTreeStructure<LighterASTNode>

fun LightTree.sourceData(sourceIdentifier: SourceIdentifier) = LightTreeSourceData(sourceIdentifier, this)

class LightTreeSourceData(
    override val sourceIdentifier: SourceIdentifier,
    val tree: LightTree
) : SourceData {
    override val indexRange: IntRange
        get() = TODO()
    override val lineRange: IntRange
        get() = TODO()
    override val startColumn: Int
        get() = TODO()
    override val endColumn: Int
        get() = TODO()

    override fun text(): String = TODO()
}

internal fun LightTree.print(node: LighterASTNode = root, indent: String = "") {
    val ref = Ref<Array<LighterASTNode?>>()

    getChildren(node, ref)
    val kidsArray = ref.get() ?: return

    for (kid in kidsArray) {
        if (kid == null) break
        kid.print(indent)
        print(kid, "\t$indent")
    }
}

internal fun LighterASTNode.print(indent: String) {
    println("$indent${tokenType} (${range()}): ${content()}")
}

private fun LighterASTNode.range() = "$startOffset:$endOffset"

private fun LighterASTNode.content(): String? =
    when (tokenType.toString()) {
        "BLOCK" -> ""
        "SCRIPT" -> ""
        "FUN" -> ""
        "FUNCTION_LITERAL" -> ""
        "CALL_EXPRESSION" -> ""
        "LAMBDA_ARGUMENT" -> ""
        "LAMBDA_EXPRESSION" -> ""
        "WHITE_SPACE" -> ""
        "ERROR_ELEMENT" -> PsiBuilderImpl.getErrorMessage(this)
        else -> toString()
    }