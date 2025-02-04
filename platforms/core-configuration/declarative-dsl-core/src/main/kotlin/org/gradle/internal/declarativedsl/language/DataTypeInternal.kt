/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.declarativedsl.language

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.FqName

object DataTypeInternal {

    @Serializable
    @SerialName("int")
    data object DefaultIntDataType : DataType.IntDataType {
        override val constantType: Class<Int> = Int::class.java
        override fun toString(): String = "Int"

        @Suppress("unused")
        private
        fun readResolve(): Any = DefaultIntDataType
    }

    @Serializable
    @SerialName("long")
    data object DefaultLongDataType : DataType.LongDataType {
        override val constantType: Class<Long> = Long::class.java
        override fun toString(): String = "Long"

        @Suppress("unused")
        private
        fun readResolve(): Any = DefaultLongDataType
    }

    @Serializable
    @SerialName("string")
    data object DefaultStringDataType : DataType.StringDataType {
        override val constantType: Class<String> = String::class.java
        override fun toString(): String = "String"

        @Suppress("unused")
        private
        fun readResolve(): Any = DefaultStringDataType
    }

    @Serializable
    @SerialName("boolean")
    data object DefaultBooleanDataType : DataType.BooleanDataType {
        override val constantType: Class<Boolean> = Boolean::class.java
        override fun toString(): String = "Boolean"

        @Suppress("unused")
        private
        fun readResolve(): Any = DefaultBooleanDataType
    }

    @Serializable
    @SerialName("null")
    data object DefaultNullType : DataType.NullType {
        override fun toString(): String = "Null"

        @Suppress("unused")
        private
        fun readResolve(): Any = DefaultNullType
    }

    @Serializable
    @SerialName("unit")
    data object DefaultUnitType : DataType.UnitType {
        override fun toString(): String = "Unit"

        @Suppress("unused")
        private
        fun readResolve(): Any = DefaultUnitType
    }

    @Serializable
    @SerialName("parameterizedOpaqueTypeSignature")
    data class DefaultParameterizedTypeSignature(
        override val name: FqName,
        override val typeParameters: List<TypeParameter>,
        override val javaTypeName: String
    ) : DataType.ParameterizedTypeSignature {
        @Serializable
        @SerialName("typeParameter")
        data class TypeParameter(override val name: String, override val isOutVariant: Boolean) : DataType.ParameterizedTypeSignature.TypeParameter

        init {
            check(typeParameters.isNotEmpty()) { "A parameterized opaque type must have at least one type parameter" }
        }
    }

    @Serializable
    @SerialName("parameterizedTypeInstance")
    data class DefaultParameterizedTypeInstance(
        override val typeSignature: DataType.ParameterizedTypeSignature,
        override val typeArguments: List<DataType.ParameterizedTypeInstance.TypeArgument>
    ) : DataType.ParameterizedTypeInstance {
        init {
            check(typeArguments.size == typeSignature.typeParameters.size) { "Mismatching type arguments and type parameter counts" }
        }

        override fun toString() = "${typeSignature.name.simpleName}<${typeArguments.joinToString()}>"
    }

    @Serializable
    @SerialName("typeVariableUsage")
    data class DefaultTypeVariableUsage(
        override val variableId: Long,
    ) : DataType.TypeVariableUsage

// TODO: `Any` type?
// TODO: Support subtyping of some sort in the schema rather than via reflection?
}
