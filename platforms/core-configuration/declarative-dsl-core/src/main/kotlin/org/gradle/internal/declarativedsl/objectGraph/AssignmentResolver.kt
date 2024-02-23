package org.gradle.internal.declarativedsl.objectGraph

import org.gradle.internal.declarativedsl.analysis.AssignmentMethod
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.PropertyReferenceResolution
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver.ExpressionResolutionProgress.Ok
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver.ExpressionResolutionProgress.UnresolvedReceiver


class AssignmentResolver {
    private
    val assignmentByNode = mutableMapOf<ResolutionNode.Property, ResolutionNode>()
    private
    val assignmentMethodByProperty = mutableMapOf<ResolutionNode.Property, AssignmentMethod>()

    sealed interface AssignmentAdditionResult {
        data class AssignmentAdded(
            val resolvedLhs: PropertyReferenceResolution,
            val assignmentMethod: AssignmentMethod
        ) : AssignmentAdditionResult

        data class UnresolvedValueUsedInLhs(
            val value: ObjectOrigin
        ) : AssignmentAdditionResult

        data class UnresolvedValueUsedInRhs(
            val value: ObjectOrigin
        ) : AssignmentAdditionResult
    }

    fun addAssignment(lhsProperty: PropertyReferenceResolution, rhsOrigin: ObjectOrigin, assignmentMethod: AssignmentMethod): AssignmentAdditionResult =
        when (val lhsOwner = resolveToObjectOrPropertyReference(lhsProperty.receiverObject)) {
            is UnresolvedReceiver -> {
                AssignmentAdditionResult.UnresolvedValueUsedInLhs(lhsOwner.accessOrigin)
            }

            is Ok -> {
                val lhsPropertyWithResolvedReceiver = PropertyReferenceResolution(lhsOwner.objectOrigin, lhsProperty.property)
                val lhsNode = ResolutionNode.Property(lhsPropertyWithResolvedReceiver)

                when (val rhsResult = resolveToObjectOrPropertyReference(rhsOrigin)) {
                    is Ok -> {
                        val rhsNode: ResolutionNode = when (val rhs = rhsResult.objectOrigin) {
                            is ObjectOrigin.PropertyReference ->
                                ResolutionNode.Property(PropertyReferenceResolution(rhs.receiver, rhs.property))

                            else -> ResolutionNode.PrimitiveValue(rhs)
                        }
                        assignmentByNode[lhsNode] = rhsNode
                        assignmentMethodByProperty[lhsNode] = assignmentMethod
                        AssignmentAdditionResult.AssignmentAdded(lhsNode.propertyReferenceResolution, assignmentMethod)
                    }

                    // TODO: lazy semantics for properties
                    is UnresolvedReceiver -> AssignmentAdditionResult.UnresolvedValueUsedInRhs(rhsResult.accessOrigin)
                }
            }
        }

    sealed interface AssignmentResolutionResult {
        data class Assigned(val objectOrigin: ObjectOrigin, val assignmentMethod: AssignmentMethod) : AssignmentResolutionResult
        data class Unassigned(val property: PropertyReferenceResolution) : AssignmentResolutionResult
    }

    fun getAssignmentResults(): Map<PropertyReferenceResolution, AssignmentResolutionResult> {
        val dsu = (assignmentByNode.keys + assignmentByNode.values).associateWithTo(mutableMapOf()) { it }

        fun get(node: ResolutionNode): ResolutionNode = when (val value = dsu.getValue(node)) {
            node -> node
            else -> get(value).also { dsu[node] = it }
        }

        fun union(left: ResolutionNode, right: ResolutionNode) {
            dsu[get(left)] = right
        }

        assignmentByNode.forEach { (key, value) ->
            union(key, value)
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

    sealed interface ExpressionResolutionProgress {
        data class Ok(val objectOrigin: ObjectOrigin) : ExpressionResolutionProgress
        data class UnresolvedReceiver(val accessOrigin: ObjectOrigin) : ExpressionResolutionProgress
    }

    fun resolveToObjectOrPropertyReference(objectOrigin: ObjectOrigin): ExpressionResolutionProgress =
        when (objectOrigin) {
            is ObjectOrigin.DelegatingObjectOrigin -> resolveToObjectOrPropertyReference(objectOrigin.delegate)

            is ObjectOrigin.PropertyReference -> {
                val receiver = objectOrigin.receiver
                when (val receiverResolutionResult = resolveToObjectOrPropertyReference(receiver)) {
                    is UnresolvedReceiver -> receiverResolutionResult
                    is Ok -> {
                        val receiverOrigin = receiverResolutionResult.objectOrigin
                        val refNode =
                            ResolutionNode.Property(PropertyReferenceResolution(receiverOrigin, objectOrigin.property))

                        val receiverAssigned = assignmentByNode[refNode]
                        if (receiverAssigned != null) {
                            Ok(receiverAssigned.toOrigin(objectOrigin))
                        } else {
                            if (objectOrigin.property.hasDefaultValue) {
                                Ok(
                                    ObjectOrigin.PropertyDefaultValue(
                                        receiverOrigin, objectOrigin.property, objectOrigin.originElement
                                    )
                                )
                            } else {
                                UnresolvedReceiver(objectOrigin)
                            }
                        }
                    }
                }
            }

            is ObjectOrigin.NewObjectFromMemberFunction -> withResolvedReceiver(objectOrigin) { objectOrigin.copy(receiver = it) }
            is ObjectOrigin.CustomConfigureAccessor -> withResolvedReceiver(objectOrigin) { objectOrigin.copy(receiver = it) }
            is ObjectOrigin.ConfiguringLambdaReceiver -> withResolvedReceiver(objectOrigin) { objectOrigin.copy(receiver = it) }

            is ObjectOrigin.NewObjectFromTopLevelFunction,
            is ObjectOrigin.ConstantOrigin,
            is ObjectOrigin.External,
            is ObjectOrigin.NullObjectOrigin,
            is ObjectOrigin.PropertyDefaultValue, // TODO: is it so?
            is ObjectOrigin.TopLevelReceiver -> Ok(objectOrigin)
        }

    private
    fun withResolved(other: ObjectOrigin, ifResolved: (receiver: ObjectOrigin) -> ObjectOrigin): ExpressionResolutionProgress {
        return when (val receiverResolutionResult = resolveToObjectOrPropertyReference(other)) {
            is UnresolvedReceiver -> receiverResolutionResult
            is Ok -> Ok(ifResolved(receiverResolutionResult.objectOrigin))
        }
    }

    private
    fun withResolvedReceiver(objectOrigin: ObjectOrigin.HasReceiver, ifReceiverResolved: (receiver: ObjectOrigin) -> ObjectOrigin): ExpressionResolutionProgress {
        val receiver = objectOrigin.receiver
        return withResolved(receiver, ifReceiverResolved)
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
