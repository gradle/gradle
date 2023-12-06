package com.example.com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.AbstractRejectedLanguageFeaturesTest
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.ElementResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.ParseTestUtil

class RejectedLanguageFeaturesAstTest: AbstractRejectedLanguageFeaturesTest() {

    override fun parse(code: String): List<ElementResult<*>> = ParseTestUtil.parseWithAst(code)
}