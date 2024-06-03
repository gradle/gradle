package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.jetbrains.kotlin.KtNodeTypes.BLOCK
import org.jetbrains.kotlin.KtNodeTypes.CALL_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.FUN
import org.jetbrains.kotlin.KtNodeTypes.FUNCTION_LITERAL
import org.jetbrains.kotlin.KtNodeTypes.LAMBDA_ARGUMENT
import org.jetbrains.kotlin.KtNodeTypes.LAMBDA_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.PARENTHESIZED
import org.jetbrains.kotlin.KtNodeTypes.SCRIPT
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderImpl
import org.jetbrains.kotlin.com.intellij.openapi.util.Ref
import org.jetbrains.kotlin.com.intellij.psi.TokenType.ERROR_ELEMENT
import org.jetbrains.kotlin.com.intellij.psi.TokenType.WHITE_SPACE
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.diagnostics.isExpression
import org.jetbrains.kotlin.lexer.KtTokens.COMMENTS
import org.jetbrains.kotlin.lexer.KtTokens.SEMICOLON


typealias LightTree = FlyweightCapableTreeStructure<LighterASTNode>


fun FlyweightCapableTreeStructure<LighterASTNode>.sourceData(
    sourceIdentifier: SourceIdentifier,
    sourceCode: String,
    sourceOffset: Int
) =
    LightTreeSourceData(
        sourceIdentifier,
        sourceCode,
        sourceOffset,
        root.range()
    )


class LightTreeSourceData(
    override val sourceIdentifier: SourceIdentifier,
    private val sourceCode: String,
    private val sourceOffset: Int,
    private val nodeRange: IntRange,
) : SourceData {

    override fun toString(): String = "LightTreeSourceData(${sourceIdentifier.fileIdentifier}:$nodeRange)"

    override val indexRange: IntRange by lazy {
        val originalRange = nodeRange
        val first = originalRange.first - sourceOffset
        val last = originalRange.last - sourceOffset
        first..last
    }

    private
    val lineColumnInfo: LineColumnInfo by lazy {
        LineColumnInfo.fromIndexRange(sourceCode, sourceOffset, indexRange)
    }
    override
    val lineRange: IntRange
        get() = lineColumnInfo.startLine..lineColumnInfo.endLine
    override
    val startColumn: Int
        get() = lineColumnInfo.startColumn
    override
    val endColumn: Int
        get() = lineColumnInfo.endColumn
    override
    fun text(): String = sourceCode.substring((indexRange.first + sourceOffset)..(indexRange.last + sourceOffset))

    private
    class LineColumnInfo(val startLine: Int, val startColumn: Int, val endLine: Int, val endColumn: Int) {
        companion object Factory {
            fun fromIndexRange(text: String, offset: Int, offsetRelativeIndexRange: IntRange): LineColumnInfo {
                fun String.newLineLength(index: Int): Int =
                    when (this[index]) {
                        '\n' -> 1
                        '\r' -> {
                            if (index + 1 < length && this[index + 1] == '\n') 2 else 1
                        }
                        else -> 0
                    }

                fun String.isValidIndex(index: Int) = index in indices

                check(text.isValidIndex(offset))

                val realStartIndex = offset + offsetRelativeIndexRange.first
                check(text.isValidIndex(realStartIndex))

                val realEndIndex = offset + offsetRelativeIndexRange.last
                check(text.isValidIndex(realEndIndex))

                check(realEndIndex - realStartIndex >= -1) // -1 is for empty intervals

                var startLine = -1
                var startColumn = -1
                var endLine = -1
                var endColumn = -1

                var i = offset
                var line = 1
                var column = 1
                while (i < text.length) {
                    if (i == realStartIndex) {
                        startLine = line
                        startColumn = column
                    }
                    if (i == realEndIndex) {
                        endLine = line
                        endColumn = column
                        if (realStartIndex == realEndIndex + 1) { // might be an empty range, e.g. 20..19
                            startLine = line
                            startColumn = column + 1
                        }
                        break
                    }

                    val newLineLength = text.newLineLength(i)
                    if (newLineLength > 0) {
                        i += newLineLength
                        line++
                        column = 1
                    } else {
                        i++
                        column++
                    }
                }

                check(startLine >= 0 && startColumn >= 0 && endLine >= 0 && endColumn >= 0)
                return LineColumnInfo(startLine, startColumn, endLine, endColumn)
            }
        }
    }
}


internal
fun FlyweightCapableTreeStructure<LighterASTNode>.print(
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


internal
fun FlyweightCapableTreeStructure<LighterASTNode>.children(
    node: LighterASTNode
): List<LighterASTNode> {
    val ref = Ref<Array<LighterASTNode?>>()
    getChildren(node, ref)
    return ref.get()
        .filterNotNull()
        .filter { it.isUseful }
} // TODO: any usages that need to be checked for parsing errors?


internal
fun FlyweightCapableTreeStructure<LighterASTNode>.getFirstChildExpressionUnwrapped(node: LighterASTNode): LighterASTNode? {
    val firstChild = children(node).firstOrNull { it: LighterASTNode -> it.isExpression() } ?: return null
    return if (firstChild.tokenType == PARENTHESIZED) {
        getFirstChildExpressionUnwrapped(firstChild)
    } else {
        firstChild
    }
}


internal
val LighterASTNode.asText: String
    get() = this.toString()


internal
val LighterASTNode.isUseful: Boolean
    get() = !(COMMENTS.contains(tokenType) || tokenType == WHITE_SPACE || tokenType == SEMICOLON)


internal
fun LighterASTNode.expectKind(expected: IElementType) {
    check(isKind(expected))
}


internal
fun List<LighterASTNode>.expectSingleOfKind(expected: IElementType): LighterASTNode =
    this.single { it.isKind(expected) }


internal
fun LighterASTNode.isKind(expected: IElementType) =
    this.tokenType == expected


internal
fun LighterASTNode.sourceData(sourceIdentifier: SourceIdentifier, sourceCode: String, sourceOffset: Int) =
    LightTreeSourceData(sourceIdentifier, sourceCode, sourceOffset, this.range())


private
fun LighterASTNode.print(indent: String) {
    println("$indent$tokenType (${range()}): ${content()}")
}


internal
fun LighterASTNode.range() = startOffset.until(endOffset)


private
fun LighterASTNode.content(): String? =
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
