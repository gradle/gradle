package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument.StarProjection
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeSignature
import org.gradle.declarative.dsl.schema.DataType.TypeVariableUsage
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.language.DataTypeInternal
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

internal fun TypeRefContext.computeGenericTypeSubstitution(expectedType: DataTypeRef?, actualType: DataTypeRef): Map<TypeVariableUsage, DataType>? {
    val expected = resolveRef(expectedType ?: return emptyMap())
    val actual = resolveRef(actualType)

    var hasConflict = false // TODO: improve type mismatch reporting
    val result = buildMap {
        fun recordEquivalence(typeVariableUsage: TypeVariableUsage, otherType: DataType) {
            val mapping = getOrPut(typeVariableUsage) { otherType }
            if (mapping != otherType) {
                hasConflict = true
            }
        }

        fun visitTypePair(left: DataType, right: DataType) {
            when {
                left is TypeVariableUsage -> recordEquivalence(left, right)
                right is TypeVariableUsage -> recordEquivalence(right, left)

                left is ParameterizedTypeInstance || right is ParameterizedTypeInstance -> {
                    if (left is ParameterizedTypeInstance && right is ParameterizedTypeInstance && left.typeArguments.size == right.typeArguments.size) {
                        left.typeArguments.zip(right.typeArguments).forEach { (leftArg, rightArg) ->
                            when (leftArg) {
                                is ConcreteTypeArgument -> {
                                    if (rightArg is ConcreteTypeArgument) {
                                        visitTypePair(resolveRef(leftArg.type), resolveRef(rightArg.type))
                                    } else {
                                        hasConflict = true
                                    }
                                }

                                is StarProjection -> Unit
                            }
                        }
                    } else {
                        hasConflict = true
                    }
                }
            }
        }

        visitTypePair(expected, actual)
    }

    return result.takeIf { !hasConflict }
}

internal fun TypeRefContext.checkIsAssignable(valueType: DataType, isAssignableTo: DataType, typeSubstitution: Map<TypeVariableUsage, DataType>): Boolean {
    val substitutedValueType = applyTypeSubstitution(valueType, typeSubstitution)
    val substitutedIsAssignableTo = applyTypeSubstitution(isAssignableTo, typeSubstitution)

    return checkIsAssignableWithoutSubstitution(substitutedValueType, substitutedIsAssignableTo)
}

private fun TypeRefContext.checkIsAssignableWithoutSubstitution(valueType: DataType, isAssignableTo: DataType): Boolean = when (isAssignableTo) {
    is DataClass -> valueType is DataClass && (sameType(valueType, isAssignableTo) || isAssignableTo.name in valueType.supertypes)
    is ParameterizedTypeInstance -> valueType is ParameterizedTypeInstance && run {
        sameTypeSignature(isAssignableTo.typeSignature, valueType.typeSignature) &&
            isAssignableTo.typeArguments.indices.all { index ->
                val isAssignableToParameter = isAssignableTo.typeSignature.typeParameters[index]
                val isAssignableToArgument = isAssignableTo.typeArguments[index]
                val valueArgument = valueType.typeArguments[index]

                when (isAssignableToArgument) {
                    is StarProjection -> true
                    is ConcreteTypeArgument -> valueArgument is ConcreteTypeArgument &&
                        if (isAssignableToParameter.isOutVariant)
                            checkIsAssignable(
                                resolveRef(valueArgument.type),
                                resolveRef(isAssignableToArgument.type),
                                emptyMap()
                            )
                        else sameType(resolveRef(valueArgument.type), resolveRef(isAssignableToArgument.type))
                }
            }
    }

    else -> sameType(valueType, isAssignableTo)
}

