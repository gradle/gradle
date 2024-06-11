package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.internal.declarativedsl.language.DataTypeInternal
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
    is DataClass -> valueType is DataClass && (sameType(valueType, isAssignableTo) || isAssignableTo.name in valueType.supertypes)
    else -> sameType(valueType, isAssignableTo)
}


/**
 * Can't check for equality: TAPI proxies are not equal to the original implementations.
 * TODO: maybe "reify" the TAPI proxies to ensure equality?
 */
private
fun sameType(left: DataType, right: DataType) = when (left) {
    is DataClass -> right is DataClass && left.name.qualifiedName == right.name.qualifiedName
    is DataType.BooleanDataType -> right is DataType.BooleanDataType
    is DataType.IntDataType -> right is DataType.IntDataType
    is DataType.LongDataType -> right is DataType.LongDataType
    is DataType.StringDataType -> right is DataType.StringDataType
    is DataType.NullType -> right is DataType.NullType
    is DataType.UnitType -> right is DataType.UnitType
}


internal
fun TypeRefContext.getDataType(objectOrigin: ObjectOrigin): DataType = when (objectOrigin) {
    is ObjectOrigin.DelegatingObjectOrigin -> getDataType(objectOrigin.delegate)
    is ObjectOrigin.ConstantOrigin -> objectOrigin.literal.type
    is ObjectOrigin.External -> resolveRef(objectOrigin.key.objectType)
    is ObjectOrigin.NewObjectFromMemberFunction -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.NewObjectFromTopLevelFunction -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.PropertyReference -> resolveRef(objectOrigin.property.valueType)
    is ObjectOrigin.PropertyDefaultValue -> resolveRef(objectOrigin.property.valueType)
    is ObjectOrigin.TopLevelReceiver -> objectOrigin.type
    is ObjectOrigin.NullObjectOrigin -> DataTypeInternal.DefaultNullType
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
