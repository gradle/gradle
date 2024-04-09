package org.gradle.internal.declarativedsl.analysis

import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.language.NullDataType
import org.gradle.internal.declarativedsl.language.UnitDataType
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
fun checkIsAssignable(valueType: DataType_, isAssignableTo: DataType_): Boolean = when (isAssignableTo) {
    is DataType_.ConstantType<*> -> valueType == isAssignableTo
    is DataClass -> valueType is DataClass && (isAssignableTo == valueType || isAssignableTo.name in valueType.supertypes)
    is DataType_.NullType -> false // TODO: proper null type support
    is DataType_.UnitType -> valueType == UnitDataType
    else -> error("Unhandled data type: ${isAssignableTo.javaClass.simpleName}")
}


internal
fun TypeRefContext.getDataType(objectOrigin: ObjectOrigin): DataType_ = when (objectOrigin) {
    is ObjectOrigin.DelegatingObjectOrigin -> getDataType(objectOrigin.delegate)
    is ObjectOrigin.ConstantOrigin -> objectOrigin.literal.type
    is ObjectOrigin.External -> resolveRef(objectOrigin.key.type)
    is ObjectOrigin.NewObjectFromMemberFunction -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.NewObjectFromTopLevelFunction -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.PropertyReference -> resolveRef(objectOrigin.property.type)
    is ObjectOrigin.PropertyDefaultValue -> resolveRef(objectOrigin.property.type)
    is ObjectOrigin.TopLevelReceiver -> objectOrigin.type
    is ObjectOrigin.NullObjectOrigin -> NullDataType
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
