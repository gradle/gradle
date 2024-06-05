package org.gradle.internal.declarativedsl.objectGraph

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.ExternalObjectProviderKey
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.AssignmentMethod
import org.gradle.internal.declarativedsl.analysis.OperationId
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.PropertyReferenceResolution
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.TypeRefContext
import org.gradle.internal.declarativedsl.analysis.access
import org.gradle.internal.declarativedsl.analysis.getDataType
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver.AssignmentResolutionResult.Assigned
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver.AssignmentResolutionResult.Unassigned
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver.ExpressionResolutionProgress.Ok


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
                    objectOrigin.parameterBindings.bindingMap.mapValues { reflect(it.value, context) }
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

        is ObjectOrigin.ImplicitThisReceiver -> reflect(objectOrigin.resolvedTo, context)
        is ObjectOrigin.AddAndConfigureReceiver -> reflect(objectOrigin.receiver, context)
    }
}


fun reflectDefaultValue(
    objectOrigin: ObjectOrigin.PropertyDefaultValue,
    context: ReflectionContext
): ObjectReflection {
    return when (val type = context.typeRefContext.getDataType(objectOrigin)) {
        is DataType.ConstantType<*> -> ObjectReflection.DefaultValue(type, objectOrigin)
        is DataClass -> reflectData(OperationId(-1L, DefaultOperationGenerationId.preExisting), type, objectOrigin, context)
        is DataType.NullType -> error("Null type can't appear in property types")
        is DataType.UnitType -> error("Unit can't appear in property types")
        else -> { error("Unhandled data type: ${type.javaClass.simpleName}") }
    }
}


fun reflectData(
    identity: OperationId,
    type: DataClass,
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
    val customAccessors = context.customAccessorsUsedByReceiver[objectOrigin].orEmpty()
    val lambdaReceiversAccessed = context.lambdaAccessorsUsedByReceiver[objectOrigin].orEmpty()
    return ObjectReflection.DataObjectReflection(
        identity, type, objectOrigin,
        properties = propertiesWithValue,
        addedObjects = added,
        customAccessorObjects = customAccessors.map { reflect(it, context) },
        lambdaAccessedObjects = lambdaReceiversAccessed.map { reflect(it, context) }
    )
}


fun ReflectionContext.resolveAssignment(
    property: PropertyReferenceResolution,
): AssignmentResolver.AssignmentResolutionResult =
    trace.resolvedAssignments[property] ?: Unassigned(property)


class ReflectionContext(
    val typeRefContext: TypeRefContext,
    val resolutionResult: ResolutionResult,
    val trace: AssignmentTrace,
) {
    val additionsByResolvedContainer = (resolutionResult.conventionAdditions + resolutionResult.additions).mapNotNull {
        val resolvedContainer = trace.resolver.resolveToObjectOrPropertyReference(it.container)
        val obj = trace.resolver.resolveToObjectOrPropertyReference(it.dataObject)
        if (resolvedContainer is Ok && obj is Ok) {
            DataAdditionRecord(resolvedContainer.objectOrigin, obj.objectOrigin)
        } else null
    }.groupBy({ it.container }, valueTransform = { it.dataObject })

    private
    val allReceiversResolved = run {
        val allReceiverReferences = resolutionResult.conventionAdditions.map { it.container } +
            resolutionResult.conventionAssignments.map { it.lhs.receiverObject } +
            resolutionResult.conventionNestedObjectAccess.map { it.dataObject.accessor.access(it.container, it.dataObject) } +
            resolutionResult.additions.map { it.container } +
            resolutionResult.assignments.map { it.lhs.receiverObject } +
            resolutionResult.nestedObjectAccess.map { it.dataObject.accessor.access(it.container, it.dataObject) }

        allReceiverReferences
            .map(trace.resolver::resolveToObjectOrPropertyReference)
            .filterIsInstance<Ok>()
            .map { it.objectOrigin }
            .flatMap { origin -> generateSequence(origin) { (it as? ObjectOrigin.HasReceiver)?.receiver } }
    }

    val customAccessorsUsedByReceiver: Map<ObjectOrigin, List<ObjectOrigin.CustomConfigureAccessor>> = run {
        allReceiversResolved.mapNotNull { (it as? ObjectOrigin.CustomConfigureAccessor)?.let { custom -> custom.receiver to custom } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second }).mapValues { it.value.distinct() }
    }

    val lambdaAccessorsUsedByReceiver: Map<ObjectOrigin, List<ObjectOrigin.ConfiguringLambdaReceiver>> = run {
        allReceiversResolved.mapNotNull { (it as? ObjectOrigin.ConfiguringLambdaReceiver)?.let { access -> access.receiver to access } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second }).mapValues { it.value.distinct() }
    }

    fun functionCall(operationId: OperationId, resolveIfNotResolved: () -> ObjectReflection) =
        functionCallResults.getOrPut(operationId, resolveIfNotResolved)

    private
    val functionCallResults = mutableMapOf<OperationId, ObjectReflection>()
}
