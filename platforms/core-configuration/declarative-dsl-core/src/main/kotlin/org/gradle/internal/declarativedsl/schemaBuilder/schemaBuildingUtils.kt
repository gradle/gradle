/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.declarativedsl.schemaBuilder

import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingTags.parameter
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingTags.returnValueType
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty


val KCallable<*>.annotationsWithGetters: List<Annotation>
    get() = this.annotations + if (this is KProperty) this.getter.annotations else emptyList()


fun KCallable<*>.returnTypeRef(host: SchemaBuildingHost): SchemaResult<DataTypeRef> {
    return host.withTag(returnValueType(this.returnType)) {
        host.modelTypeRef(this.returnType)
    }
}

fun SupportedCallable.returnTypeRef(host: SchemaBuildingHost) =
    returnTypeRef(host) { this.returnType }

fun SupportedCallable.returnTypeRef(host: SchemaBuildingHost, typeMapping: (SupportedCallable) -> SupportedTypeProjection.SupportedType): SchemaResult<DataTypeRef> {
    val returnType = typeMapping(this)

    return host.withTag(returnValueType(returnType)) {
        host.modelTypeRef(returnType.toKType())
    }
}

fun KParameter.parameterTypeToRef(host: SchemaBuildingHost): SchemaResult<DataTypeRef> {
    return host.withTag(parameter(this)) {
        if (isVararg)
            host.varargTypeRef(type)
        else
            host.modelTypeRef(type)
    }
}

fun SupportedKParameter.parameterTypeToRef(host: SchemaBuildingHost): SchemaResult<DataTypeRef> =
    host.withTag(parameter(this)) {
        if (isVararg)
            host.varargTypeRef(type.toKType())
        else host.typeRef(type.toKType())
    }
