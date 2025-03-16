package org.gradle.internal.declarativedsl.demo

import org.gradle.declarative.dsl.schema.DataType
import org.gradle.internal.declarativedsl.analysis.OperationId
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.ref
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver.AssignmentAdditionResult.AssignmentAdded
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver.AssignmentResolutionResult.Assigned
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTrace
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTraceElement
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTracer
import org.gradle.internal.declarativedsl.objectGraph.ObjectReflection


val int = DataTypeInternal.DefaultIntDataType.ref


val string = DataTypeInternal.DefaultStringDataType.ref


val boolean = DataTypeInternal.DefaultBooleanDataType.ref


fun printResolutionResults(
    result: ResolutionResult
) {
    println(result.errors.joinToString("\n") { "ERROR: ${it.errorReason} in ${it.element.sourceData.text()}\n" })
    println("Assignments:\n" + result.assignments.joinToString("\n") { (k, v) -> "$k := $v" })
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
                    is AssignmentResolver.AssignmentAdditionResult.Reassignment,
                    is AssignmentAdded -> error("unexpected")
                    is AssignmentResolver.AssignmentAdditionResult.UnresolvedValueUsedInLhs -> "lhs: ${result.value}"
                    is AssignmentResolver.AssignmentAdditionResult.UnresolvedValueUsedInRhs -> "rhs: ${result.value}"
                }
                println("${element.lhs} !:= ${element.rhs} -- unassigned property in $locationString")
            }
            is AssignmentTraceElement.Reassignment -> {
                println("${element.lhs} !:= ${element.rhs} -- reassignment")
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


@Suppress("NestedBlockDepth")
fun prettyStringFromReflection(objectReflection: ObjectReflection): String {
    val visitedIdentity = mutableSetOf<OperationId>()

    fun StringBuilder.recurse(current: ObjectReflection, depth: Int) {
        fun indent() = "    ".repeat(depth)
        fun nextIndent() = "    ".repeat(depth + 1)
        when (current) {
            is ObjectReflection.ConstantValue -> append(
                if (current.type is DataType.StringDataType)
                    "\"${current.value}\""
                else current.value.toString()
            )
            is ObjectReflection.EnumValue -> current.objectOrigin.toString()
            is ObjectReflection.DataObjectReflection -> {
                append(current.type.toString() + (if (current.identity.invocationId != -1L) "#" + current.identity else "") + " ")
                if (visitedIdentity.add(current.identity)) {
                    append("{\n")
                    current.properties.forEach {
                        append(nextIndent() + it.key.name + " = ")
                        recurse(it.value.value, depth + 1)
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

            is ObjectReflection.External -> append("(external ${current.key.objectType}})")
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

            is ObjectReflection.DefaultValue -> append("(default value)")
            is ObjectReflection.AddedByUnitInvocation -> append("invoked: ${objectReflection.objectOrigin}")
            is ObjectReflection.Null -> append("null")
            is ObjectReflection.GroupedVarargReflection -> {
                append("[")
                current.elementsReflection.forEach { recurse(it, depth) }
                append("]")
            }
        }
    }

    return buildString { recurse(objectReflection, 0) }
}
