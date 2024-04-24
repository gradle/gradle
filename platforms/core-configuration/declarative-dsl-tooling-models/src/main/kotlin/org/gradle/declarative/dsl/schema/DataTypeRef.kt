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

import java.io.Serializable


sealed interface DataTypeRef : Serializable {

    fun isNamed(): Boolean = false

    fun getDataType(): DataType = error("Not a reference to a named data type")

    fun isTyped(): Boolean = false

    fun getFqName(): FqName = error("Data type only available as a name")

    interface Type : DataTypeRef

    interface Name : DataTypeRef
}
