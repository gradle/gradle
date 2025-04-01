/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.declarativedsl.objectGraph

import org.gradle.internal.declarativedsl.analysis.AssignmentMethod
import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.NestedObjectAccessRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.OperationId
import org.gradle.internal.declarativedsl.analysis.PropertyReferenceResolution
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinkTraceElement.RecordedAddition
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinkTraceElement.RecordedNestedObjectAccess
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinkTraceElement.UnassignedValueUsedInAddition
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinksResolver.PropertyLinkResolutionResult
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinksResolver.PropertyLinkResolutionResult.Ok
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinkTraceElement.UnassignedValueUsedInAssignment
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinkTraceElement.UnassignedValueUsedInNestedObjectAccess
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinksResolver.PropertyLinkResolutionResult.UnresolvedPropertyUsage


class PropertyLinkTracer(
    val propertyLinkResolverFactory: () -> PropertyLinksResolver
) {
    private class NewResolutionResultState {
        val newResultAssignmentsFromDefaults = mutableListOf<AssignmentRecord>()
        val newResultAdditionsFromDefaults = mutableListOf<DataAdditionRecord>()
        val newResultNestedObjectAccessFromDefaults = mutableListOf<NestedObjectAccessRecord>()
        val newResultAssignments = mutableListOf<AssignmentRecord>()
        val newResultAdditions = mutableListOf<DataAdditionRecord>()
        val newResultNestedObjectAccess = mutableListOf<NestedObjectAccessRecord>()

        fun registerAssignment(assignmentRecord: AssignmentRecord, isFromDefaults: Boolean) =
            (if (isFromDefaults) newResultAssignmentsFromDefaults else newResultAssignments).add(assignmentRecord)

        fun registerAddition(additionRecord: DataAdditionRecord, isFromDefaults: Boolean) =
            (if (isFromDefaults) newResultAdditionsFromDefaults else newResultAdditions).add(additionRecord)

        fun registerNestedObjectAccess(nestedObjectAccessRecord: NestedObjectAccessRecord, isFromDefaults: Boolean) =
            (if (isFromDefaults) newResultNestedObjectAccessFromDefaults else newResultNestedObjectAccess).add(nestedObjectAccessRecord)
    }

    @Suppress("LongMethod")
    fun producePropertyLinkResolutionTrace(resolutionResult: ResolutionResult): PropertyLinkTrace {
        val propertyLinkResolver = propertyLinkResolverFactory()

        val traceElements = mutableListOf<PropertyLinkTraceElement>()

        val newResolutionResultElements = NewResolutionResultState()

        val events = listOf(
            resolutionResult.assignmentsFromDefaults.map { Event.Assignment(it, isFromDefaults = true) },
            resolutionResult.additionsFromDefaults.map { Event.Addition(it, isFromDefaults = true) },
            resolutionResult.nestedObjectAccessFromDefaults.map { Event.NestedObjectAccess(it, isFromDefaults = true) },
            resolutionResult.assignments.map { Event.Assignment(it, isFromDefaults = false) },
            resolutionResult.additions.map { Event.Addition(it, isFromDefaults = false) },
            resolutionResult.nestedObjectAccess.map { Event.NestedObjectAccess(it, isFromDefaults = false) },
        ).flatten().sortedBy { it.operationId }

        events.forEach { event ->
            event.relevantOrigins.forEach {
                propertyLinkResolver.resolvePropertyLinksIn(it)
            }
            when (event) {
                is Event.Assignment -> resolvingPropertyLinks(propertyLinkResolver) {
                    with(event.assignmentRecord) {
                        val traceElement = when (val additionResult = propertyLinkResolver.addAssignment(lhs, rhs, assignmentMethod, operationId.generationId)) {
                            is PropertyLinksResolver.AssignmentAdditionResult.AssignmentAdded -> {
                                val record = AssignmentRecord(
                                    lhs = additionResult.resolvedLhs,
                                    rhs = additionResult.resolvedRhs,
                                    // explicitly write down the other fields instead of `copy` so that it is harder to make a mistake if new fields are added
                                    operationId = event.assignmentRecord.operationId,
                                    assignmentMethod = event.assignmentRecord.assignmentMethod,
                                    originElement = event.assignmentRecord.originElement
                                )
                                newResolutionResultElements.registerAssignment(record, event.isFromDefaults)
                                PropertyLinkTraceElement.RecordedAssignment(
                                    event.assignmentRecord.originElement,
                                    additionResult.resolvedLhs,
                                    rhs,
                                    additionResult.resolvedRhs,
                                    additionResult.assignmentMethod
                                )
                            }

                            is PropertyLinksResolver.AssignmentAdditionResult.UnresolvedValueUsedInLhs ->
                                UnassignedValueUsedInAssignment(event.assignmentRecord.originElement, additionResult, lhs, rhs)

                            is PropertyLinksResolver.AssignmentAdditionResult.UnresolvedValueUsedInRhs ->
                                UnassignedValueUsedInAssignment(event.assignmentRecord.originElement, additionResult, lhs, rhs)

                            is PropertyLinksResolver.AssignmentAdditionResult.Reassignment ->
                                PropertyLinkTraceElement.Reassignment(event.assignmentRecord.originElement, additionResult, lhs, rhs)
                        }

                        traceElements.add(traceElement)
                    }
                }

                is Event.Addition -> resolvingPropertyLinks(propertyLinkResolver) {
                    val container = resolvePropertyLinksIn(event.additionRecord.container)
                    val dataObject = resolvePropertyLinksIn(event.additionRecord.dataObject)

                    ifOk {
                        val newAdditionRecord = DataAdditionRecord(
                            container = ensureResolved(container),
                            dataObject = ensureResolved(dataObject),
                            operationId = event.additionRecord.operationId
                        )
                        newResolutionResultElements.registerAddition(newAdditionRecord, event.isFromDefaults)
                        traceElements.add(RecordedAddition(event.additionRecord, newAdditionRecord))
                    }

                    ifHasUnresolvedPropertyUsages {
                        traceElements.add(UnassignedValueUsedInAddition(event.additionRecord, it))
                    }
                }

                is Event.NestedObjectAccess -> resolvingPropertyLinks(propertyLinkResolver) {
                    val container = resolvePropertyLinksIn(event.nestedObjectAccessRecord.container)
                    val dataObject = resolvePropertyLinksIn(event.nestedObjectAccessRecord.dataObject)

                    ifOk {
                        val newNestedObjectAccessRecord = NestedObjectAccessRecord(
                            container = ensureResolved(container),
                            dataObject = ensureResolved(dataObject),
                            operationId = event.nestedObjectAccessRecord.operationId
                        )
                        newResolutionResultElements.registerNestedObjectAccess(newNestedObjectAccessRecord, event.isFromDefaults)
                        traceElements.add(RecordedNestedObjectAccess(event.nestedObjectAccessRecord, newNestedObjectAccessRecord))
                    }

                    ifHasUnresolvedPropertyUsages {
                        traceElements.add(UnassignedValueUsedInNestedObjectAccess(event.nestedObjectAccessRecord, it))
                    }
                }
            }
        }
        val assignments = propertyLinkResolver.getFinalAssignmentResults()

        val newResolutionResult = with(newResolutionResultElements) {
            ResolutionResult(
                resolutionResult.topLevelReceiver,
                newResultAssignments,
                newResultAdditions,
                newResultNestedObjectAccess,
                resolutionResult.errors,
                newResultAssignmentsFromDefaults,
                newResultAdditionsFromDefaults,
                newResultNestedObjectAccessFromDefaults
            )
        }

        return PropertyLinkTrace(traceElements, newResolutionResult, assignments)
    }

    private sealed interface Event {

        val operationId: OperationId
        val relevantOrigins: List<ObjectOrigin>

        val isFromDefaults: Boolean

        data class Assignment(val assignmentRecord: AssignmentRecord, override val isFromDefaults: Boolean) : Event {
            override val operationId: OperationId = assignmentRecord.operationId
            override val relevantOrigins: List<ObjectOrigin> get() = listOf(assignmentRecord.lhs.receiverObject, assignmentRecord.rhs)
        }

        data class Addition(val additionRecord: DataAdditionRecord, override val isFromDefaults: Boolean) : Event {
            override val operationId: OperationId = additionRecord.operationId
            override val relevantOrigins: List<ObjectOrigin> get() = listOf(additionRecord.container, additionRecord.dataObject)
        }

        data class NestedObjectAccess(val nestedObjectAccessRecord: NestedObjectAccessRecord, override val isFromDefaults: Boolean) : Event {
            override val operationId: OperationId = nestedObjectAccessRecord.operationId
            override val relevantOrigins: List<ObjectOrigin> get() = listOf(nestedObjectAccessRecord.container, nestedObjectAccessRecord.dataObject)
        }
    }
}

