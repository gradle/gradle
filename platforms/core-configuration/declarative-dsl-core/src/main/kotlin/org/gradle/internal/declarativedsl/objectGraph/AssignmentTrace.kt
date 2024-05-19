package org.gradle.internal.declarativedsl.objectGraph

import org.gradle.internal.declarativedsl.analysis.AssignmentMethod
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.PropertyReferenceResolution
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTraceElement.UnassignedValueUsed


class AssignmentTracer(
    val assignmentResolverFactory: () -> AssignmentResolver
) {
    fun produceAssignmentTrace(resolutionResult: ResolutionResult): AssignmentTrace {
        val assignmentResolver = assignmentResolverFactory()
        val elementResults = buildList {
            val assignments = resolutionResult.conventionAssignments + resolutionResult.assignments
            assignments.forEach { (lhs, rhs, callId, method) ->
                add(
                    when (val additionResult = assignmentResolver.addAssignment(lhs, rhs, method, callId.generationId)) {
                        is AssignmentResolver.AssignmentAdditionResult.AssignmentAdded -> AssignmentTraceElement.RecordedAssignment(additionResult.resolvedLhs, rhs, additionResult.assignmentMethod)
                        is AssignmentResolver.AssignmentAdditionResult.UnresolvedValueUsedInLhs -> UnassignedValueUsed(additionResult, lhs, rhs)
                        is AssignmentResolver.AssignmentAdditionResult.UnresolvedValueUsedInRhs -> UnassignedValueUsed(additionResult, lhs, rhs)
                        is AssignmentResolver.AssignmentAdditionResult.Reassignment -> AssignmentTraceElement.Reassignment(additionResult, lhs, rhs)
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

    sealed interface FailedToRecordAssignment : AssignmentTraceElement

    data class RecordedAssignment(
        override val lhs: PropertyReferenceResolution,
        override val rhs: ObjectOrigin,
        val assignmentMethod: AssignmentMethod
    ) : AssignmentTraceElement

    data class UnassignedValueUsed(
        val assignmentAdditionResult: AssignmentResolver.AssignmentAdditionResult,
        override val lhs: PropertyReferenceResolution,
        override val rhs: ObjectOrigin
    ) : FailedToRecordAssignment

    data class Reassignment(
        val assignmentAdditionResult: AssignmentResolver.AssignmentAdditionResult.Reassignment,
        override val lhs: PropertyReferenceResolution,
        override val rhs: ObjectOrigin,
    ) : FailedToRecordAssignment
}
