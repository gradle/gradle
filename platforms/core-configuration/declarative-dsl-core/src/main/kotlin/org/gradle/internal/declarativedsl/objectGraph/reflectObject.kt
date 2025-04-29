package org.gradle.internal.declarativedsl.objectGraph

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.dsl.schema.ExternalObjectProviderKey
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.AssignmentMethod
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.OperationId
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.TypeRefContext
import org.gradle.internal.declarativedsl.analysis.canonical
import org.gradle.internal.declarativedsl.analysis.getDataType
import org.gradle.internal.declarativedsl.language.DataTypeInternal


sealed interface ObjectReflection {
    val type: DataType
    val objectOrigin: ObjectOrigin

    data class DataObjectReflection(
        val identity: OperationId,
        override val type: DataClass,
        override val objectOrigin: ObjectOrigin,
        val properties: Map<DataProperty, PropertyValueReflection>,
        val addedObjects: List<ObjectReflection>,
        val customAccessorObjects: List<ObjectReflection>,
        val lambdaAccessedObjects: List<ObjectReflection>
    ) : ObjectReflection

    data class ConstantValue(
        override val type: DataType.ConstantType<*>,
        override val objectOrigin: ObjectOrigin.ConstantOrigin,
        val value: Any
    ) : ObjectReflection

    data class EnumValue(
        override val type: EnumClass,
        override val objectOrigin: ObjectOrigin.EnumConstantOrigin
    ) : ObjectReflection

    data class External(
        override val type: DataType,
        override val objectOrigin: ObjectOrigin.External,
    ) : ObjectReflection {
        val key: ExternalObjectProviderKey
            get() = objectOrigin.key
    }

    data class Null(override val objectOrigin: ObjectOrigin) : ObjectReflection {
        override val type: DataType
            get() = DataTypeInternal.DefaultNullType
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
        override val type = DataTypeInternal.DefaultUnitType
    }

    data class GroupedVarargReflection(
        override val type: DataType,
        val elementsReflection: List<ObjectReflection>,
        override val objectOrigin: ObjectOrigin.GroupedVarargValue
    ) : ObjectReflection
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
        is ObjectOrigin.DelegatingObjectOrigin -> reflect(objectOrigin.canonical(), context)

        is ObjectOrigin.ConstantOrigin -> ObjectReflection.ConstantValue(
            type as DataType.ConstantType<*>,
            objectOrigin,
            objectOrigin.literal.value
        )

        is ObjectOrigin.EnumConstantOrigin -> ObjectReflection.EnumValue(type as EnumClass, objectOrigin)

        is ObjectOrigin.External -> ObjectReflection.External(type, objectOrigin)

        is ObjectOrigin.NullObjectOrigin -> ObjectReflection.Null(objectOrigin)

        is ObjectOrigin.TopLevelReceiver -> reflectData(OperationId(0, DefaultOperationGenerationId.preExisting), type as DataClass, objectOrigin, context)

        is ObjectOrigin.ConfiguringLambdaReceiver -> reflectData(OperationId(-1L, DefaultOperationGenerationId.preExisting), type as DataClass, objectOrigin, context)

        is ObjectOrigin.PropertyDefaultValue -> reflectDefaultValue(objectOrigin, context)
        is ObjectOrigin.FunctionInvocationOrigin -> context.functionCall(objectOrigin.invocationId) {
            when (objectOrigin.function.semantics) {
                is FunctionSemantics.AddAndConfigure -> {
                    if (type is DataType.UnitType) {
                        ObjectReflection.AddedByUnitInvocation(objectOrigin)
                    } else {
                        reflectData(
                            objectOrigin.invocationId,
                            type as DataClass,
                            objectOrigin,
                            context
                        )
                    }
                }

                is FunctionSemantics.Pure -> ObjectReflection.PureFunctionInvocation(
                    type,
                    objectOrigin,
                    objectOrigin.parameterBindings.bindingMap.mapValues { reflect(it.value.objectOrigin, context) }
                )

                is FunctionSemantics.AccessAndConfigure -> {
                    when (objectOrigin) {
                        is ObjectOrigin.AccessAndConfigureReceiver -> reflect(objectOrigin.delegate, context)
                        else -> error("unexpected origin type")
                    }
                }

                is FunctionSemantics.Builder -> error("can't appear here")
            }
        }

        is ObjectOrigin.PropertyReference,
        is ObjectOrigin.FromLocalValue -> error("value origin needed")

        is ObjectOrigin.CustomConfigureAccessor -> reflectData(OperationId(-1L, DefaultOperationGenerationId.preExisting), type as DataClass, objectOrigin, context)

        is ObjectOrigin.GroupedVarargValue ->
            ObjectReflection.GroupedVarargReflection(
                objectOrigin.varargArrayType,
                objectOrigin.elementValues.map { reflect(it, context) },
                objectOrigin
            )
    }
}


