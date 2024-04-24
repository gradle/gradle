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

package org.gradle.internal.declarativedsl.schema


sealed interface DataType {
    sealed interface ConstantType<JvmType> : DataType

    interface IntDataType : ConstantType<Int>
    interface LongDataType : ConstantType<Long>
    interface StringDataType : ConstantType<String>
    interface BooleanDataType : ConstantType<Boolean>

    // TODO: implement nulls?
    interface NullType : DataType

    interface UnitType : DataType

    // TODO: `Any` type?
    // TODO: Support subtyping of some sort in the schema rather than via reflection?
}
