package org.gradle.internal.declarativedsl.mappingToJvm

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
                getObjectByResolvedOrigin(addedObject.objectOrigin)
                apply(addedObject, conversionFilter)
                // TODO: maybe add the "containers" to the schema, so that added objects can be better expressed in this interpretation step
            }

            objectReflection.customAccessorObjects.forEach { customAccessor ->
                getObjectByResolvedOrigin(customAccessor.objectOrigin) ?: error("could not get object by custom accessor ${customAccessor.objectOrigin}")
                apply(customAccessor, conversionFilter)
            }

            objectReflection.lambdaAccessedObjects.forEach { lambdaAccessedObject ->
                getObjectByResolvedOrigin(lambdaAccessedObject.objectOrigin) ?: error("could not get object from lambda passed to ${lambdaAccessedObject.objectOrigin}")
                apply(lambdaAccessedObject, conversionFilter)
            }
        }
    }

    private
    val reflectionIdentityObjects = mutableMapOf<ObjectAccessKey, Any?>()

    private
    fun objectByIdentity(key: ObjectAccessKey, newObject: () -> Any?): Any? {
        // This code does not use `computeIfAbsent` because `newObject` can make reentrant calls, leading to CME.
        // Also, it does not check for the value being null, because null values are potentially allowed
        if (key !in reflectionIdentityObjects) {
            val newInstance = newObject()
            reflectionIdentityObjects[key] = newInstance
            return newInstance
        } else {
            return reflectionIdentityObjects[key]
        }
    }

    private
    fun applyPropertyValue(receiver: ObjectOrigin, property: DataProperty, assigned: PropertyValueReflection) {
        when (assigned.assignmentMethod) {
            is AssignmentMethod.Property -> setPropertyValue(receiver, property, getObjectByResolvedOrigin(assigned.value.objectOrigin))
            is AssignmentMethod.BuilderFunction -> invokeBuilderFunction(receiver, assigned.assignmentMethod.function, assigned.value.objectOrigin)
            is AssignmentMethod.AsConstructed -> Unit // the value should have already been passed to the constructor or the factory function
        }
    }

    private
    fun getObjectByResolvedOrigin(objectOrigin: ObjectOrigin): Any? {
        return when (objectOrigin) {
            is ObjectOrigin.DelegatingObjectOrigin -> getObjectByResolvedOrigin(objectOrigin.delegate)
            is ObjectOrigin.ConstantOrigin -> objectOrigin.literal.value
            is ObjectOrigin.EnumConstantOrigin -> getEnumConstant(objectOrigin)
            is ObjectOrigin.External -> externalObjectsMap[objectOrigin.key] ?: error("no external object provided for external object key of ${objectOrigin.key}")
            is ObjectOrigin.NewObjectFromMemberFunction -> objectByIdentity(ObjectAccessKey.Identity(objectOrigin.invocationId)) { objectFromMemberFunction(objectOrigin) }
            is ObjectOrigin.NewObjectFromTopLevelFunction -> objectByIdentity(ObjectAccessKey.Identity(objectOrigin.invocationId)) { objectFromTopLevelFunction(/*objectOrigin*/) }
            is ObjectOrigin.NullObjectOrigin -> null
            is ObjectOrigin.PropertyDefaultValue -> getPropertyValue(objectOrigin.receiver, objectOrigin.property)
            is ObjectOrigin.PropertyReference -> getPropertyValue(objectOrigin.receiver, objectOrigin.property)
            is ObjectOrigin.TopLevelReceiver -> topLevelObject
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
    ): Any? {
        val dataFun = origin.function
        val receiverInstance = getObjectByResolvedOrigin(origin.receiver)
            ?: error("tried to invoke a function $dataFun on a null receiver ${origin.receiver}")

        val callResult = invokeFunctionAndGetResult(receiverInstance, origin)
        return callResult.result
    }

    private
    fun invokeFunctionAndGetResult(
        receiverInstance: Any,
        origin: ObjectOrigin.FunctionInvocationOrigin
    ): DeclarativeRuntimeFunction.InvocationResult {
        val dataFun = origin.function
        val receiverKClass = receiverInstance::class
        return when (val runtimeFunction = functionResolver.resolve(receiverKClass, dataFun)) {
            is RuntimeFunctionResolver.Resolution.Resolved -> {
                val bindingWithValues = origin.parameterBindings.bindingMap.mapValues { getObjectByResolvedOrigin(it.value) }
                runtimeFunction.function.callByWithErrorHandling(receiverInstance, bindingWithValues, origin.parameterBindings.providesConfigureBlock)
            }

            RuntimeFunctionResolver.Resolution.Unresolved -> error("could not resolve a member function $dataFun call in the owner class $receiverKClass")
        }
    }

    private
    fun objectFromConfiguringLambda(
        origin: ObjectOrigin.ConfiguringLambdaReceiver
    ): Any? = objectByIdentity(
        ObjectAccessKey.ConfiguringLambda(
            origin.receiver,
            origin.function,
            identityValues = origin.parameterBindings.bindingMap.map {(parameter, value) ->
                if (parameter.semantics is ParameterSemantics.IdentityKey) {
                    (value as? ObjectOrigin.ConstantOrigin)?.literal?.value
                } else null
            })
    ) {
        val function = origin.function
        val receiverInstance = getObjectByResolvedOrigin(origin.receiver)
            ?: error("tried to invoke a function $function on a null receiver ${origin.receiver}")

        invokeFunctionAndGetResult(receiverInstance, origin).capturedValue
    }

    private
    fun objectFromCustomAccessor(
        origin: ObjectOrigin.CustomConfigureAccessor
    ): Any? = objectByIdentity(ObjectAccessKey.CustomAccessor(origin.receiver, origin.accessor.customAccessorIdentifier)) {
        customAccessors.getObjectFromCustomAccessor(
            getObjectByResolvedOrigin(origin.receiver) ?: error("receiver for custom accessor is null"), origin.accessor
        )
    }

    private
    fun invokeBuilderFunction(receiverOrigin: ObjectOrigin, function: DataBuilderFunction, valueOrigin: ObjectOrigin) {
        val receiverInstance = getObjectByResolvedOrigin(receiverOrigin)
            ?: error("tried to invoke a function $function on a null receiver $receiverOrigin")
        val receiverKClass = receiverInstance::class
        val parameterBinding = ParameterValueBinding(mapOf(function.dataParameter to valueOrigin), false)

        when (val runtimeFunction = functionResolver.resolve(receiverKClass, function)) {
            is RuntimeFunctionResolver.Resolution.Resolved ->
                runtimeFunction.function.callByWithErrorHandling(receiverInstance, parameterBinding.bindingMap.mapValues { getObjectByResolvedOrigin(it.value) }, parameterBinding.providesConfigureBlock).result
            RuntimeFunctionResolver.Resolution.Unresolved -> error("could not resolve a member function $function call in the owner class $receiverKClass")
        }
    }

    private
    fun objectFromTopLevelFunction(
        // origin: ObjectOrigin.NewObjectFromTopLevelFunction
    ): Any? {
        TODO("support calls to top-level functions: they need to carry the owner class information to get resolved")
    }

    private
    fun getPropertyValue(receiver: ObjectOrigin, dataProperty: DataProperty): Any? {
        val receiverInstance = getObjectByResolvedOrigin(receiver)
            ?: error("tried to access a property ${dataProperty.name} on a null receiver")
        val receiverKClass = receiverInstance::class
        return when (val property = propertyResolver.resolvePropertyRead(receiverKClass, dataProperty.name)) {
            is RuntimePropertyResolver.ReadResolution.ResolvedRead -> property.getter.getValue(receiverInstance)
            RuntimePropertyResolver.ReadResolution.UnresolvedRead -> error("cannot get property ${dataProperty.name} from the receiver class $receiverKClass")
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
        val receiverInstance = getObjectByResolvedOrigin(receiver)
            ?: error("tried to access a property ${dataProperty.name} on a null receiver")
        val receiverKClass = receiverInstance::class
        when (val property = propertyResolver.resolvePropertyWrite(receiverKClass, dataProperty.name)) {
            is RuntimePropertyResolver.WriteResolution.ResolvedWrite -> property.setter.setValue(receiverInstance, value)
            RuntimePropertyResolver.WriteResolution.UnresolvedWrite -> error("cannot set property ${dataProperty.name} in the receiver class $receiverKClass")
        }
    }
}
