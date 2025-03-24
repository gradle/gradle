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

import org.gradle.declarative.dsl.schema.DataType.ClassDataType
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument
import java.io.Serializable


interface AnalysisSchema : Serializable {
    val topLevelReceiverType: DataClass
    val dataClassTypesByFqName: Map<FqName, ClassDataType>

    /**
     * The signatures for generic types, which are families of types that may have different type arguments.
     * Note: type signatures are not types themselves.
     */
    val genericSignaturesByFqName: Map<FqName, DataType.ParameterizedTypeSignature>

    /**
     * Instantiations of the generic types that are used in the schema.
     * The key matches that of [genericSignaturesByFqName].
     * The inner map's key is the full type arguments list for the instantiation.
     *
     * The type references in the inner map's [TypeArgument] keys are represented by the [DataTypeRef.Name] or [DataTypeRef.NameWithArgs] whenever possible instead
     * of direct [DataTypeRef.Type] references.
     *
     * An instantiation is a type that may be referenced elsewhere in the schema.
     */
    val genericInstantiationsByFqName: Map<FqName, Map<List<TypeArgument>, ClassDataType>>

    /**
     * Available assignment augmentations, grouped by the type identified by its name as in [dataClassTypesByFqName] or [genericSignaturesByFqName].
     */
    val assignmentAugmentationsByTypeName: Map<FqName, List<AssignmentAugmentation>>

    val externalFunctionsByFqName: Map<FqName, DataTopLevelFunction>
    val externalObjectsByFqName: Map<FqName, ExternalObjectProviderKey>
    val defaultImports: Set<FqName>
}
