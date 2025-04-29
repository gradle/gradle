package org.gradle.internal.declarativedsl.mappingToJvm

import org.gradle.declarative.dsl.schema.DataBuilderFunction
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.ExternalObjectProviderKey
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.analysis.AssignmentMethod
import org.gradle.internal.declarativedsl.analysis.DeclarativeDslInterpretationException
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.OperationId
import org.gradle.internal.declarativedsl.objectGraph.ObjectReflection
import org.gradle.internal.declarativedsl.objectGraph.PropertyValueReflection
import kotlin.reflect.KClass


class DeclarativeReflectionToObjectConverter(
    private val externalObjectsMap: Map<ExternalObjectProviderKey, Any>,
    private val topLevelObject: Any,
    private val functionResolver: RuntimeFunctionResolver,
    private val propertyResolver: RuntimePropertyResolver,
    private val customAccessors: RuntimeCustomAccessors,
    private val getScopeClassLoader: () -> ClassLoader
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
                getObjectByResolvedOrigin(addedObject.objectOrigin)
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
            reflectionIdentityObjects[key] = instanceAndPublicType.instance
            reflectionIdentityPublicTypes[key] = instanceAndPublicType.publicType
            return instanceAndPublicType
        } else {
            return InstanceAndPublicType.of(reflectionIdentityObjects[key], reflectionIdentityPublicTypes[key])
        }
    }

    private
    fun applyPropertyValue(receiver: ObjectOrigin, property: DataProperty, assigned: PropertyValueReflection) {
        when (assigned.assignmentMethod) {
            is AssignmentMethod.Property, is AssignmentMethod.Augmentation -> setPropertyValue(receiver, property, getObjectByResolvedOrigin(assigned.value.objectOrigin).instance)
            is AssignmentMethod.BuilderFunction -> invokeBuilderFunction(receiver, assigned.assignmentMethod.function, assigned.value.objectOrigin)
            is AssignmentMethod.AsConstructed -> Unit // the value should have already been passed to the constructor or the factory function
        }
    }

    private
    fun getObjectByResolvedOrigin(objectOrigin: ObjectOrigin): InstanceAndPublicType {
        return when (objectOrigin) {
            is ObjectOrigin.DelegatingObjectOrigin -> getObjectByResolvedOrigin(objectOrigin.delegate)
            is ObjectOrigin.ConstantOrigin -> InstanceAndPublicType.of(objectOrigin.literal.value, objectOrigin.literal.type.constantType.kotlin)
            is ObjectOrigin.EnumConstantOrigin -> getEnumConstant(objectOrigin)
            is ObjectOrigin.External -> InstanceAndPublicType.unknownPublicType(
                externalObjectsMap[objectOrigin.key] ?: error("no external object provided for external object key of ${objectOrigin.key}")
            )

            is ObjectOrigin.NewObjectFromMemberFunction -> objectByIdentity(ObjectAccessKey.Identity(objectOrigin.invocationId)) { objectFromMemberFunction(objectOrigin) }
            is ObjectOrigin.NewObjectFromTopLevelFunction -> objectByIdentity(ObjectAccessKey.Identity(objectOrigin.invocationId)) { objectFromTopLevelFunction(objectOrigin) }
            is ObjectOrigin.NullObjectOrigin -> InstanceAndPublicType.NULL
            is ObjectOrigin.PropertyDefaultValue -> getPropertyValue(objectOrigin.receiver, objectOrigin.property)
            is ObjectOrigin.PropertyReference -> getPropertyValue(objectOrigin.receiver, objectOrigin.property)
            is ObjectOrigin.TopLevelReceiver -> InstanceAndPublicType.of(topLevelObject, topLevelObject::class)
            is ObjectOrigin.ConfiguringLambdaReceiver -> objectFromConfiguringLambda(objectOrigin)
            is ObjectOrigin.CustomConfigureAccessor -> objectFromCustomAccessor(objectOrigin)
            is ObjectOrigin.GroupedVarargValue -> {
                @Suppress("UNCHECKED_CAST")
                val resultArray = when (val elementClass = loadJvmTypeFor(objectOrigin.elementType)) {
                    Int::class.java -> IntArray(objectOrigin.elementValues.size) { getObjectByResolvedOrigin(objectOrigin.elementValues[it]).instance as Int }
                    Long::class.java -> LongArray(objectOrigin.elementValues.size) { getObjectByResolvedOrigin(objectOrigin.elementValues[it]).instance as Long }
                    Boolean::class.java -> BooleanArray(objectOrigin.elementValues.size) { getObjectByResolvedOrigin(objectOrigin.elementValues[it]).instance as Boolean }
                    else -> (java.lang.reflect.Array.newInstance(elementClass, objectOrigin.elementValues.size) as Array<in Any>).also {
                        (objectOrigin.elementValues.map { getObjectByResolvedOrigin(it).instance }.toTypedArray() as Array<out Any>).copyInto(it)
                    }
                }

                InstanceAndPublicType.of(
                    resultArray,
                    resultArray::class
                )
            }
        }
    }

    private fun loadJvmTypeFor(dataType: DataType): Class<*> = when (dataType) {
        is DataType.HasTypeName -> try {
            getScopeClassLoader().loadClass(dataType.javaTypeName)
        } catch (e: ClassNotFoundException) {
            throw DeclarativeDslInterpretationException("Failed to load the JVM class for $dataType (${e.message})")
                .also { it.addSuppressed(e) }
        }

        is DataType.BooleanDataType -> Boolean::class.java
        is DataType.IntDataType -> Int::class.java
        is DataType.LongDataType -> Long::class.java
        is DataType.StringDataType -> String::class.java
        is DataType.NullType -> Nothing::class.java
        is DataType.TypeVariableUsage -> Any::class.java
        is DataType.UnitType -> Unit::class.java
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
        return when (val runtimeFunction = functionResolver.resolve(receiverKClass, dataFun, getScopeClassLoader())) {
            is RuntimeFunctionResolver.Resolution.Resolved -> {
                val bindingWithValues = origin.parameterBindings.bindingMap.mapValues { getObjectByResolvedOrigin(it.value.objectOrigin).instance }
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
                    (value.objectOrigin as? ObjectOrigin.ConstantOrigin)?.literal?.value
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

        when (val runtimeFunction = functionResolver.resolve(receiverKClass, function, getScopeClassLoader())) {
            is RuntimeFunctionResolver.Resolution.Resolved -> {
                val binding = mapOf(function.dataParameter to getObjectByResolvedOrigin(valueOrigin).instance)
                runtimeFunction.function.callByWithErrorHandling(receiverInstance, binding, false).result
            }

            RuntimeFunctionResolver.Resolution.Unresolved -> error("could not resolve a member function $function call in the owner class $receiverKClass")
        }
    }

    private
    fun objectFromTopLevelFunction(
        origin: ObjectOrigin.NewObjectFromTopLevelFunction
    ): InstanceAndPublicType {
        return when (val runtimeFunction = functionResolver.resolve(Any::class, origin.function, getScopeClassLoader())) {
            is RuntimeFunctionResolver.Resolution.Resolved -> {
                val bindingWithValues = origin.parameterBindings.bindingMap.mapValues { getObjectByResolvedOrigin(it.value.objectOrigin).instance }
                runtimeFunction.function.callByWithErrorHandling(receiver = null, bindingWithValues, origin.parameterBindings.providesConfigureBlock)
                    .result
            }

            RuntimeFunctionResolver.Resolution.Unresolved -> error("could not resolve a top-level function ${origin.function}")
        }
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
    fun getEnumConstant(objectOrigin: ObjectOrigin.EnumConstantOrigin): InstanceAndPublicType {
        val enumClass = loadJvmTypeFor(objectOrigin.type)
        val enumConstant = if (enumClass.isEnum) {
            @Suppress("UNCHECKED_CAST")
            (enumClass as Class<Enum<*>>).enumConstants.find { it.name == objectOrigin.entryName }
        } else {
            error("$enumClass is not an enum class")
        }
        return InstanceAndPublicType.of(enumConstant, enumClass.kotlin)
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
