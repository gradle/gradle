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

import org.gradle.declarative.dsl.schema.DataProperty.PropertyMode.ReadOnly
import org.gradle.declarative.dsl.schema.DataProperty.PropertyMode.ReadWrite
import org.gradle.declarative.dsl.schema.DataProperty.PropertyMode.WriteOnly
import org.gradle.tooling.ToolingModelContract
import java.io.Serializable


interface DataProperty : Serializable {
    val name: String
    val valueType: DataTypeRef
    val mode: PropertyMode
    val hasDefaultValue: Boolean
    val isHiddenInDsl: Boolean
    val isDirectAccessOnly: Boolean

    @ToolingModelContract(subTypes = [
        ReadWrite::class,
        ReadOnly::class,
        WriteOnly::class
    ])
    sealed interface PropertyMode : Serializable {
        interface ReadWrite : PropertyMode
        interface ReadOnly : PropertyMode
        interface WriteOnly : PropertyMode
    }
}
