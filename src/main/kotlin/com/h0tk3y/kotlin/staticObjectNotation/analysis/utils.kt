package com.h0tk3y.kotlin.staticObjectNotation.analysis

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.full.isSubclassOf

@OptIn(ExperimentalContracts::class)
internal inline fun AnalysisContext.withScope(scope: AnalysisScope, action: () -> Unit) {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    enterScope(scope)
    try {
        action()
    } finally {
        leaveScope(scope)
    }
}

internal fun checkIsAssignable(valueType: DataType, isAssignableTo: DataType): Boolean = when (isAssignableTo) {
    is DataType.ConstantType<*> -> valueType == isAssignableTo
    is DataType.DataClass<*> -> valueType is DataType.DataClass<*> && valueType.kClass.isSubclassOf(isAssignableTo.kClass)
    DataType.NullType -> false // TODO: proper null type support
    DataType.UnitType -> valueType == DataType.UnitType
}

fun TypeRefContext.getDataType(objectOrigin: ObjectOrigin): DataType = when (objectOrigin) {
    is ObjectOrigin.ConstantOrigin -> objectOrigin.literal.type
    is ObjectOrigin.External -> resolveRef(objectOrigin.key.type)
    is ObjectOrigin.NewObjectFromFunctionInvocation -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.PropertyReference -> resolveRef(objectOrigin.property.type)
    is ObjectOrigin.PropertyDefaultValue -> resolveRef(objectOrigin.property.type)
    is ObjectOrigin.TopLevelReceiver -> objectOrigin.type
    is ObjectOrigin.FromLocalValue -> getDataType(objectOrigin.assigned)
    is ObjectOrigin.NullObjectOrigin -> DataType.NullType
    is ObjectOrigin.ConfigureReceiver -> resolveRef(objectOrigin.accessor.objectType)
    is ObjectOrigin.BuilderReturnedReceiver -> getDataType(objectOrigin.receiverObject)
}
