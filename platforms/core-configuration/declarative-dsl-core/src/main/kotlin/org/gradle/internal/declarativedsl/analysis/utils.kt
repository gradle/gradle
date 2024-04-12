package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataType
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
fun checkIsAssignable(valueType: DataType, isAssignableTo: DataType): Boolean = when {
    isAssignableTo is DataClass -> valueType is DataClass && (isAssignableTo == valueType || isAssignableTo.name in valueType.supertypes)
    isAssignableTo.isNull -> false // TODO: proper null type support
    isAssignableTo.isUnit -> valueType == UnitDataType
    else -> valueType == isAssignableTo
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
