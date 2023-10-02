package com.h0tk3y.kotlin.staticObjectNotation.demo

import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.*
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver

val int = DataType.IntDataType.ref
val string = DataType.StringDataType.ref
val boolean = DataType.BooleanDataType.ref

fun AnalysisSchema.resolve(
    code: String
): ResolutionResult {
    val ast = parseToAst(code)

    val languageBuilder = LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder())
    val tree = languageBuilder.build(ast.single())
    val resolver: DataObjectResolver = DataObjectResolverImpl()
    val languageElements = tree.results.filterIsInstance<Element<*>>().map { it.element }

    val failures = tree.results.filterIsInstance<FailingResult>()

    if (failures.isNotEmpty()) {
        println("Failures:")
        fun printFailures(failure: FailingResult) {
            when (failure) {
                is UnsupportedConstruct -> println(
                    failure.languageFeature.toString() + " in " + ast.toString().take(100)
                )
                is MultipleFailuresResult -> printFailures(failure)
            }
        }
        failures.forEach { printFailures(it) }
    }
    
    return resolver.resolve(this, languageElements)
}

fun printResolutionResults(
    result: ResolutionResult
) {
    println(result.errors.joinToString("\n") { "ERROR: ${it.errorReason} in ${it.element.originAst.text}\n" })
    println("Assignments:\n" + result.assignments.entries.joinToString("\n") { (k, v) -> "$k := $v" })
    println()
    println("Additions:\n" + result.additions.joinToString("\n") { (container, obj) -> "$container += $obj" })
}

fun printResolvedAssignments(result: ResolutionResult) {
    val assignments = AssignmentResolver()
    for ((lhs, rhs) in result.assignments) {
        val assigned = assignments.addAssignment(lhs, rhs)
        if (assigned is AssignmentResolver.AssignmentResolutionProgress.UnresolvedReceiver) {
            println("$lhs =/= $rhs : Assignment used an unassigned receiver ${assigned.accessOrigin}")
        }
    }
    val values = assignments.getAssignmentResults()
    println("\n===\nAssignment results")
    values.forEach { (lhs, rhs) ->
        val rhsString = when (rhs) {
            is AssignmentResolver.AssignmentResolutionResult.Assigned -> ":= ${rhs.objectOrigin}"
            is AssignmentResolver.AssignmentResolutionResult.Unassigned -> "!:= ${rhs.objectOrigin}"
        }
        println("$lhs $rhsString")
    }
}

inline fun <reified T> typeRef(): DataTypeRef.Name {
    val parts = T::class.qualifiedName!!.split(".")
    return DataTypeRef.Name(FqName(parts.dropLast(1).joinToString("."), parts.last()))
}