private fun resolvingPropertyLinks(propertyLinksResolver: PropertyLinksResolver, f: PropertyLinksResolutionContext.() -> Unit): Unit = PropertyLinksResolutionContext(propertyLinksResolver).run(f)

private class PropertyLinksResolutionContext(private val resolver: PropertyLinksResolver) {
    val unresolvedUsages = mutableListOf<UnresolvedPropertyUsage>()

    fun resolvePropertyLinksIn(objectOrigin: ObjectOrigin): PropertyLinkResolutionResult =
        resolver.resolvePropertyLinksIn(objectOrigin).also { if (it is UnresolvedPropertyUsage) unresolvedUsages.add(it) }

    inner class ResolvedLinksContext {
        fun ensureResolved(propertyLinkResolutionResult: PropertyLinkResolutionResult): ObjectOrigin = (propertyLinkResolutionResult as Ok).objectOrigin
    }

    fun <T> ifOk(f: ResolvedLinksContext.() -> T?): T? = if (unresolvedUsages.isEmpty()) ResolvedLinksContext().f() else null
    fun <T> ifHasUnresolvedPropertyUsages(f: (UnresolvedPropertyUsage) -> T?): T? = if (unresolvedUsages.isNotEmpty()) f(unresolvedUsages.first()) else null
}