internal fun TypeRefContext.applyTypeSubstitution(type: DataType, substitution: Map<TypeVariableUsage, DataType>): DataType {
    fun substituteInTypeArgument(typeArgument: ParameterizedTypeInstance.TypeArgument) = when (typeArgument) {
        is ConcreteTypeArgument -> TypeArgumentInternal.DefaultConcreteTypeArgument(applyTypeSubstitution(resolveRef(typeArgument.type), substitution).ref)
        is StarProjection -> typeArgument
    }

    return when (type) {
        is ParameterizedTypeInstance -> DataTypeInternal.DefaultParameterizedTypeInstance(type.typeSignature, type.typeArguments.map(::substituteInTypeArgument))
        is TypeVariableUsage -> substitution[type] ?: type

        is DataClass,
        is EnumClass,
        is DataType.ConstantType<*>,
        is DataType.NullType,
        is DataType.UnitType -> type
    }
}

private fun sameTypeSignature(left: ParameterizedTypeSignature, right: ParameterizedTypeSignature) =
    left.name.qualifiedName == right.name.qualifiedName

/**
 * Can't check for equality: TAPI proxies are not equal to the original implementations.
 * TODO: maybe "reify" the TAPI proxies to ensure equality?
 */
internal
fun TypeRefContext.sameType(left: DataType, right: DataType): Boolean = when (left) {
    is ParameterizedTypeInstance -> right is ParameterizedTypeInstance &&
        with(left.typeArguments.zip(right.typeArguments)) {
            size == left.typeArguments.size &&
                all { (leftArg, rightArg) ->
                    when (leftArg) {
                        is StarProjection -> rightArg is StarProjection
                        is ConcreteTypeArgument -> rightArg is ConcreteTypeArgument && sameType(resolveRef(leftArg.type), resolveRef(rightArg.type))
                    }
                }
        }

    is DataType.ClassDataType -> right is DataType.ClassDataType && left.name.qualifiedName == right.name.qualifiedName
    is DataType.BooleanDataType -> right is DataType.BooleanDataType
    is DataType.IntDataType -> right is DataType.IntDataType
    is DataType.LongDataType -> right is DataType.LongDataType
    is DataType.StringDataType -> right is DataType.StringDataType
    is DataType.NullType -> right is DataType.NullType
    is DataType.UnitType -> right is DataType.UnitType
    is TypeVariableUsage -> right is TypeVariableUsage && left.variableId == right.variableId
}

internal
fun TypeRefContext.simplyTyped(objectOrigin: ObjectOrigin): TypedOrigin = TypedOrigin(objectOrigin, getDataType(objectOrigin))

internal
fun TypeRefContext.getDataType(objectOrigin: ObjectOrigin): DataType = when (objectOrigin) {
    is ObjectOrigin.DelegatingObjectOrigin -> getDataType(objectOrigin.delegate)
    is ObjectOrigin.ConstantOrigin -> objectOrigin.literal.type
    is ObjectOrigin.EnumConstantOrigin -> objectOrigin.type
    is ObjectOrigin.External -> resolveRef(objectOrigin.key.objectType)
    is ObjectOrigin.NewObjectFromMemberFunction -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.NewObjectFromTopLevelFunction -> resolveRef(objectOrigin.function.returnValueType)
    is ObjectOrigin.PropertyReference -> resolveRef(objectOrigin.property.valueType)
    is ObjectOrigin.PropertyDefaultValue -> resolveRef(objectOrigin.property.valueType)
    is ObjectOrigin.TopLevelReceiver -> objectOrigin.type
    is ObjectOrigin.NullObjectOrigin -> DataTypeInternal.DefaultNullType
    is ObjectOrigin.CustomConfigureAccessor -> resolveRef(objectOrigin.accessedType)
    is ObjectOrigin.ConfiguringLambdaReceiver -> resolveRef(objectOrigin.lambdaReceiverType)
    is ObjectOrigin.GroupedVarargValue -> objectOrigin.elementType
    is ObjectOrigin.AugmentationOrigin -> resolveRef(objectOrigin.augmentedProperty.property.valueType)
}


internal
fun SchemaFunction.format(receiver: ObjectOrigin?, lowercase: Boolean = true): String {
    val text = when (receiver) {
        null -> "top level function ${this.simpleName}"
        else -> "function ${this.simpleName} (having as receiver $receiver)"
    }
    return when {
        !lowercase -> text.replaceFirstChar { it.uppercase() }
        else -> text
    }
}
