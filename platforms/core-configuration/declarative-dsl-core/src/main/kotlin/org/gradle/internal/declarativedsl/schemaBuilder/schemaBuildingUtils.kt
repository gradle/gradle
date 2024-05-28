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
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.ref
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType


fun KClassifier.toDataTypeRef(): DataTypeRef =
    when (this) {
        Unit::class -> DataTypeInternal.DefaultUnitType.ref
        Int::class -> DataTypeInternal.DefaultIntDataType.ref
        String::class -> DataTypeInternal.DefaultStringDataType.ref
        Boolean::class -> DataTypeInternal.DefaultBooleanDataType.ref
        Long::class -> DataTypeInternal.DefaultLongDataType.ref
        is KClass<*> -> DataTypeRefInternal.DefaultName(DefaultFqName.parse(checkNotNull(qualifiedName)))
        else -> error("unexpected type")
    }


internal
fun KType.checkInScope(typeScope: DataSchemaBuilder.PreIndex, receiver: KClass<*>? = null, function: KFunction<*>) {
    if (classifier?.isInScope(typeScope) != true) {
        error("Type used in function ${format(receiver, function)} is not in schema scope: $this")
    }
}


private
fun KClassifier.isInScope(typeScope: DataSchemaBuilder.PreIndex) =
    isBuiltInType || this is KClass<*> && typeScope.hasType(this)


private
val KClassifier.isBuiltInType: Boolean
    get() = when (this) {
        Int::class, String::class, Boolean::class, Long::class, Unit::class -> true
        else -> false
    }


val KCallable<*>.annotationsWithGetters: List<Annotation>
    get() = this.annotations + if (this is KProperty) this.getter.annotations else emptyList()


fun KCallable<*>.returnTypeToRefOrError(receiver: KClass<*>?) =
    returnTypeToRefOrError(receiver) { this.returnType }


fun KCallable<*>.returnTypeToRefOrError(receiver: KClass<*>?, typeMapping: (KCallable<*>) -> KType) =
    typeMapping(this).toDataTypeRef() ?: error("Conversion to data types failed for return type of ${format(receiver, this)}: ${typeMapping(this)}")


fun KParameter.parameterTypeToRefOrError(receiver: KClass<*>?, function: KFunction<*>) =
    parameterTypeToRefOrError(receiver, function) { this.type }


fun KParameter.parameterTypeToRefOrError(receiver: KClass<*>?, function: KFunction<*>, typeMapping: (KParameter) -> KType) =
    typeMapping(this).toDataTypeRef() ?: error("Conversion to data types failed for parameter type of function ${format(receiver, function)}: ${typeMapping(this)}")


private
fun format(receiver: KClass<*>?, callable: KCallable<*>) =
    "${receiver?.simpleName.let { s -> "$s." }}${callable.name}"


fun KType.toDataTypeRef(): DataTypeRef? = when {
    // isMarkedNullable -> TODO: support nullable types
    arguments.isNotEmpty() -> null // TODO: support for some particular generic types
    else -> when (val classifier = classifier) {
        null -> null
        else -> classifier.toDataTypeRef()
    }
}
