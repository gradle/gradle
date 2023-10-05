package com.h0tk3y.kotlin.staticObjectNotation.objectGraph

import com.h0tk3y.kotlin.staticObjectNotation.analysis.ConfigureAccessor
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ObjectOrigin
import com.h0tk3y.kotlin.staticObjectNotation.analysis.PropertyReferenceResolution
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.ExpressionResolutionProgress.Ok
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.ExpressionResolutionProgress.UnresolvedReceiver

class AssignmentResolver {
    private val assignmentByNode = mutableMapOf<ResolutionNode.Property, ResolutionNode>()

    sealed interface AssignmentAdditionResult {
        data class AssignmentAdded(
            val resolvedLhs: PropertyReferenceResolution
        ) : AssignmentAdditionResult

        data class UnresolvedValueUsedInLhs(
            val value: ObjectOrigin
        ) : AssignmentAdditionResult

        data class UnresolvedValueUsedInRhs(
            val value: ObjectOrigin
        ) : AssignmentAdditionResult
    }

    fun addAssignment(lhsProperty: PropertyReferenceResolution, rhsOrigin: ObjectOrigin): AssignmentAdditionResult =
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
                        AssignmentAdditionResult.AssignmentAdded(lhsNode.propertyReferenceResolution)
                    }

                    // TODO: lazy semantics for properties
                    is UnresolvedReceiver -> AssignmentAdditionResult.UnresolvedValueUsedInRhs(rhsResult.accessOrigin)
                }
            }
        }

    sealed interface AssignmentResolutionResult {
        data class Assigned(val objectOrigin: ObjectOrigin) : AssignmentResolutionResult
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
                        is ResolutionNode.PrimitiveValue -> AssignmentResolutionResult.Assigned(result.objectOrigin)
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
            is ObjectOrigin.ConfigureReceiver -> resolveToObjectOrPropertyReference(
                resolveConfigureReceiver(objectOrigin)
            )
            is ObjectOrigin.FromLocalValue -> resolveToObjectOrPropertyReference(objectOrigin.assigned)
            is ObjectOrigin.BuilderReturnedReceiver -> resolveToObjectOrPropertyReference(objectOrigin.receiverObject)

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
                            UnresolvedReceiver(objectOrigin)
                        }
                    }
                }
            }

            is ObjectOrigin.NewObjectFromFunctionInvocation -> {
                val receiver = objectOrigin.receiverObject
                if (receiver == null) {
                    Ok(objectOrigin)
                } else {
                    when (val receiverResolutionResult = resolveToObjectOrPropertyReference(receiver)) {
                        is UnresolvedReceiver -> receiverResolutionResult
                        is Ok -> Ok(objectOrigin.copy(receiverObject = receiverResolutionResult.objectOrigin))
                    }
                }
            }

            is ObjectOrigin.ConstantOrigin,
            is ObjectOrigin.External,
            is ObjectOrigin.NullObjectOrigin,
            is ObjectOrigin.TopLevelReceiver -> Ok(objectOrigin)
        }

    private fun resolveConfigureReceiver(objectOrigin: ObjectOrigin.ConfigureReceiver) =
        when (val accessor = objectOrigin.accessor) {
            is ConfigureAccessor.Property -> toPropertyReference(objectOrigin, accessor)
        }

    private fun toPropertyReference(
        objectOrigin: ObjectOrigin.ConfigureReceiver, accessor: ConfigureAccessor.Property
    ) = ObjectOrigin.PropertyReference(
        objectOrigin.receiverObject, accessor.dataProperty, objectOrigin.originElement
    )

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