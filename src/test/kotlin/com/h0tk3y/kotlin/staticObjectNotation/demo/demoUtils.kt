package com.h0tk3y.kotlin.staticObjectNotation.demo

import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.*
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTracer
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.AssignmentAdditionResult.AssignmentAdded
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.AssignmentResolutionResult.Assigned
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTrace
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTraceElement

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

fun assignmentTrace(result: ResolutionResult) = 
    AssignmentTracer { AssignmentResolver() }.produceAssignmentTrace(result)

fun printAssignmentTrace(trace: AssignmentTrace) {
    trace.elements.forEach { element ->
        when (element) {
            is AssignmentTraceElement.UnassignedValueUsed -> {
                val locationString = when (val result = element.assignmentAdditionResult) {
                    AssignmentAdded -> error("unexpected")
                    is AssignmentResolver.AssignmentAdditionResult.UnresolvedValueUsedInLhs -> "lhs: ${result.value}"
                    is AssignmentResolver.AssignmentAdditionResult.UnresolvedValueUsedInRhs -> "rhs: ${result.value}"
                }
                println("${element.lhs} !:= ${element.rhs} -- unassigned property in $locationString")
            }
            is AssignmentTraceElement.RecordedAssignment -> {
                val assigned = trace.resolvedAssignments.getValue(element.lhs) as Assigned
                println("${element.lhs} := ${element.rhs} => ${assigned.objectOrigin}")
            }
        }
    }    
}

fun printResolvedAssignments(result: ResolutionResult) {
    printAssignmentTrace(assignmentTrace(result))
}

inline fun <reified T> typeRef(): DataTypeRef.Name {
    val parts = T::class.qualifiedName!!.split(".")
    return DataTypeRef.Name(FqName(parts.dropLast(1).joinToString("."), parts.last()))
}