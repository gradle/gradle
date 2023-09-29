package com.h0tk3y.kotlin.staticObjectNotation.evaluation

import com.h0tk3y.kotlin.staticObjectNotation.language.LanguageTreeElement
import com.h0tk3y.kotlin.staticObjectNotation.analysis.*

interface Evaluator {
    fun evaluate(dataObjectResolution: ObjectOrigin): ValueOrFailure
}

sealed interface DataValue {
    val type: DataType
    val originElement: LanguageTreeElement

    data class Constant<JvmType>(
        override val type: DataType.ConstantType<JvmType>,
        override val originElement: LanguageTreeElement,
        val value: JvmType,
    ) : DataValue

    data class DataClassObject<JvmType : Any>(
        override val type: DataType.DataClass<JvmType>,
        override val originElement: LanguageTreeElement,
        val constructorArgs: ParameterValueBinding
    ) : DataValue

    sealed interface ReferenceTarget<out JvmType> {
        val type: DataType

        data class Value<JvmType>(val value: DataValue) : ReferenceTarget<JvmType> {
            override val type: DataType
                get() = value.type
        }

        data class Property<JvmType>(
            val ofObject: DataClassObject<*>,
            val property: DataProperty
        ) : ReferenceTarget<JvmType> {
            override val type: DataType
                get() = TODO("Not yet implemented")
        }
    }

    data class Reference<JvmType>(
        val toValue: ReferenceTarget<JvmType>,
        override val originElement: LanguageTreeElement
    ) : DataValue {
        override val type: DataType
            get() = toValue.type

        data class CircularReferenceValueFailure(
            val referenceChain: List<Reference<*>>,
            override val originElement: LanguageTreeElement
        ) : ValueOrFailure.ValueFailure
    }
}