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

package org.gradle.declarative.dsl.schema

import org.gradle.tooling.ToolingModelContract
import java.io.Serializable


@ToolingModelContract(subTypes = [
    DataType.NullType::class,
    DataType.UnitType::class,
    DataType.ConstantType::class,
    DataType.IntDataType::class,
    DataType.LongDataType::class,
    DataType.StringDataType::class,
    DataType.BooleanDataType::class,
    DataType.ClassDataType::class,
    DataClass::class,
    EnumClass::class
])
sealed interface DataType : Serializable {

    @ToolingModelContract(subTypes = [
        IntDataType::class,
        LongDataType::class,
        StringDataType::class,
        BooleanDataType::class,
    ])
    sealed interface ConstantType<JvmType> : DataType {
        val constantType: Class<*>
    }

    interface IntDataType : ConstantType<Int>
    interface LongDataType : ConstantType<Long>
    interface StringDataType : ConstantType<String>
    interface BooleanDataType : ConstantType<Boolean>

    // TODO: implement nulls?
    interface NullType : DataType

    interface UnitType : DataType

    @ToolingModelContract(subTypes = [
        DataClass::class,
        EnumClass::class
    ])
    sealed interface ClassDataType : DataType {
        val name: FqName
        val javaTypeName: String
    }

    // TODO: `Any` type?
    // TODO: Support subtyping of some sort in the schema rather than via reflection?
}
