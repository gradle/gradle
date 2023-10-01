package com.h0tk3y.kotlin.staticObjectNotation.objectGraph

import com.h0tk3y.kotlin.staticObjectNotation.analysis.ConfigureAccessor
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ObjectOrigin
import com.h0tk3y.kotlin.staticObjectNotation.analysis.PropertyReferenceResolution

class AssignmentResolver {
    private val assignmentByNode = mutableMapOf<ResolutionNode.Property, ResolutionNode>()

    fun addAssignment(lhsProperty: PropertyReferenceResolution, rhsOrigin: ObjectOrigin) {
        val lhsOwner = resolveToObjectOrPropertyReference(lhsProperty.receiverObject)
        val lhsPropertyWithResolvedReceiver = PropertyReferenceResolution(lhsOwner, lhsProperty.property)
        val lhsNode = ResolutionNode.Property(lhsPropertyWithResolvedReceiver)

        val rhsNode: ResolutionNode = when (val rhs = resolveToObjectOrPropertyReference(rhsOrigin)) {
            is ObjectOrigin.PropertyReference ->
                ResolutionNode.Property(PropertyReferenceResolution(rhs.receiver, rhs.property))

            else -> ResolutionNode.PrimitiveValue(rhs)
        }

        assignmentByNode[lhsNode] = rhsNode
    }

    fun getAssignedObjects(): Map<PropertyReferenceResolution, ObjectOrigin> {
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
                val result = get(lhs)
                if (result is ResolutionNode.PrimitiveValue) {
                    put(lhs.propertyReferenceResolution, result.objectOrigin)
                }
            }
        }
    }

    private fun resolveToObjectOrPropertyReference(objectOrigin: ObjectOrigin): ObjectOrigin = when (objectOrigin) {
        is ObjectOrigin.ConfigureReceiver -> resolveToObjectOrPropertyReference(resolveConfigureReceiver(objectOrigin))
        is ObjectOrigin.FromLocalValue -> resolveToObjectOrPropertyReference(objectOrigin.assigned)
        is ObjectOrigin.BuilderReturnedReceiver -> resolveToObjectOrPropertyReference(objectOrigin.receiverObject)

        is ObjectOrigin.NewObjectFromFunctionInvocation -> objectOrigin

        is ObjectOrigin.ConstantOrigin,
        is ObjectOrigin.External,
        is ObjectOrigin.NullObjectOrigin,
        is ObjectOrigin.PropertyReference,
        is ObjectOrigin.TopLevelReceiver -> objectOrigin
    }

    private fun resolveConfigureReceiver(objectOrigin: ObjectOrigin.ConfigureReceiver) =
        when (val accessor = objectOrigin.accessor) {
            is ConfigureAccessor.Property -> toPropertyReference(objectOrigin, accessor)
        }

    private fun toPropertyReference(
        objectOrigin: ObjectOrigin.ConfigureReceiver,
        accessor: ConfigureAccessor.Property
    ) = ObjectOrigin.PropertyReference(
        objectOrigin.receiverObject, accessor.dataProperty, objectOrigin.originElement
    )

    sealed interface ResolutionNode {
        data class Property(val propertyReferenceResolution: PropertyReferenceResolution) : ResolutionNode
        data class PrimitiveValue(val objectOrigin: ObjectOrigin) : ResolutionNode
    }
}