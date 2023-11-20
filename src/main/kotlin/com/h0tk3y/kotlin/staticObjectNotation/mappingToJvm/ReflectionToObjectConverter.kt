package com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm

import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataProperty
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataType
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataTypeRef
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ExternalObjectProviderKey
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ObjectOrigin
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ParameterValueBinding
import com.h0tk3y.kotlin.staticObjectNotation.analysis.TypeRefContext
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ObjectReflection
import com.h0tk3y.kotlin.staticObjectNotation.types.isConfigureLambda
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

class RestrictedReflectionToObjectConverter(
    private val externalObjectsMap: Map<ExternalObjectProviderKey, Any>,
    private val typeRefContext: TypeRefContext,
    private val topLevelObject: Any
) {
    fun apply(objectReflection: ObjectReflection) {
        if (objectReflection is ObjectReflection.DataObjectReflection) {
            objectReflection.properties.forEach { (property, value) ->
                val valueObject = getObjectByResolvedOrigin(value.objectOrigin)
                if (!property.isReadOnly) {
                    setPropertyValue(objectReflection.objectOrigin, property, valueObject)
                }

                if (valueObject != null) {
                    apply(value)
                }
                // TODO: record properties assigned in function calls or constructors, so that
                //       we can check that all properties were assigned
            }

            objectReflection.addedObjects.forEach { objectReflection ->
                // We need the side effect of invoking the function producing the object, if it was there
                getObjectByResolvedOrigin(objectReflection.objectOrigin)
                // TODO: maybe add the "containers" to the schema, so that added objects can be better expressed in this interpretation step
            }
        }
    }

    private val reflectionIdentityObjects = mutableMapOf<Long, Any?>()

    private fun objectByIdentity(identity: Long, newObjectCreation: () -> Any?): Any? {
        return reflectionIdentityObjects.computeIfAbsent(identity) { newObjectCreation() }
    }

    private fun getObjectByResolvedOrigin(objectOrigin: ObjectOrigin): Any? {
        return when (objectOrigin) {
            is ObjectOrigin.ConstantOrigin -> objectOrigin.literal.value
            is ObjectOrigin.External -> {
                externalObjectsMap[objectOrigin.key] ?: error("No external object provided for external object key of ${objectOrigin.key}")
            }

            is ObjectOrigin.ConfigureReceiver -> getObjectByResolvedOrigin(objectOrigin.receiver)
            is ObjectOrigin.BuilderReturnedReceiver -> getObjectByResolvedOrigin(objectOrigin.receiver)
            is ObjectOrigin.NewObjectFromMemberFunction -> objectByIdentity(objectOrigin.invocationId) {
                objectFromMemberFunction(objectOrigin)
            }

            is ObjectOrigin.NewObjectFromTopLevelFunction -> objectByIdentity(objectOrigin.invocationId) {
                objectFromTopLevelFunction(objectOrigin)
            }

            is ObjectOrigin.NullObjectOrigin -> null
            is ObjectOrigin.PropertyDefaultValue -> getPropertyValue(objectOrigin.receiver, objectOrigin.property)
            is ObjectOrigin.PropertyReference -> getPropertyValue(objectOrigin.receiver, objectOrigin.property)
            is ObjectOrigin.TopLevelReceiver -> topLevelObject
            is ObjectOrigin.FromLocalValue -> getObjectByResolvedOrigin(objectOrigin.assigned)
        }
    }

    private fun objectFromMemberFunction(
        origin: ObjectOrigin.NewObjectFromMemberFunction
    ): Any? {
        val dataFun = origin.function
        val ownerKClass = (typeRefContext.resolveRef(dataFun.receiver) as? DataType.DataClass<*>)?.kClass
            ?: error("Member functions on non-class type are not supported")
        val receiver = getObjectByResolvedOrigin(origin.receiver)
        check(receiver != null) { "member function $dataFun called on a null receiver" }

        for (kFun in ownerKClass.memberFunctions) {
            if (kFun.name == dataFun.simpleName) {
                val params = bindDataParametersToFunctionParameters(receiver, kFun, origin.parameterBindings)
                if (params != null) {
                    return kFun.callBy(params)
                }
            }
        }
        error("could not resolve a member function $dataFun call in the owner class $ownerKClass")
    }

    private fun objectFromTopLevelFunction(
        origin: ObjectOrigin.NewObjectFromTopLevelFunction
    ): Any? {
        TODO("support calls to top-level functions: they need to carry the owner class information to get resolved")
    }

    private fun getPropertyValue(receiver: ObjectOrigin, dataProperty: DataProperty): Any? {
        val receiverInstance = getObjectByResolvedOrigin(receiver)
            ?: error("tried to access a property ${dataProperty.name} on a null receiver")
        val receiverKClass = receiverInstance::class
        val callable = receiverKClass.memberProperties.find { it.name == dataProperty.name  && it.visibility == KVisibility.PUBLIC }
            ?: receiverKClass.memberFunctions.find { it.name == getterName(dataProperty) && it.parameters.size == 1 && it.visibility == KVisibility.PUBLIC }
            ?: error("cannot get property ${dataProperty.name} from the receiver class $receiverKClass")
        return callable.call(receiverInstance)
    }

    private fun setPropertyValue(receiver: ObjectOrigin, dataProperty: DataProperty, value: Any?) {
        val receiverInstance = getObjectByResolvedOrigin(receiver)
            ?: error("tried to access a property ${dataProperty.name} on a null receiver")
        val receiverKClass = receiverInstance::class
        val setter = (receiverKClass.memberProperties.find { it.name == dataProperty.name && it.visibility == KVisibility.PUBLIC } as? KMutableProperty<*>)?.setter
            ?: receiverKClass.memberFunctions.find { it.name == setterName(dataProperty) && it.visibility == KVisibility.PUBLIC }
            ?: error("cannot set property ${dataProperty.name} in the receiver class $receiverKClass")
        setter.call(receiverInstance, value)
    }

    private fun getterName(dataProperty: DataProperty) = "get" + capitalize(dataProperty)

    private fun setterName(dataProperty: DataProperty) = "set" + capitalize(dataProperty)

    private fun capitalize(dataProperty: DataProperty) = dataProperty.name.replaceFirstChar {
        if (it.isLowerCase()) it.uppercaseChar() else it
    }

    private fun bindDataParametersToFunctionParameters(
        receiver: Any?,
        kFunction: KFunction<*>,
        parameterValueBinding: ParameterValueBinding
    ): Map<KParameter, Any?>? {

        val namedValues = parameterValueBinding.bindingMap.mapKeys { (param, _) -> param.name }
        val used = mutableSetOf<String>()
        val binding = buildMap<KParameter, Any?> {
            kFunction.parameters.forEach { param ->
                val paramName = param.name
                when {
                    param == kFunction.instanceParameter -> put(param, receiver)
                    isConfigureLambda(param) -> put(param, universalConfigureLambda)
                    paramName != null && paramName in namedValues -> {
                        put(param, getObjectByResolvedOrigin(namedValues.getValue(paramName)))
                        used += paramName
                    }

                    param.isOptional -> Unit
                    else -> return null
                }
            }
        }
        return if (used.size == namedValues.size) binding else null
    }

    private val universalConfigureLambda: (Nothing) -> Unit = { }

    private fun DataTypeRef.toKClass() = when (val type = typeRefContext.resolveRef(this)) {
        DataType.BooleanDataType -> Boolean::class
        DataType.IntDataType -> Int::class
        DataType.LongDataType -> Long::class
        DataType.StringDataType -> String::class
        is DataType.DataClass<*> -> type.kClass
        DataType.NullType -> Nothing::class
        DataType.UnitType -> Unit::class
    }
}
