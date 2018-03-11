package org.gradle.kotlin.dsl.ktlint.ruleset

import com.github.shyiko.ktlint.core.Rule

import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes


class BlankLinesRule : Rule("gradle-kotlin-dsl-blank-lines") {

    private
    var skippedFirstTopLevelWhiteSpace = false

    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
    ) {

        if (node is PsiWhiteSpace) {

            val split = node.getText().split("\n")

            // Not more than 2 blank lines anywhere in the file
            if (split.size > 4 || split.size == 3 && PsiTreeUtil.nextLeaf(node) == null /* eof */) {
                emit(node.startOffset + split[0].length + split[1].length + 2, "Needless blank line(s)", true)
                if (autoCorrect) {
                    (node as LeafPsiElement)
                        .rawReplaceWithText("${split.first()}\n${if (split.size > 3) "\n" else ""}${split.last()}")
                }
            }

            // Two blank lines before top level elements
            if (node.treeParent.elementType == KtStubElementTypes.FILE) {
                if (!skippedFirstTopLevelWhiteSpace) {
                    skippedFirstTopLevelWhiteSpace = true
                    return
                }
                if (node.treeNext != null
                    && node.treeNext.elementType != KtStubElementTypes.IMPORT_LIST
                    && PsiTreeUtil.nextLeaf(node) != null /* not oef */
                ) {
                    if (split.size < 4) {
                        emit(
                            node.startOffset,
                            "Top level elements must be separated by two blank lines",
                            false
                        )
                    }
                }
            }
        }
    }
}
