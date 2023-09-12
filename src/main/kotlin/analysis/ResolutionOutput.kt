package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.AccessChain
import com.h0tk3y.kotlin.staticObjectNotation.FunctionCall
import com.h0tk3y.kotlin.staticObjectNotation.LanguageTreeElement
import com.h0tk3y.kotlin.staticObjectNotation.LocalValue
import com.h0tk3y.kotlin.staticObjectNotation.evaluation.DataValue

// TODO: report failures to resolve with potential candidates that could not work
data class PropertyReferenceResolution(
    val receiverObject: ObjectOrigin,
    val property: DataProperty
) {
    override fun toString(): String = "$receiverObject${'.'}${property.name}"
}

sealed interface ObjectOrigin {
    val originElement: LanguageTreeElement

    data class TopLevelReceiver(val type: DataType, override val originElement: LanguageTreeElement) : ObjectOrigin {
        override fun toString(): String = "(top-level-object)"
    }

    data class FromLocalValue(val localValue: LocalValue, val assigned: ObjectOrigin) : ObjectOrigin {
        override val originElement: LanguageTreeElement
            get() = assigned.originElement

        override fun toString(): String = "(val ${localValue.name} = $assigned)"
    }

    data class ConstantOrigin(val constant: DataValue.Constant<*>) : ObjectOrigin {
        override val originElement: LanguageTreeElement
            get() = constant.originElement

        override fun toString(): String = "${constant.value.let { if (it is String) "\"$it\"" else it }}"
    }

    data class FromFunctionInvocation(
        val function: SchemaFunction,
        val receiverObject: ObjectOrigin?,
        val parameterBindings: ParameterValueBinding,
        override val originElement: FunctionCall,
        val invocationId: Long
    ) : ObjectOrigin {
        override fun toString(): String =
            receiverObject?.toString()?.plus(".").orEmpty() +
                    "${function.simpleName}#$invocationId(${parameterBindings.bindingMap.entries.joinToString { (k, v) -> "${k.name} = $v" }})"
    }

    data class PropertyReference(
        val receiver: ObjectOrigin,
        val property: DataProperty,
        override val originElement: AccessChain
    ) : ObjectOrigin {
        override fun toString(): String = "$receiver${'.'}${property.name}"
    }

    data class External(val key: ExternalObjectProviderKey, override val originElement: AccessChain) : ObjectOrigin {
        override fun toString(): String = "${key.type}"
    }
}

data class ParameterValueBinding(val bindingMap: Map<DataParameter, ObjectOrigin>)


