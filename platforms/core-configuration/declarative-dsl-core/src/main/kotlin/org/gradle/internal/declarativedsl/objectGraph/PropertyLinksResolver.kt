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

import org.gradle.declarative.dsl.evaluation.OperationGenerationId
import org.gradle.internal.declarativedsl.analysis.AssignmentMethod
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ParameterValueBinding
import org.gradle.internal.declarativedsl.analysis.PropertyReferenceResolution
import org.gradle.internal.declarativedsl.analysis.access
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinksResolver.PropertyLinkResolutionResult.Ok
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinksResolver.PropertyLinkResolutionResult.UnresolvedPropertyUsage
import java.util.IdentityHashMap

/**
 * Instead of relying at runtime on an opaque storage mechanism, like JVM fields, DCL takes responsibility for knowing what
 * values are assigned to properties at any "time" (time modeled by monotonous [org.gradle.internal.declarativedsl.analysis.OperationId]).
 *
 * Therefore, it "unwraps" property usages directly to what was stored in the property at the time it was used.
 *
 * To do that, the property link resolver maintains a mapping of current property values, which is updated by new assignments
 * and can be queried to resolve a property reference to the property's value, but only at the right time: after all assignments
 * that should be observed have been recorded and before any assignment that should NOT be observed has been recorded.
 *
 * Current property values are queried by [resolvePropertyLinksIn], which also transforms any [ObjectOrigin] that contains
 * [ObjectOrigin.PropertyReference]s into the result that uses the current property value. This operation also stores the result
 * for that object origin, so querying it again for the same [ObjectOrigin] instance does not depend on the current property values anymore.
 *
 * New property assignments are added with [addAssignment], which mutates the current tracked property values.
 *
 * A normal workflow is: all object origins that appear in a [org.gradle.internal.declarativedsl.analysis.ResolutionResult] should be passed to [resolvePropertyLinksIn] after
 * the assignments to properties that they should observe have been recorded with [addAssignment] and before any assignment has been recorded that
 * should not be observed.
 * The [org.gradle.internal.declarativedsl.analysis.OperationId]s of the assignments and other effects recorded in the resolution result
 * are expected to be consistent with the order of observation described above.
 */
class PropertyLinksResolver {
    private
    val assignmentByNode = mutableMapOf<ResolutionNode.Property, GenerationResolutionNode>()

    private
    val assignmentMethodByProperty = mutableMapOf<ResolutionNode.Property, AssignmentMethod>()

    data class GenerationResolutionNode(
        val generationId: OperationGenerationId,
        val node: ResolutionNode
    )

    sealed interface AssignmentAdditionResult {
        data class AssignmentAdded(
            val resolvedLhs: PropertyReferenceResolution,
            val resolvedRhs: ObjectOrigin,
            val assignmentMethod: AssignmentMethod
        ) : AssignmentAdditionResult

        data class Reassignment(
            val resolvedLhs: PropertyReferenceResolution
        ) : AssignmentAdditionResult

        data class UnresolvedValueUsedInLhs(
            val value: ObjectOrigin
        ) : AssignmentAdditionResult

        data class UnresolvedValueUsedInRhs(
            val value: ObjectOrigin
        ) : AssignmentAdditionResult
    }

    @Suppress("NestedBlockDepth")
    fun addAssignment(lhsProperty: PropertyReferenceResolution, rhsOrigin: ObjectOrigin, assignmentMethod: AssignmentMethod, generationId: OperationGenerationId): AssignmentAdditionResult =
        when (val lhsOwner = resolvePropertyLinksIn(lhsProperty.receiverObject)) {
            is UnresolvedPropertyUsage -> {
                AssignmentAdditionResult.UnresolvedValueUsedInLhs(lhsOwner.accessOrigin)
            }

            is Ok -> {
                val lhsPropertyWithResolvedReceiver = PropertyReferenceResolution(lhsOwner.objectOrigin, lhsProperty.property)
                val lhsNode = ResolutionNode.Property(lhsPropertyWithResolvedReceiver)

                if (lhsNode in assignmentByNode && hasAssignmentInTheSameGeneration(assignmentByNode.getValue(lhsNode), generationId)) {
                    AssignmentAdditionResult.Reassignment(lhsPropertyWithResolvedReceiver)
                } else when (val rhsResult = resolvePropertyLinksIn(rhsOrigin)) {
                    is Ok -> {
                        val rhsNode: ResolutionNode = when (val rhs = rhsResult.objectOrigin) {
                            is ObjectOrigin.PropertyReference ->
                                ResolutionNode.Property(PropertyReferenceResolution(rhs.receiver, rhs.property))

                            else -> ResolutionNode.PrimitiveValue(rhs)
                        }

                        if (lhsNode !in assignmentByNode || hasAssignmentInLowerGeneration(assignmentByNode.getValue(lhsNode), generationId)) {
                            assignmentByNode[lhsNode] = GenerationResolutionNode(generationId, rhsNode)
                            assignmentMethodByProperty[lhsNode] = assignmentMethod
                            AssignmentAdditionResult.AssignmentAdded(lhsNode.propertyReferenceResolution, rhsResult.objectOrigin, assignmentMethod)
                        } else {
                            // We should never come across a situation where an assignment already exists that is in a higher generation,
                            // but if we do, just pull the emergency stop handle as this is indicative of a bug rather than a user error.
                            error("unexpected assignment in higher generation")
                        }
                    }

                    // TODO: lazy semantics for properties
                    is UnresolvedPropertyUsage -> AssignmentAdditionResult.UnresolvedValueUsedInRhs(rhsResult.accessOrigin)
                }
            }
        }

