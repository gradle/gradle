/*
 * Copyright 2024 the original author or authors.
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


interface DataType {
    sealed interface ConstantType<JvmType> : DataType
    @Serializable
    @SerialName("int")
    data object IntDataType : ConstantType<Int> {
        override fun toString(): String = "Int"
    }
    @Serializable
    @SerialName("long")
    data object LongDataType : ConstantType<Long> {
        override fun toString(): String = "Long"
    }
    @Serializable
    @SerialName("string")
    data object StringDataType : ConstantType<String> {
        override fun toString(): String = "String"
    }
    @Serializable
    @SerialName("boolean")
    data object BooleanDataType : ConstantType<Boolean> {
        override fun toString(): String = "Boolean"
    }

    // TODO: implement nulls?
    @Serializable
    @SerialName("null")
    data object NullType : DataType

    @Serializable
    @SerialName("unit")
    data object UnitType : DataType

    // TODO: `Any` type?
    // TODO: Support subtyping of some sort in the schema rather than via reflection?
}
