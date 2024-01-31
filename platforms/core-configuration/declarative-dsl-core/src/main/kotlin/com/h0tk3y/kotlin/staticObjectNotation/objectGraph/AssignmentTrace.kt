package com.h0tk3y.kotlin.staticObjectNotation.objectGraph

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AssignmentMethod
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ObjectOrigin
import com.h0tk3y.kotlin.staticObjectNotation.analysis.PropertyReferenceResolution
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionResult
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTraceElement.UnassignedValueUsed
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.AssignmentAdditionResult.*

class AssignmentTracer(
    val assignmentResolverFactory: () -> AssignmentResolver
) {
    fun produceAssignmentTrace(resolutionResult: ResolutionResult): AssignmentTrace {
        val assignmentResolver = assignmentResolverFactory()
        val elementResults = buildList {
            resolutionResult.assignments.forEach { (lhs, rhs, _, method) ->
                add(
                    when (val additionResult = assignmentResolver.addAssignment(lhs, rhs, method)) {
                        is AssignmentAdded -> AssignmentTraceElement.RecordedAssignment(additionResult.resolvedLhs, rhs, additionResult.assignmentMethod)
                        is UnresolvedValueUsedInLhs -> UnassignedValueUsed(additionResult, lhs, rhs)
                        is UnresolvedValueUsedInRhs -> UnassignedValueUsed(additionResult, lhs, rhs)
                    }
                )
            }
        }
        val assignments = assignmentResolver.getAssignmentResults()
        return AssignmentTrace(elementResults, assignmentResolver, assignments)
    }
}

class AssignmentTrace(
    val elements: List<AssignmentTraceElement>,
    val resolver: AssignmentResolver,
    val resolvedAssignments: Map<PropertyReferenceResolution, AssignmentResolver.AssignmentResolutionResult>
)

sealed interface AssignmentTraceElement {
    val lhs: PropertyReferenceResolution
    val rhs: ObjectOrigin

    data class RecordedAssignment(
        override val lhs: PropertyReferenceResolution,
        override val rhs: ObjectOrigin,
        val assignmentMethod: AssignmentMethod
    ) : AssignmentTraceElement

    data class UnassignedValueUsed(
        val assignmentAdditionResult: AssignmentResolver.AssignmentAdditionResult,
        override val lhs: PropertyReferenceResolution,
        override val rhs: ObjectOrigin
    ) : AssignmentTraceElement
}
