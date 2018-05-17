package org.gradle.kotlin.dsl.ktlint.ruleset

import com.github.shyiko.ktlint.core.Rule

import org.jetbrains.kotlin.KtNodeTypes.PRIMARY_CONSTRUCTOR
import org.jetbrains.kotlin.KtNodeTypes.VALUE_PARAMETER
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.WHITE_SPACE
import org.jetbrains.kotlin.psi.KtDeclarationModifierList


class VisibilityModifiersOwnLineRule : Rule("visibility-modifiers-own-line") {

    private
    val ownSingleLineModifierTokens = arrayOf(
        KtTokens.PUBLIC_KEYWORD, KtTokens.PROTECTED_KEYWORD, KtTokens.PRIVATE_KEYWORD, KtTokens.INTERNAL_KEYWORD
    )

    private
    val order = arrayOf(
        KtTokens.PUBLIC_KEYWORD,
        KtTokens.PROTECTED_KEYWORD,
        KtTokens.PRIVATE_KEYWORD,
        KtTokens.INTERNAL_KEYWORD,
        KtTokens.EXPECT_KEYWORD,
        KtTokens.ACTUAL_KEYWORD,
        KtTokens.FINAL_KEYWORD,
        KtTokens.OPEN_KEYWORD,
        KtTokens.ABSTRACT_KEYWORD,
        KtTokens.SEALED_KEYWORD,
        KtTokens.CONST_KEYWORD,
        KtTokens.EXTERNAL_KEYWORD,
        KtTokens.OVERRIDE_KEYWORD,
        KtTokens.LATEINIT_KEYWORD,
        KtTokens.TAILREC_KEYWORD,
        KtTokens.VARARG_KEYWORD,
        KtTokens.SUSPEND_KEYWORD,
        KtTokens.INNER_KEYWORD,
        KtTokens.ENUM_KEYWORD,
        KtTokens.ANNOTATION_KEYWORD,
        KtTokens.COMPANION_KEYWORD,
        KtTokens.INLINE_KEYWORD,
        KtTokens.INFIX_KEYWORD,
        KtTokens.OPERATOR_KEYWORD,
        KtTokens.DATA_KEYWORD
        // NOINLINE_KEYWORD, CROSSINLINE_KEYWORD, OUT_KEYWORD, IN_KEYWORD, REIFIED_KEYWORD
        // HEADER_KEYWORD, IMPL_KEYWORD
    )

    private
    val tokenSet = TokenSet.create(*order)

    private
    val skippedParents = listOf(PRIMARY_CONSTRUCTOR, VALUE_PARAMETER)

    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
    ) {

        if (node.psi is KtDeclarationModifierList && node.treeParent.elementType !in skippedParents) {

            val modifierArr = node.getChildren(tokenSet)

            val vizModifiers = modifierArr.filter { it.elementType in ownSingleLineModifierTokens }

            if (vizModifiers.isNotEmpty()) {

                val vizModifierExpectedText = vizModifiers.joinToString(separator = " ", postfix = "\n") { it.text }

                if (!node.textIncludingSurroundingWhitespace.contains(vizModifierExpectedText)) {
                    emit(
                        node.startOffset,
                        "Visibility modifiers must be on their own single line",
                        false
                    )
                }
            }
        }
    }

    private
    val ASTNode.textIncludingSurroundingWhitespace
        get() = "${
        if (treePrev?.elementType == WHITE_SPACE) treePrev.text else ""
        }$text${
        if (treeNext?.elementType == WHITE_SPACE) treeNext.text else ""
        }"
}
