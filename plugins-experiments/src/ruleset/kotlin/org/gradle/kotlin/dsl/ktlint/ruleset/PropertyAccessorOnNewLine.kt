package org.gradle.kotlin.dsl.ktlint.ruleset

import com.github.shyiko.ktlint.core.Rule
import org.jetbrains.kotlin.KtNodeTypes

import org.jetbrains.kotlin.com.intellij.lang.ASTNode


class PropertyAccessorOnNewLine : Rule("property-get-new-line") {

    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
    ) {
        if (node.elementType == KtNodeTypes.PROPERTY_ACCESSOR) {
            if (!node.treePrev.text.contains("\n")) {
                // fail
                emit(node.startOffset, "Property accessor must be on a new line", false)
            }
        }
    }
}
