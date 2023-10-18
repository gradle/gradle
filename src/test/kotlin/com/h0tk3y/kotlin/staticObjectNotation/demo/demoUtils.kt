package com.h0tk3y.kotlin.staticObjectNotation.demo

import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.*
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.*
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.AssignmentAdditionResult.AssignmentAdded
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.AssignmentResolutionResult.Assigned

val int = DataType.IntDataType.ref
val string = DataType.StringDataType.ref
val boolean = DataType.BooleanDataType.ref

fun AnalysisSchema.resolve(
    code: String
): ResolutionResult {
    val ast = parseToAst(code)

    val languageBuilder = LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder())
    val tree = languageBuilder.build(ast.single())
    val resolver: Resolver = defaultCodeResolver()
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
                    is AssignmentAdded -> error("unexpected")
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
    println("\nResolved assignments:")
    printAssignmentTrace(assignmentTrace(result))
}

inline fun <reified T> typeRef(): DataTypeRef.Name {
    val parts = T::class.qualifiedName!!.split(".")
    return DataTypeRef.Name(FqName(parts.dropLast(1).joinToString("."), parts.last()))
}

fun prettyStringFromReflection(objectReflection: ObjectReflection): String {
    val visitedIdentity = mutableSetOf<Long>()
    
    fun StringBuilder.recurse(current: ObjectReflection, depth: Int) {
        fun indent() = "    ".repeat(depth)
        fun nextIndent() = "    ".repeat(depth + 1)
        when (current) {
            is ObjectReflection.ConstantValue -> append(
                if (current.type == DataType.StringDataType) 
                    "\"${current.value}\"" 
                else current.value.toString()
            )
            is ObjectReflection.DataObjectReflection -> {
                append(current.type.toString() + (if (current.identity != -1L) "#" + current.identity else "") + " ")
                if (visitedIdentity.add(current.identity)) {
                    append("{\n")
                    current.properties.forEach {
                        append(nextIndent() + it.key.name + " = ")
                        recurse(it.value, depth + 1)
                        append("\n")
                    }
                    current.addedObjects.forEach {
                        append("${nextIndent()}+ ")
                        recurse(it, depth + 1)
                        append("\n")
                    }
                    append("${indent()}}")
                } else {
                    append("{ ... }")
                }
            }

            is ObjectReflection.External -> append("(external ${current.externalObjectProviderKey.type}})")
            ObjectReflection.Null -> append("null")
            is ObjectReflection.PureFunctionInvocation -> {
                append(current.objectOrigin.function.simpleName)
                append("#" + current.objectOrigin.invocationId)
                if (visitedIdentity.add(current.objectOrigin.invocationId)) {
                    append("(")
                    if (current.parameterResolution.isNotEmpty()) {
                        append("\n")
                        for (param in current.parameterResolution) {
                            append("${nextIndent()}${param.key.name}")
                            append(" = ")
                            recurse(param.value, depth + 1)
                            append("\n")
                        }
                        append(indent())
                    }
                    append(")")
                } else {
                    append("(...)")
                }
            }
        }
    }

    return buildString { recurse(objectReflection, 0) }
}