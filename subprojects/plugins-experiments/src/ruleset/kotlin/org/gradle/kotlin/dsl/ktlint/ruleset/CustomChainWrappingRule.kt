package org.gradle.kotlin.dsl.ktlint.ruleset

import com.github.shyiko.ktlint.core.Rule

import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.psiUtil.nextLeaf
import org.jetbrains.kotlin.psi.psiUtil.prevLeaf


// Same as upstream except it doesn't have checks for "same line tokens"
class CustomChainWrappingRule : Rule("gradle-kotlin-dsl-chain-wrapping") {

    private
    val nextLineTokens = TokenSet.create(KtTokens.DOT, KtTokens.SAFE_ACCESS, KtTokens.ELVIS)

    private
    val noSpaceAroundTokens = TokenSet.create(KtTokens.DOT, KtTokens.SAFE_ACCESS)

    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
    ) {
        /*
           org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement (DOT) | "."
           org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl (WHITE_SPACE) | "\n        "
           org.jetbrains.kotlin.psi.KtCallExpression (CALL_EXPRESSION)
         */
        val elementType = node.elementType
        if (nextLineTokens.contains(elementType)) {
            val nextLeaf = node.psi.nextLeaf(true)
            if (nextLeaf is PsiWhiteSpaceImpl && nextLeaf.textContains('\n')) {
                emit(node.startOffset, "Line must not end with \"${node.text}\"", true)
                if (autoCorrect) {
                    val prevLeaf = node.psi.prevLeaf(true)
                    if (prevLeaf is PsiWhiteSpaceImpl) {
                        prevLeaf.rawReplaceWithText(nextLeaf.text)
                    } else {
                        (node.psi as LeafPsiElement).rawInsertBeforeMe(PsiWhiteSpaceImpl(nextLeaf.text))
                    }
                    if (noSpaceAroundTokens.contains(elementType)) {
                        nextLeaf.node.treeParent.removeChild(nextLeaf.node)
                    } else {
                        nextLeaf.rawReplaceWithText(" ")
                    }
                }
            }
        }
    }
}
