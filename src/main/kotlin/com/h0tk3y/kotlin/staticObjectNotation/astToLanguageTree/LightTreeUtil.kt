package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.language.SourceData
import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import org.jetbrains.kotlin.KtNodeTypes.*
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderImpl
import org.jetbrains.kotlin.com.intellij.openapi.util.Ref
import org.jetbrains.kotlin.com.intellij.psi.TokenType.ERROR_ELEMENT
import org.jetbrains.kotlin.com.intellij.psi.TokenType.WHITE_SPACE
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.diagnostics.isExpression
import org.jetbrains.kotlin.lexer.KtTokens.*

typealias LightTree = FlyweightCapableTreeStructure<LighterASTNode>

fun FlyweightCapableTreeStructure<LighterASTNode>.sourceData(
    sourceIdentifier: SourceIdentifier
) = LightTreeSourceData(sourceIdentifier, this.root) // TODO: not ok to use the root here, due to the artificial wrapping of the script as a class initializer

class LightTreeSourceData(
    override val sourceIdentifier: SourceIdentifier,
    val node: LighterASTNode
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

internal fun FlyweightCapableTreeStructure<LighterASTNode>.print(
    node: LighterASTNode = root,
    indent: String = ""
) {
    val ref = Ref<Array<LighterASTNode?>>()

    getChildren(node, ref)
    val kidsArray = ref.get() ?: return

    for (kid in kidsArray) {
        if (kid == null) break
        kid.print(indent)
        print(kid, "\t$indent")
    }
}

internal fun FlyweightCapableTreeStructure<LighterASTNode>.children(
    node: LighterASTNode
): List<LighterASTNode> {
    val ref = Ref<Array<LighterASTNode?>>()
    getChildren(node, ref)
    return ref.get()
        .filterNotNull()
        .filter { it.isUseful }
}

internal fun FlyweightCapableTreeStructure<LighterASTNode>.getFirstChildExpressionUnwrapped(node: LighterASTNode): LighterASTNode? {
    val filter: (LighterASTNode) -> Boolean = { it -> it.isExpression()}
    val firstChild = firstChild(node, filter) ?: return null
    return if (firstChild.tokenType == PARENTHESIZED) {
        getFirstChildExpressionUnwrapped(firstChild)
    } else {
        firstChild
    }
}

internal fun FlyweightCapableTreeStructure<LighterASTNode>.firstChild(
    node: LighterASTNode,
    filter: (LighterASTNode) -> Boolean
): LighterASTNode? {
    return children(node).firstOrNull(filter)
}

internal val LighterASTNode.asText: String
    get() = this.toString()

internal val LighterASTNode.isUseful: Boolean
    get() = !(COMMENTS.contains(tokenType) || tokenType == WHITE_SPACE || tokenType == SEMICOLON || tokenType == ERROR_ELEMENT)

internal fun LighterASTNode.expectKind(expected: IElementType) {
    check(isKind(expected)) // TODO: sucky String matching, but don't have a better idea atm
}

internal fun List<LighterASTNode>.expectSingleOfKind(expected: IElementType): LighterASTNode =
    this.single { it.isKind(expected) }

internal fun LighterASTNode.isKind(expected: IElementType) =
    this.tokenType.debugName == expected.debugName

internal fun LighterASTNode.sourceData(sourceIdentifier: SourceIdentifier) = LightTreeSourceData(sourceIdentifier, this)

private fun LighterASTNode.print(indent: String) {
    println("$indent${tokenType} (${range()}): ${content()}")
}

private fun LighterASTNode.range() = "$startOffset:$endOffset"

private fun LighterASTNode.content(): String? =
    when (tokenType) {
        BLOCK -> ""
        SCRIPT -> ""
        FUN -> ""
        FUNCTION_LITERAL -> ""
        CALL_EXPRESSION -> ""
        LAMBDA_ARGUMENT -> ""
        LAMBDA_EXPRESSION -> ""
        WHITE_SPACE -> ""
        ERROR_ELEMENT -> PsiBuilderImpl.getErrorMessage(this)
        else -> toString()
    }