fun reflectDefaultValue(
    objectOrigin: ObjectOrigin.PropertyDefaultValue,
    context: ReflectionContext
): ObjectReflection {
    return when (val type = context.typeRefContext.getDataType(objectOrigin)) {
        is DataType.ConstantType<*> -> ObjectReflection.DefaultValue(type, objectOrigin)
        is DataType.ParameterizedTypeInstance -> ObjectReflection.DefaultValue(type, objectOrigin)
        is DataClass -> reflectData(OperationId(-1L, DefaultOperationGenerationId.preExisting), type, objectOrigin, context)
        is EnumClass -> ObjectReflection.DefaultValue(type, objectOrigin)
        is DataType.NullType -> error("Null type can't appear in property types")
        is DataType.UnitType -> error("Unit can't appear in property types")
        is DataType.TypeVariableUsage -> error("Type variables cannot appear as value types, must be substituted")
    }
}


fun reflectData(
    identity: OperationId,
    type: DataClass,
    objectOrigin: ObjectOrigin,
    context: ReflectionContext
): ObjectReflection.DataObjectReflection {
    val canonicalOrigin = objectOrigin.canonical()

    val propertiesWithValue = type.properties.mapNotNull { property ->
        val assignment = context.assignmentsByResolvedReceiverAndProperty[canonicalOrigin]?.get(property)
        if (assignment != null) {
            property to PropertyValueReflection(reflect(assignment.rhs, context), assignment.assignmentMethod)
        } else if (property.hasDefaultValue) {
            property to PropertyValueReflection(reflect(ObjectOrigin.PropertyDefaultValue(objectOrigin, property, objectOrigin.originElement), context), AssignmentMethod.AsConstructed)
        } else null
    }.toMap()

    val added = context.additionsByResolvedContainer[canonicalOrigin].orEmpty().map { reflect(it, context) }
    val customAccessors = context.customAccessorsUsedByReceiver[canonicalOrigin].orEmpty()
    val lambdaReceiversAccessed = context.lambdaAccessorsUsedByReceiver[canonicalOrigin].orEmpty()
    return ObjectReflection.DataObjectReflection(
        identity, type, canonicalOrigin,
        properties = propertiesWithValue,
        addedObjects = added,
        customAccessorObjects = customAccessors.map { reflect(it, context) },
        lambdaAccessedObjects = lambdaReceiversAccessed.map { reflect(it, context) }
    )
}


class ReflectionContext(
    val typeRefContext: TypeRefContext,
    val resolutionResult: ResolutionResult,
) {
    val assignmentsByResolvedReceiverAndProperty = (resolutionResult.assignmentsFromDefaults + resolutionResult.assignments)
        .groupBy { it.lhs.receiverObject.canonical() }.mapValues { (_, assignments) -> assignments.asReversed().associateBy { it.lhs.property } }

    val additionsByResolvedContainer = (resolutionResult.additionsFromDefaults + resolutionResult.additions)
        .groupBy({ it.container.canonical() }, valueTransform = { it.dataObject })

    private
    val allReceiversResolved = run {
        val allReceiverReferences = resolutionResult.additionsFromDefaults.map { it.container } +
            resolutionResult.assignmentsFromDefaults.map { it.lhs.receiverObject } +
            resolutionResult.nestedObjectAccessFromDefaults.map { it.dataObject } +
            resolutionResult.additions.map { it.container } +
            resolutionResult.assignments.map { it.lhs.receiverObject } +
            resolutionResult.nestedObjectAccess.map { it.dataObject }

        allReceiverReferences
            .flatMap { origin -> generateSequence(origin.canonical()) { (it as? ObjectOrigin.HasReceiver)?.receiver?.canonical() } }
            .toSet()
    }

    val customAccessorsUsedByReceiver: Map<ObjectOrigin, List<ObjectOrigin.CustomConfigureAccessor>> = run {
        allReceiversResolved.mapNotNull { (it as? ObjectOrigin.CustomConfigureAccessor)?.let { custom -> custom.receiver to custom } }
            .groupBy(keySelector = { it.first.canonical() }, valueTransform = { it.second }).mapValues { it.value.distinct() }
    }

    val lambdaAccessorsUsedByReceiver: Map<ObjectOrigin, List<ObjectOrigin.ConfiguringLambdaReceiver>> = run {
        allReceiversResolved.mapNotNull { (it as? ObjectOrigin.ConfiguringLambdaReceiver)?.let { access -> access.receiver to access } }
            .groupBy(keySelector = { it.first.canonical() }, valueTransform = { it.second }).mapValues { it.value.distinct() }
    }

    fun functionCall(operationId: OperationId, resolveIfNotResolved: () -> ObjectReflection) =
        functionCallResults.getOrPut(operationId, resolveIfNotResolved)

    private
    val functionCallResults = mutableMapOf<OperationId, ObjectReflection>()
}
