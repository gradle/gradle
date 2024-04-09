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
import org.gradle.internal.declarativedsl.analysis.DataType


@Serializable
@SerialName("int")
data object IntDataType : DataType.ConstantType<Int> {
    override fun toString(): String = "Int"
    override fun getType(): Class<Int> = Int::class.java
}


@Serializable
@SerialName("long")
data object LongDataType : DataType.ConstantType<Long> {
    override fun toString(): String = "Long"

    override fun getType(): Class<Long> = Long::class.java
}


@Serializable
@SerialName("string")
data object StringDataType : DataType.ConstantType<String> {
    override fun toString(): String = "String"

    override fun getType(): Class<String> = String::class.java
}


@Serializable
@SerialName("boolean")
data object BooleanDataType : DataType.ConstantType<Boolean> {
    override fun toString(): String = "Boolean"

    override fun getType(): Class<Boolean> = Boolean::class.java
}


@Serializable
@SerialName("null")
data object NullDataType : DataType.NullType // TODO: implement nulls?


@Serializable
@SerialName("unit")
data object UnitDataType : DataType.UnitType

// TODO: `Any` type?
// TODO: Support subtyping of some sort in the schema rather than via reflection?
