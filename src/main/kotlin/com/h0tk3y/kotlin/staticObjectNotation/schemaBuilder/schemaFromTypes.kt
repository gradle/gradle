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

package com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataTypeRef
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FqName
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType

fun schemaFromTypes(
    topLevelReceiver: KClass<*>,
    types: List<KClass<*>>,
    externalFunctions: List<KFunction<*>> = emptyList(),
    externalObjects: Map<FqName, KClass<*>> = emptyMap(),
    defaultImports: List<FqName> = emptyList(),
    dataClassSchemaProducer: DataClassSchemaProducer = defaultDataClassSchemaProducer,
    configureLambdas: ConfigureLambdaHandler = kotlinFunctionAsConfigureLambda,
): AnalysisSchema =
    DataSchemaBuilder(dataClassSchemaProducer).schemaFromTypes(
        topLevelReceiver, types, externalFunctions, externalObjects, defaultImports, configureLambdas
    )

fun KType.toKClass() = (classifier ?: error("unclassifiable type $this is used in the schema")) as? KClass<*>
    ?: error("type $this classified as a non-class is used in the schema")


internal fun KType.toDataTypeRefOrError() =
    toDataTypeRef()
        ?: error("failed to convert type $this to data type")

private fun KType.toDataTypeRef(): DataTypeRef? = when {
    // isMarkedNullable -> TODO: support nullable types
    arguments.isNotEmpty() -> null // TODO: support for some particular generic types
    else -> when (val classifier = classifier) {
        null -> null
        else -> classifier.toDataTypeRef()
    }
}
