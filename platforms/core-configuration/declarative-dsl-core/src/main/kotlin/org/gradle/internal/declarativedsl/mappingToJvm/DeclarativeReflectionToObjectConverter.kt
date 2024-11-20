package org.gradle.internal.declarativedsl.mappingToJvm

import org.gradle.api.internal.plugins.DslObject
import org.gradle.declarative.dsl.schema.DataBuilderFunction
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.ExternalObjectProviderKey
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.analysis.AssignmentMethod
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.OperationId
import org.gradle.internal.declarativedsl.analysis.ParameterValueBinding
import org.gradle.internal.declarativedsl.objectGraph.ObjectReflection
import org.gradle.internal.declarativedsl.objectGraph.PropertyValueReflection
import kotlin.reflect.KClass


class DeclarativeReflectionToObjectConverter(
    private val externalObjectsMap: Map<ExternalObjectProviderKey, Any>,
    private val topLevelObject: Any,
    private val functionResolver: RuntimeFunctionResolver,
    private val propertyResolver: RuntimePropertyResolver,
    private val customAccessors: RuntimeCustomAccessors
) : ReflectionToObjectConverter {

    override fun apply(objectReflection: ObjectReflection, conversionFilter: ReflectionToObjectConverter.ConversionFilter) {
        if (objectReflection is ObjectReflection.DataObjectReflection) {
            conversionFilter.filterProperties(objectReflection).forEach { property ->
                val assigned = objectReflection.properties.getValue(property)
                applyPropertyValue(objectReflection.objectOrigin, property, assigned)
                apply(assigned.value, conversionFilter)
                // TODO: record properties assigned in function calls or constructors, so that
                //       we can check that all properties were assigned
            }

            objectReflection.addedObjects.forEach { addedObject ->
                // We need the side effect of invoking the function producing the object, if it was there
                getObjectByResolvedOrigin(addedObject.objectOrigin).first
                apply(addedObject, conversionFilter)
                // TODO: maybe add the "containers" to the schema, so that added objects can be better expressed in this interpretation step
            }

            objectReflection.customAccessorObjects.forEach { customAccessor ->
                getObjectByResolvedOrigin(customAccessor.objectOrigin).validate { "could not get object by custom accessor ${customAccessor.objectOrigin}" }.first
                apply(customAccessor, conversionFilter)
            }

            objectReflection.lambdaAccessedObjects.forEach { lambdaAccessedObject ->
                getObjectByResolvedOrigin(lambdaAccessedObject.objectOrigin).validate { "could not get object from lambda passed to ${lambdaAccessedObject.objectOrigin}" }.first
                apply(lambdaAccessedObject, conversionFilter)
            }
        }
    }

    private
    val reflectionIdentityObjects = mutableMapOf<ObjectAccessKey, Any?>()

    private
    val reflectionIdentityPublicTypes = mutableMapOf<ObjectAccessKey, KClass<*>?>()

    private
    fun objectByIdentity(key: ObjectAccessKey, newInstanceAndPublicType: () -> InstanceAndPublicType): InstanceAndPublicType {
        // This code does not use `computeIfAbsent` because `newObject` can make reentrant calls, leading to CME.
        // Also, it does not check for the value being null, because null values are potentially allowed
        if (key !in reflectionIdentityObjects) {
            val instanceAndPublicType = newInstanceAndPublicType()
            reflectionIdentityObjects[key] = instanceAndPublicType.first
            reflectionIdentityPublicTypes[key] = instanceAndPublicType.second
            return instanceAndPublicType
        } else {
            return reflectionIdentityObjects[key] to reflectionIdentityPublicTypes[key]
        }
    }

    private
    fun applyPropertyValue(receiver: ObjectOrigin, property: DataProperty, assigned: PropertyValueReflection) {
        when (assigned.assignmentMethod) {
            is AssignmentMethod.Property -> setPropertyValue(receiver, property, getObjectByResolvedOrigin(assigned.value.objectOrigin).first)
            is AssignmentMethod.BuilderFunction -> invokeBuilderFunction(receiver, assigned.assignmentMethod.function, assigned.value.objectOrigin)
            is AssignmentMethod.AsConstructed -> Unit // the value should have already been passed to the constructor or the factory function
        }
    }

    private
    fun getObjectByResolvedOrigin(objectOrigin: ObjectOrigin): InstanceAndPublicType {
        return when (objectOrigin) {
            is ObjectOrigin.DelegatingObjectOrigin -> getObjectByResolvedOrigin(objectOrigin.delegate)
            is ObjectOrigin.ConstantOrigin -> objectOrigin.literal.value to objectOrigin.literal.type.constantType.kotlin
            is ObjectOrigin.EnumConstantOrigin -> getEnumConstant(objectOrigin) to Class.forName(objectOrigin.javaTypeName).kotlin
            is ObjectOrigin.External -> withFallbackPublicType(externalObjectsMap[objectOrigin.key] ?: error("no external object provided for external object key of ${objectOrigin.key}"))
            is ObjectOrigin.NewObjectFromMemberFunction -> objectByIdentity(ObjectAccessKey.Identity(objectOrigin.invocationId)) { objectFromMemberFunction(objectOrigin) }
            is ObjectOrigin.NewObjectFromTopLevelFunction -> objectByIdentity(ObjectAccessKey.Identity(objectOrigin.invocationId)) { objectFromTopLevelFunction() }
            is ObjectOrigin.NullObjectOrigin -> nullInstanceAndPublicType
            is ObjectOrigin.PropertyDefaultValue -> getPropertyValue(objectOrigin.receiver, objectOrigin.property)
            is ObjectOrigin.PropertyReference -> getPropertyValue(objectOrigin.receiver, objectOrigin.property)
            is ObjectOrigin.TopLevelReceiver -> topLevelObject to topLevelObject::class
            is ObjectOrigin.ConfiguringLambdaReceiver -> objectFromConfiguringLambda(objectOrigin)
            is ObjectOrigin.CustomConfigureAccessor -> objectFromCustomAccessor(objectOrigin)
        }
    }


    private
    sealed interface ObjectAccessKey {
        data class Identity(val id: OperationId) : ObjectAccessKey
        data class CustomAccessor(val owner: ObjectOrigin, val accessorId: String) : ObjectAccessKey
        data class ConfiguringLambda(val owner: ObjectOrigin, val function: SchemaFunction, val identityValues: List<Any?>) : ObjectAccessKey
    }


    private
    fun objectFromMemberFunction(
        origin: ObjectOrigin.NewObjectFromMemberFunction
    ): InstanceAndPublicType {
        val dataFun = origin.function
        val receiverInstanceAndPublicType = getObjectByResolvedOrigin(origin.receiver).validate { "tried to invoke a function $dataFun on a null receiver ${origin.receiver}" }

        val callResult = invokeFunctionAndGetResult(receiverInstanceAndPublicType, origin)
        return callResult.result
    }

    private
    fun invokeFunctionAndGetResult(
        receiverInstanceAndPublicType: Pair<Any, KClass<*>>,
        origin: ObjectOrigin.FunctionInvocationOrigin
    ): DeclarativeRuntimeFunction.InvocationResult {
        val dataFun = origin.function
        val receiverInstance = receiverInstanceAndPublicType.first
        val receiverKClass = receiverInstanceAndPublicType.second
        return when (val runtimeFunction = functionResolver.resolve(receiverKClass, dataFun)) {
            is RuntimeFunctionResolver.Resolution.Resolved -> {
                val bindingWithValues = origin.parameterBindings.bindingMap.mapValues { getObjectByResolvedOrigin(it.value).first }
                runtimeFunction.function.callByWithErrorHandling(receiverInstance, bindingWithValues, origin.parameterBindings.providesConfigureBlock)
            }

            RuntimeFunctionResolver.Resolution.Unresolved -> error("could not resolve a member function $dataFun call in the owner class $receiverKClass")
        }
    }

    private
    fun objectFromConfiguringLambda(
        origin: ObjectOrigin.ConfiguringLambdaReceiver
    ): InstanceAndPublicType = objectByIdentity(
        ObjectAccessKey.ConfiguringLambda(
            origin.receiver,
            origin.function,
            identityValues = origin.parameterBindings.bindingMap.map { (parameter, value) ->
                if (parameter.semantics is ParameterSemantics.IdentityKey) {
                    (value as? ObjectOrigin.ConstantOrigin)?.literal?.value
                } else null
            })
    ) {
        val function = origin.function
        val receiverInstanceAndPublicType = getObjectByResolvedOrigin(origin.receiver).validate { "tried to invoke a function $function on a null receiver ${origin.receiver}" }
        invokeFunctionAndGetResult(receiverInstanceAndPublicType, origin).capturedValue
    }

    private
    fun objectFromCustomAccessor(
        origin: ObjectOrigin.CustomConfigureAccessor
    ): InstanceAndPublicType = objectByIdentity(ObjectAccessKey.CustomAccessor(origin.receiver, origin.accessor.customAccessorIdentifier)) {
        val instanceAndPublicType = getObjectByResolvedOrigin(origin.receiver).validate { "receiver for custom accessor is null" }
        customAccessors.getObjectFromCustomAccessor(instanceAndPublicType.first, origin.accessor)
    }

    private
    fun invokeBuilderFunction(receiverOrigin: ObjectOrigin, function: DataBuilderFunction, valueOrigin: ObjectOrigin) {
        val receiverInstanceAndPublicType = getObjectByResolvedOrigin(receiverOrigin).validate { "tried to invoke a function $function on a null receiver $receiverOrigin" }
        val receiverInstance = receiverInstanceAndPublicType.first
        val receiverKClass = receiverInstanceAndPublicType.second
        val parameterBinding = ParameterValueBinding(mapOf(function.dataParameter to valueOrigin), false)

        when (val runtimeFunction = functionResolver.resolve(receiverKClass, function)) {
            is RuntimeFunctionResolver.Resolution.Resolved ->
                runtimeFunction.function.callByWithErrorHandling(receiverInstance, parameterBinding.bindingMap.mapValues { getObjectByResolvedOrigin(it.value).first }, parameterBinding.providesConfigureBlock).result
            RuntimeFunctionResolver.Resolution.Unresolved -> error("could not resolve a member function $function call in the owner class $receiverKClass")
        }
    }

    private
    fun objectFromTopLevelFunction(
        // origin: ObjectOrigin.NewObjectFromTopLevelFunction
    ): InstanceAndPublicType {
        TODO("support calls to top-level functions: they need to carry the owner class information to get resolved")
    }

    private
    fun getPropertyValue(receiver: ObjectOrigin, dataProperty: DataProperty): InstanceAndPublicType {
        val receiverInstanceAndPublicType = getObjectByResolvedOrigin(receiver).validate { "tried to access a property ${dataProperty.name} on a null receiver" }
        val receiverInstance = receiverInstanceAndPublicType.first
        val receiverPublicType = receiverInstanceAndPublicType.second
        return when (val property = propertyResolver.resolvePropertyRead(receiverPublicType, dataProperty.name)) {
            is RuntimePropertyResolver.ReadResolution.ResolvedRead -> property.getter.getValue(receiverInstance)
            RuntimePropertyResolver.ReadResolution.UnresolvedRead -> error("cannot get property ${dataProperty.name} from the receiver class $receiverPublicType")
        }
    }

    private
    fun getEnumConstant(objectOrigin: ObjectOrigin.EnumConstantOrigin): Enum<*>? {
        val typeName = objectOrigin.javaTypeName
        val classLoader = topLevelObject.javaClass.classLoader
        try {
            val enumClass = classLoader.loadClass(typeName) as Class<*>
            if (enumClass.isEnum) {
                @Suppress("UNCHECKED_CAST")
                return (enumClass as Class<Enum<*>>).enumConstants.find { it.name == objectOrigin.entryName }
            } else {
                error("$typeName is not an enum class")
            }
        } catch (e: ClassNotFoundException) {
            error("failed loading class $typeName: ${e.message}")
        }
    }

    private
    fun setPropertyValue(receiver: ObjectOrigin, dataProperty: DataProperty, value: Any?) {
        val receiverInstance = getObjectByResolvedOrigin(receiver).validate { "tried to access a property ${dataProperty.name} on a null receiver" }.first
        val receiverKClass = receiverInstance::class
        when (val property = propertyResolver.resolvePropertyWrite(receiverKClass, dataProperty.name)) {
            is RuntimePropertyResolver.WriteResolution.ResolvedWrite -> property.setter.setValue(receiverInstance, value)
            RuntimePropertyResolver.WriteResolution.UnresolvedWrite -> error("cannot set property ${dataProperty.name} in the receiver class $receiverKClass")
        }
    }
}

typealias InstanceAndPublicType = Pair<Any?, KClass<*>?>

val nullInstanceAndPublicType: InstanceAndPublicType = null to null

fun InstanceAndPublicType.validate(errorMessageOnInstanceNull: () -> String): Pair<Any, KClass<*>> {
    checkNotNull(first, errorMessageOnInstanceNull)
    checkNotNull(second) { "public type for ${first!!::class.qualifiedName} unknown" }
    return first!! to second!!
}

fun withFallbackPublicType(instance: Any): InstanceAndPublicType =
    instance to DslObject(instance).publicType.concreteClass.kotlin
