package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.*

val int = DataType.IntDataType.ref
val string = DataType.StringDataType.ref
val boolean = DataType.BooleanDataType.ref

fun resolve(
    schema: AnalysisSchema,
    code: String
): ResolutionResult {
    val ast = parseToAst(code)

    val languageBuilder = LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder())
    val tree = languageBuilder.build(ast.single())
    val resolver: DataObjectResolver = DataObjectResolverImpl()
    val languageElements = tree.results.filterIsInstance<Element<*>>().map { it.element }
    return resolver.resolve(schema, languageElements)
}

fun printResolutionResults(
    result: ResolutionResult
) {
    println(result.errors.joinToString("\n") { "ERROR: ${it.errorReason} in ${it.element.originAst.text}\n" })
    println("Assignments:\n" + result.assignments.entries.joinToString("\n") { (k, v) -> "$k := $v" })
    println()
    println("Additions:\n" + result.additions.joinToString("\n") { (container, obj) -> "$container += $obj" })
}

inline fun <reified T> typeRef(): DataTypeRef.Name {
    val parts = T::class.qualifiedName!!.split(".")
    return DataTypeRef.Name(FqName(parts.dropLast(1).joinToString("."), parts.last()))
}