/**
 * Represents "traced" resolution results, which have as much property transformed to their actual value origins as possible.
 *
 * The [trace] contain a log of tracing, including the encountered problems.
 *
 * The [resolvedPropertyLinksResolutionResult] is the version of [ResolutionResult] with all its object origins transformed by replacing
 * property references with the actual property values.
 *
 * The [finalAssignments] is a convenience container that has tracing results for all properties, indicating the successfully assigned values
 * in the final state, or the failures, if the property is used but not resolved.
 * Note: the final state might not include some of the assignments, if the corresponding property got reassigned.
 */
class PropertyLinkTrace(
    val trace: List<PropertyLinkTraceElement>,
    val resolvedPropertyLinksResolutionResult: ResolutionResult,
    val finalAssignments: Map<PropertyReferenceResolution, PropertyLinksResolver.AssignmentResolutionResult>,
)


sealed interface PropertyLinkTraceElement {
    val originElement: LanguageTreeElement

    sealed interface AssignmentPropertyLinkTraceElement : PropertyLinkTraceElement {
        val lhs: PropertyReferenceResolution
        val rhs: ObjectOrigin
    }

    sealed interface FailedToResolveLinks : PropertyLinkTraceElement

    data class RecordedAssignment(
        override val originElement: LanguageTreeElement,
        override val lhs: PropertyReferenceResolution,
        override val rhs: ObjectOrigin,
        val resolvedRhs: ObjectOrigin,
        val assignmentMethod: AssignmentMethod
    ) : AssignmentPropertyLinkTraceElement

    data class UnassignedValueUsedInAssignment(
        override val originElement: LanguageTreeElement,
        val assignmentAdditionResult: PropertyLinksResolver.AssignmentAdditionResult,
        override val lhs: PropertyReferenceResolution,
        override val rhs: ObjectOrigin
    ) : AssignmentPropertyLinkTraceElement, FailedToResolveLinks

    data class Reassignment(
        override val originElement: LanguageTreeElement,
        val assignmentAdditionResult: PropertyLinksResolver.AssignmentAdditionResult.Reassignment,
        override val lhs: PropertyReferenceResolution,
        override val rhs: ObjectOrigin,
    ) : AssignmentPropertyLinkTraceElement, FailedToResolveLinks

    data class RecordedAddition(
        val originalAddition: DataAdditionRecord,
        val resolvedAddition: DataAdditionRecord
    ) : PropertyLinkTraceElement {
        override val originElement: LanguageTreeElement
            get() = originalAddition.dataObject.originElement
    }

    data class UnassignedValueUsedInAddition(
        val additionRecord: DataAdditionRecord,
        val unassignedPropertyUsage: UnresolvedPropertyUsage
    ) : FailedToResolveLinks {
        override val originElement: LanguageTreeElement
            get() = additionRecord.dataObject.originElement
    }

    data class RecordedNestedObjectAccess(
        val originalNestedObjectAccess: NestedObjectAccessRecord,
        val resolvedNestedObjectAccess: NestedObjectAccessRecord
    ) : PropertyLinkTraceElement {
        override val originElement: LanguageTreeElement
            get() = originalNestedObjectAccess.dataObject.originElement
    }

    data class UnassignedValueUsedInNestedObjectAccess(
        val nestedObjectAccessRecord: NestedObjectAccessRecord,
        val unassignedPropertyUsage: UnresolvedPropertyUsage
    ) : FailedToResolveLinks {
        override val originElement: LanguageTreeElement
            get() = nestedObjectAccessRecord.dataObject.originElement
    }
}