    private
    fun hasAssignmentInTheSameGeneration(existingNode: GenerationResolutionNode, generationId: OperationGenerationId): Boolean {
        return existingNode.generationId == generationId
    }

    private
    fun hasAssignmentInLowerGeneration(existingNode: GenerationResolutionNode, generationId: OperationGenerationId): Boolean {
        return existingNode.generationId < generationId
    }

    sealed interface AssignmentResolutionResult {
        data class Assigned(val objectOrigin: ObjectOrigin, val assignmentMethod: AssignmentMethod) : AssignmentResolutionResult
        data class Unassigned(val property: PropertyReferenceResolution) : AssignmentResolutionResult
    }

    fun getFinalAssignmentResults(): Map<PropertyReferenceResolution, AssignmentResolutionResult> {
        val dsu = (assignmentByNode.keys + assignmentByNode.values.map { it.node }).associateWithTo(mutableMapOf()) { it }

        fun get(node: ResolutionNode): ResolutionNode = when (val value = dsu.getValue(node)) {
            node -> node
            else -> get(value).also { dsu[node] = it }
        }

        fun union(left: ResolutionNode, right: ResolutionNode) {
            dsu[get(left)] = right
        }

        assignmentByNode.forEach { (key, value) ->
            union(key, value.node)
        }

        return buildMap {
            assignmentByNode.forEach { (lhs, _) ->
                put(lhs.propertyReferenceResolution, run {
                    when (val result = get(lhs)) {
                        is ResolutionNode.PrimitiveValue -> {
                            val assignmentMethod = assignmentMethodByProperty.getValue(lhs)
                            AssignmentResolutionResult.Assigned(result.objectOrigin, assignmentMethod)
                        }

                        is ResolutionNode.Property -> AssignmentResolutionResult.Unassigned(result.propertyReferenceResolution)
                    }
                })
            }
        }
    }

    sealed interface PropertyLinkResolutionResult {
        data class Ok(val objectOrigin: ObjectOrigin) : PropertyLinkResolutionResult
        data class UnresolvedPropertyUsage(val accessOrigin: ObjectOrigin) : PropertyLinkResolutionResult
    }

    private val mutableOriginResolutionTrace = IdentityHashMap<ObjectOrigin, PropertyLinkResolutionResult>()

