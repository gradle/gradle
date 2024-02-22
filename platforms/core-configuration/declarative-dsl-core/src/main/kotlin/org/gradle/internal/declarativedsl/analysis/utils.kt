package org.gradle.internal.declarativedsl.analysis

import org.gradle.internal.declarativedsl.language.DataType
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


@OptIn(ExperimentalContracts::class)
internal
inline fun AnalysisContext.withScope(scope: AnalysisScope, action: () -> Unit) {
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


internal
fun checkIsAssignable(valueType: DataType, isAssignableTo: DataType): Boolean = when (isAssignableTo) {
    is DataType.ConstantType<*> -> valueType == isAssignableTo
    is DataClass -> valueType is DataClass && (isAssignableTo == valueType || isAssignableTo.name in valueType.supertypes)
    DataType.NullType -> false // TODO: proper null type support
    DataType.UnitType -> valueType == DataType.UnitType
    else -> error("Unhandled data type: ${isAssignableTo.javaClass.simpleName}")
}


internal
fun TypeRefContext.getDataType(objectOrigin: ObjectOrigin): DataType = when (objectOrigin) {
    is ObjectOrigin.DelegatingObjectOrigin -> getDataType(objectOrigin.delegate)
    is ObjectOrigin.ConstantOrigin -> objectOrigin.literal.type
    is ObjectOrigin.External -> resolveRef(objectOrigin.key.type)
    is ObjectOrigin.NewObjectFromMemberFunction -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.NewObjectFromTopLevelFunction -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.PropertyReference -> resolveRef(objectOrigin.property.type)
    is ObjectOrigin.PropertyDefaultValue -> resolveRef(objectOrigin.property.type)
    is ObjectOrigin.TopLevelReceiver -> objectOrigin.type
    is ObjectOrigin.NullObjectOrigin -> DataType.NullType
    is ObjectOrigin.CustomConfigureAccessor -> resolveRef(objectOrigin.accessedType)
    is ObjectOrigin.ConfiguringLambdaReceiver -> resolveRef(objectOrigin.lambdaReceiverType)
}


internal
fun AnalysisContext.checkAccessOnCurrentReceiver(
    receiver: ObjectOrigin,
    access: LanguageTreeElement
) {
    if (receiver !is ObjectOrigin.ImplicitThisReceiver || !receiver.isCurrentScopeReceiver) {
        errorCollector.collect(ResolutionError(access, ErrorReason.AccessOnCurrentReceiverOnlyViolation))
    }
}
