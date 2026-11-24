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

package org.gradle.internal.declarativedsl.analysis

import kotlinx.serialization.Serializable
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.language.DataTypeInternal.DefaultParameterizedTypeSignature

@Serializable
data object DefaultVarargSignature : DataType.VarargSignature {
    override val typeParameters: List<DataType.ParameterizedTypeSignature.TypeParameter>
        get() = listOf(DefaultParameterizedTypeSignature.TypeParameter("T", isOutVariant = true))
    override val name: FqName
        get() = DefaultFqName.Companion.parse(Array::class.qualifiedName!!)
    override val javaTypeName: String
        get() = "[]"
}