    @Suppress("NestedBlockDepth")
    fun resolvePropertyLinksIn(objectOrigin: ObjectOrigin): PropertyLinkResolutionResult {
        mutableOriginResolutionTrace[objectOrigin]
            ?.let { return it }

        fun withResolvedParameterBindings(parameterValueBinding: ParameterValueBinding, ifResolved: (ParameterValueBinding) -> ObjectOrigin): PropertyLinkResolutionResult {
            val bindings = parameterValueBinding.copy(bindingMap = parameterValueBinding.bindingMap.mapValues { (_, value) ->
                when (val resolvedValue = resolvePropertyLinksIn(value.objectOrigin)) {
                    is Ok -> value.copy(objectOrigin = resolvedValue.objectOrigin)
                    else -> return@withResolvedParameterBindings resolvedValue
                }
            })

            return Ok(ifResolved(bindings))
        }

        val result = when (objectOrigin) {
            /**
             * If a property reference appears inside the object origin, find out what the current state of the property is and return it as the result.
             */
            is ObjectOrigin.PropertyReference -> {
                val receiver = objectOrigin.receiver
                when (val receiverResolutionResult = resolvePropertyLinksIn(receiver)) {
                    is UnresolvedPropertyUsage -> receiverResolutionResult
                    is Ok -> {
                        val refNode = ResolutionNode.Property(PropertyReferenceResolution(receiverResolutionResult.objectOrigin, objectOrigin.property))

                        val receiverAssigned = assignmentByNode[refNode]?.node
                        if (receiverAssigned != null) {
                            Ok(receiverAssigned.toOrigin(objectOrigin))
                        } else {
                            if (objectOrigin.property.hasDefaultValue) {
                                Ok(ObjectOrigin.PropertyDefaultValue(receiverResolutionResult.objectOrigin, objectOrigin.property, objectOrigin.originElement))
                            } else {
                                UnresolvedPropertyUsage(objectOrigin)
                            }
                        }
                    }
                }
            }

            is ObjectOrigin.FromLocalValue -> resolvePropertyLinksIn(objectOrigin.assigned)

            is ObjectOrigin.AugmentationOrigin -> {
                withResolvedProgress(objectOrigin.augmentedProperty.receiver) { receiver ->
                    withResolvedProgress(objectOrigin.augmentationOperand) { operand ->
                        withResolvedProgress(objectOrigin.augmentationResult) { result ->
                            val property = objectOrigin.augmentedProperty.copy(receiver = receiver)
                            Ok(objectOrigin.copy(augmentedProperty = property, augmentationOperand = operand, augmentationResult = result))
                        }
                    }
                }
            }

            is ObjectOrigin.AccessAndConfigureReceiver -> {
                withResolvedProgress(objectOrigin.receiver) { receiver ->
                    withResolvedProgress(objectOrigin.accessor.access(receiver, objectOrigin)) {
                        Ok(it)
                    }
                }
            }

            is ObjectOrigin.AddAndConfigureReceiver -> {
                withResolvedProgress(objectOrigin.receiver) {
                    Ok(objectOrigin.copy(receiver = it as ObjectOrigin.FunctionOrigin))
                }
            }

            is ObjectOrigin.BuilderReturnedReceiver -> {
                withResolvedProgress(objectOrigin.receiver) { receiver ->
                    withResolvedParameterBindings(objectOrigin.parameterBindings) { parameterValueBinding ->
                        objectOrigin.copy(receiver = receiver, parameterBindings = parameterValueBinding)
                    }
                }
            }

            is ObjectOrigin.ImplicitThisReceiver -> {
                withResolvedProgress(objectOrigin.resolvedTo) {
                    Ok(it)
                }
            }

            is ObjectOrigin.NewObjectFromMemberFunction -> withResolvedReceiverProgress(objectOrigin) { resolvedReceiver ->
                withResolvedParameterBindings(objectOrigin.parameterBindings) { parameterValueBinding ->
                    objectOrigin.copy(receiver = resolvedReceiver, parameterBindings = parameterValueBinding)
                }
            }

            is ObjectOrigin.NewObjectFromTopLevelFunction -> withResolvedParameterBindings(objectOrigin.parameterBindings) { parameterValueBinding ->
                objectOrigin.copy(parameterBindings = parameterValueBinding)
            }

            is ObjectOrigin.CustomConfigureAccessor -> withResolvedReceiver(objectOrigin) { objectOrigin.copy(receiver = it) }
            is ObjectOrigin.ConfiguringLambdaReceiver -> withResolvedReceiverProgress(objectOrigin) { receiver ->
                withResolvedParameterBindings(objectOrigin.parameterBindings) { parameterBindings ->
                    objectOrigin.copy(receiver = receiver, parameterBindings = parameterBindings)
                }
            }

            is ObjectOrigin.ConstantOrigin,
            is ObjectOrigin.EnumConstantOrigin,
            is ObjectOrigin.External,
            is ObjectOrigin.NullObjectOrigin,
            is ObjectOrigin.GroupedVarargValue,
            is ObjectOrigin.PropertyDefaultValue, // TODO: is it so?
            is ObjectOrigin.TopLevelReceiver -> Ok(objectOrigin)
        }

        mutableOriginResolutionTrace.put(objectOrigin, result)
        return result
    }

    private
    fun withResolvedProgress(other: ObjectOrigin, ifResolved: (resolved: ObjectOrigin) -> PropertyLinkResolutionResult): PropertyLinkResolutionResult {
        return when (val receiverResolutionResult = resolvePropertyLinksIn(other)) {
            is UnresolvedPropertyUsage -> receiverResolutionResult
            is Ok -> ifResolved(receiverResolutionResult.objectOrigin)
        }
    }

    private
    fun withResolvedReceiver(objectOrigin: ObjectOrigin.HasReceiver, ifReceiverResolved: (receiver: ObjectOrigin) -> ObjectOrigin) =
        withResolvedProgress(objectOrigin.receiver) { Ok(ifReceiverResolved(it)) }

    private
    fun withResolvedReceiverProgress(objectOrigin: ObjectOrigin.HasReceiver, ifReceiverResolved: (receiver: ObjectOrigin) -> PropertyLinkResolutionResult): PropertyLinkResolutionResult {
        val receiver = objectOrigin.receiver
        return withResolvedProgress(receiver, ifReceiverResolved)
    }

    sealed interface ResolutionNode {
        data class Property(val propertyReferenceResolution: PropertyReferenceResolution) : ResolutionNode
        data class PrimitiveValue(val objectOrigin: ObjectOrigin) : ResolutionNode

        fun toOrigin(usage: ObjectOrigin) = when (this) {
            is PrimitiveValue -> objectOrigin
            is Property -> ObjectOrigin.PropertyReference(
                propertyReferenceResolution.receiverObject,
                propertyReferenceResolution.property,
                usage.originElement
            )
        }
    }
}
