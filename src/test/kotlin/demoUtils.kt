package com.h0tk3y.kotlin.staticObjectNotation.analysis

import analysis.DataObjectResolver
import analysis.DataObjectResolverImpl
import analysis.ResolutionResult
import com.h0tk3y.kotlin.staticObjectNotation.ElementOrFailureResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.DefaultLanguageTreeBuilder
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.LanguageTreeBuilderWithTopLevelBlock
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.parseToAst
import com.h0tk3y.kotlin.staticObjectNotation.text

fun resolve(
    schema: AnalysisSchema,
    code: String
): ResolutionResult {
    val ast = parseToAst(code)

    val languageBuilder = LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder())
    val tree = languageBuilder.build(ast.single())
    val resolver: DataObjectResolver = DataObjectResolverImpl()
    val languageElements = tree.results.filterIsInstance<ElementOrFailureResult.ElementResult<*>>().map { it.element }
    return resolver.resolve(schema, languageElements)
}

fun printResolutionResults(
    result: ResolutionResult
) {
    println(result.errors.joinToString("\n") { "ERROR: ${it.errorReason} in ${it.element.originAst.text}\n" })
    println(
        "Assignments:\n" + result.assignments.entries.joinToString("\n") { (k, v) -> "$k := $v" }
    )
    println()
    println(
        "Additions:\n" + result.additions.joinToString("\n") { (container, obj) -> "$container += $obj" }
    )
}