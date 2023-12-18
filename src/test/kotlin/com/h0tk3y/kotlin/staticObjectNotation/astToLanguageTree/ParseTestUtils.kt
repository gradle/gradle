package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import org.intellij.lang.annotations.Language

class ParseTestUtil {

    companion object Parser {
        fun parseWithAst(@Language("kts") code: String): List<ElementResult<*>> {
            val ast = parseToAst(code)
            return DefaultLanguageTreeBuilder().build(ast, SourceIdentifier("test")).results
        }

        fun parseWithLightParser(@Language("kts") code: String): List<ElementResult<*>> {
            val (tree, sourceOffset) = parseToLightTree(code)
            return DefaultLanguageTreeBuilder().build(tree, sourceOffset, SourceIdentifier("test")).results
        }
    }

}
