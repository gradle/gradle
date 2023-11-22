package com.h0tk3y.kotlin.staticObjectNotation.objectGraph

import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import com.h0tk3y.kotlin.staticObjectNotation.analysis.getDataType
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.AssignmentResolutionResult.Assigned
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.AssignmentResolutionResult.Unassigned
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver.ExpressionResolutionProgress.Ok

sealed interface ObjectReflection {
    val type: DataType
    val objectOrigin: ObjectOrigin

    data class DataObjectReflection(
        val identity: Long,
        override val type: DataType.DataClass<*>,
        override val objectOrigin: ObjectOrigin,
        val properties: Map<DataProperty, PropertyValueReflection>,
        val addedObjects: List<ObjectReflection>
    ) : ObjectReflection

    data class ConstantValue(
        override val type: DataType.ConstantType<*>,
        override val objectOrigin: ObjectOrigin.ConstantOrigin,
        val value: Any
    ) : ObjectReflection

    data class External(
        override val type: DataType,
        override val objectOrigin: ObjectOrigin.External,
    ) : ObjectReflection {
        val key: ExternalObjectProviderKey get() = objectOrigin.key
    }

    data class Null(override val objectOrigin: ObjectOrigin) : ObjectReflection {
        override val type: DataType
            get() = DataType.NullType
    }

    data class DefaultValue(
        override val type: DataType,
        override val objectOrigin: ObjectOrigin
    ) : ObjectReflection

    data class PureFunctionInvocation(
        override val type: DataType,
        override val objectOrigin: ObjectOrigin.FunctionOrigin,
        val parameterResolution: Map<DataParameter, ObjectReflection>
    ) : ObjectReflection

    data class AddedByUnitInvocation(
        override val objectOrigin: ObjectOrigin
    ) : ObjectReflection {
        override val type = DataType.UnitType
    }
}

data class PropertyValueReflection(
    val value: ObjectReflection,
    val assignmentMethod: AssignmentMethod
)

fun reflect(
    objectOrigin: ObjectOrigin,
    context: ReflectionContext,
): ObjectReflection {
    val type = with(context.typeRefContext) {
        getDataType(objectOrigin)
    }
    return when (objectOrigin) {
        is ObjectOrigin.ConstantOrigin -> ObjectReflection.ConstantValue(
            type as DataType.ConstantType<*>,
            objectOrigin,
            objectOrigin.literal.value
        )

        is ObjectOrigin.External -> ObjectReflection.External(type, objectOrigin)

        is ObjectOrigin.NullObjectOrigin -> ObjectReflection.Null(objectOrigin)

        is ObjectOrigin.TopLevelReceiver -> reflectData(0, type as DataType.DataClass<*>, objectOrigin, context)

        is ObjectOrigin.PropertyDefaultValue -> reflectDefaultValue(objectOrigin, context)
        is ObjectOrigin.FunctionInvocationOrigin -> context.functionCall(objectOrigin.invocationId) {
            when (objectOrigin.function.semantics) {
                is FunctionSemantics.AddAndConfigure -> {
                    if (type == DataType.UnitType) {
                        ObjectReflection.AddedByUnitInvocation(objectOrigin)
                    } else {
                        reflectData(
                            objectOrigin.invocationId,
                            type as DataType.DataClass<*>,
                            objectOrigin,
                            context
                        )
                    }
                }

                is FunctionSemantics.Pure -> ObjectReflection.PureFunctionInvocation(
                    type,
                    objectOrigin,
                    objectOrigin.parameterBindings.bindingMap.mapValues { reflect(it.value, context) }
                )

                is FunctionSemantics.AccessAndConfigure,
                is FunctionSemantics.Builder -> error("can't appear here")
            }
        }

        is ObjectOrigin.ConfigureReceiver,
        is ObjectOrigin.PropertyReference,
        is ObjectOrigin.FromLocalValue -> error("value origin needed")
    }
}

fun reflectDefaultValue(
    objectOrigin: ObjectOrigin.PropertyDefaultValue,
    context: ReflectionContext
): ObjectReflection {
    return when (val type = context.typeRefContext.getDataType(objectOrigin)) {
        is DataType.ConstantType<*> -> ObjectReflection.DefaultValue(type, objectOrigin)
        is DataType.DataClass<*> -> reflectData(-1L, type, objectOrigin, context)
        DataType.NullType -> error("Null type can't appear in property types")
        DataType.UnitType -> error("Unit can't appear in property types")
    }
}

fun defaultConstantValue(type: DataType.ConstantType<*>) = when (type) {
    DataType.BooleanDataType -> false
    DataType.IntDataType -> 0
    DataType.LongDataType -> 0L
    DataType.StringDataType -> ""
}

fun reflectData(
    identity: Long,
    type: DataType.DataClass<*>,
    objectOrigin: ObjectOrigin,
    context: ReflectionContext
): ObjectReflection.DataObjectReflection {
    val propertiesWithValue = type.properties.mapNotNull {
        val referenceResolution = PropertyReferenceResolution(objectOrigin, it)
        when (val assignment = context.resolveAssignment(referenceResolution)) {
            is Assigned -> it to PropertyValueReflection(reflect(assignment.objectOrigin, context), assignment.assignmentMethod)
            else -> if (it.hasDefaultValue) {
                it to PropertyValueReflection(reflect(ObjectOrigin.PropertyDefaultValue(objectOrigin, it, objectOrigin.originElement), context), AssignmentMethod.AsConstructed)
            } else null
        }
    }.toMap()
    val added = context.additionsByResolvedContainer[objectOrigin].orEmpty().map { reflect(it, context) }
    return ObjectReflection.DataObjectReflection(identity, type, objectOrigin, propertiesWithValue, added)
}

fun ReflectionContext.resolveAssignment(
    property: PropertyReferenceResolution,
): AssignmentResolver.AssignmentResolutionResult =
    trace.resolvedAssignments.get(property) ?: Unassigned(property)

class ReflectionContext(
    val typeRefContext: TypeRefContext,
    val resolutionResult: ResolutionResult,
    val trace: AssignmentTrace,
) {
    val additionsByResolvedContainer = resolutionResult.additions.mapNotNull {
        val resolvedContainer = trace.resolver.resolveToObjectOrPropertyReference(it.container)
        val obj = trace.resolver.resolveToObjectOrPropertyReference(it.dataObject)
        if (resolvedContainer is Ok && obj is Ok) {
            DataAddition(resolvedContainer.objectOrigin, obj.objectOrigin)
        } else null
    }.groupBy({ it.container }, valueTransform = { it.dataObject })

    fun functionCall(callId: Long, resolveIfNotResolved: () -> ObjectReflection) =
        functionCallResults.getOrPut(callId, resolveIfNotResolved)

    private val functionCallResults = mutableMapOf<Long, ObjectReflection>()
}
