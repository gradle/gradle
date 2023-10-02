package com.h0tk3y.kotlin.staticObjectNotation.objectGraph

import com.h0tk3y.kotlin.staticObjectNotation.analysis.ConfigureAccessor
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ObjectOrigin
import com.h0tk3y.kotlin.staticObjectNotation.analysis.PropertyReferenceResolution
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.AssignmentResolutionProgress.Ok

class AssignmentResolver {
    private val assignmentByNode = mutableMapOf<ResolutionNode.UnassignedProperty, ResolutionNode>()

    fun addAssignment(lhsProperty: PropertyReferenceResolution, rhsOrigin: ObjectOrigin): AssignmentResolutionProgress {
        val lhsOwner = resolveToObjectOrPropertyReference(lhsProperty.receiverObject)
        if (lhsOwner is AssignmentResolutionProgress.UnresolvedReceiver) {
            return lhsOwner
        }
        lhsOwner as Ok
        val lhsPropertyWithResolvedReceiver = PropertyReferenceResolution(lhsOwner.objectOrigin, lhsProperty.property)
        val lhsNode = ResolutionNode.UnassignedProperty(lhsPropertyWithResolvedReceiver)

        val rhsNode: ResolutionNode = when (val rhsResult = resolveToObjectOrPropertyReference(rhsOrigin)) {
            is Ok -> {
                when (val rhs = rhsResult.objectOrigin) {
                    is ObjectOrigin.PropertyReference ->
                        ResolutionNode.UnassignedProperty(PropertyReferenceResolution(rhs.receiver, rhs.property))

                    else -> ResolutionNode.PrimitiveValue(rhs)
                }
            }

            else -> return rhsResult
        }

        assignmentByNode[lhsNode] = rhsNode
        return Ok(rhsNode.toOrigin(rhsOrigin))
    }

    sealed interface AssignmentResolutionResult {
        data class Assigned(val objectOrigin: ObjectOrigin) : AssignmentResolutionResult
        data class Unassigned(val objectOrigin: PropertyReferenceResolution) : AssignmentResolutionResult
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
                        is ResolutionNode.UnassignedProperty -> AssignmentResolutionResult.Unassigned(result.propertyReferenceResolution)
                    }
                })
            }
        }
    }

    sealed interface AssignmentResolutionProgress {
        data class Ok(val objectOrigin: ObjectOrigin) : AssignmentResolutionProgress
        data class UnresolvedReceiver(val accessOrigin: ObjectOrigin) : AssignmentResolutionProgress
    }

    private fun resolveToObjectOrPropertyReference(objectOrigin: ObjectOrigin): AssignmentResolutionProgress =
        when (objectOrigin) {
        is ObjectOrigin.ConfigureReceiver -> resolveToObjectOrPropertyReference(resolveConfigureReceiver(objectOrigin))
            is ObjectOrigin.FromLocalValue -> resolveToObjectOrPropertyReference(objectOrigin.assigned)
            is ObjectOrigin.BuilderReturnedReceiver -> resolveToObjectOrPropertyReference(objectOrigin.receiverObject)

            is ObjectOrigin.PropertyReference -> {
                val receiver = objectOrigin.receiver
                when (val receiverResolutionResult = resolveToObjectOrPropertyReference(receiver)) {
                    is AssignmentResolutionProgress.UnresolvedReceiver -> receiverResolutionResult
                    is Ok -> {
                        val receiverOrigin = receiverResolutionResult.objectOrigin
                        ResolutionNode.UnassignedProperty(PropertyReferenceResolution(receiverOrigin, objectOrigin.property))

                        Ok(
                            ObjectOrigin.PropertyReference(
                                receiverOrigin, objectOrigin.property, objectOrigin.originElement
                            )
                        )
                    }
                }
            }

            is ObjectOrigin.NewObjectFromFunctionInvocation -> {
                val receiver = objectOrigin.receiverObject
                if (receiver == null) {
                    Ok(objectOrigin)
                } else {
                    when (val receiverResolutionResult = resolveToObjectOrPropertyReference(receiver)) {
                        is AssignmentResolutionProgress.UnresolvedReceiver -> receiverResolutionResult
                        is Ok -> {
                            Ok(objectOrigin.copy(receiverObject = receiverResolutionResult.objectOrigin))
                        }
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
        data class UnassignedProperty(val propertyReferenceResolution: PropertyReferenceResolution) : ResolutionNode
        data class PrimitiveValue(val objectOrigin: ObjectOrigin) : ResolutionNode

        fun toOrigin(usage: ObjectOrigin) = when (this) {
            is PrimitiveValue -> objectOrigin
            is UnassignedProperty -> ObjectOrigin.PropertyReference(
                propertyReferenceResolution.receiverObject,
                propertyReferenceResolution.property,
                usage.originElement
            )
        }
    } 
}