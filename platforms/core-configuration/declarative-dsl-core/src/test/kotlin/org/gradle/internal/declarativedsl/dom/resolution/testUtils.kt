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

package org.gradle.internal.declarativedsl.dom.resolution

import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.dom.DocumentResolution

internal fun resolutionPrettyString(resolution: DocumentResolution): String =
    resolution::class.simpleName + when (resolution) {
        is DocumentResolution.ElementResolution.SuccessfulElementResolution.ContainerElementResolved ->
            " -> element ${functionSignatureString(resolution.elementFactoryFunction)}"

        is DocumentResolution.ElementResolution.SuccessfulElementResolution.ConfiguringElementResolved ->
            " -> configure ${resolution.elementType}"

        is DocumentResolution.PropertyResolution.PropertyAssignmentResolved ->
            " -> ${resolution.receiverType}.${resolution.property.name}: ${typeString(resolution.property.valueType)}"

        is DocumentResolution.ValueNodeResolution.ValueFactoryResolution.ValueFactoryResolved ->
            " -> ${functionSignatureString(resolution.function)}"

        is DocumentResolution.ValueNodeResolution.LiteralValueResolved -> " -> ${resolution.value}"
        is DocumentResolution.UnsuccessfulResolution -> "(${resolution.reasons.joinToString()})"
        is DocumentResolution.ValueNodeResolution.NamedReferenceResolution.NamedReferenceResolved -> " -> ${resolution.referenceName}"
    }

private fun functionSignatureString(function: SchemaFunction) =
    "${function.simpleName}(${function.parameters.joinToString { typeString(it.type) }}): ${typeString(function.returnValueType)}"

private fun typeString(typeRef: DataTypeRef) = when (typeRef) {
    is DataTypeRef.Type -> typeRef.dataType.toString()
    is DataTypeRef.Name -> typeRef.fqName.simpleName
}
