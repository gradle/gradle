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

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.FqName
import kotlin.reflect.KClass


@Suppress("LongParameterList")
fun schemaFromTypes(
    topLevelReceiver: KClass<*>,
    types: Iterable<KClass<*>> = listOf(topLevelReceiver),
    externalFunctionDiscovery: TopLevelFunctionDiscovery = CompositeTopLevelFunctionDiscovery(listOf()),
    externalObjects: Map<FqName, KClass<*>> = emptyMap(),
    defaultImports: List<FqName> = emptyList(),
    configureLambdas: ConfigureLambdaHandler = kotlinFunctionAsConfigureLambda,
    propertyExtractor: PropertyExtractor = DefaultPropertyExtractor(),
    functionExtractor: FunctionExtractor = basicFunctionExtractor(configureLambdas),
    augmentationsProvider: AugmentationsProvider = CompositeAugmentationsProvider(emptyList()),
    typeDiscovery: TypeDiscovery = basicTypeDiscovery(configureLambdas),
    failureReporter: SchemaFailureReporter = ThrowingSchemaFailureReporter
): AnalysisSchema =
    DataSchemaBuilder(typeDiscovery, propertyExtractor, functionExtractor, augmentationsProvider).schemaFromTypes(
        topLevelReceiver, types, externalFunctionDiscovery.discoverTopLevelFunctions(), externalObjects, defaultImports, failureReporter
    )

fun basicFunctionExtractor(configureLambdas: ConfigureLambdaHandler): CompositeFunctionExtractor = CompositeFunctionExtractor(
    listOf(
        GetterBasedConfiguringFunctionExtractor(::isValidNestedModelType),
        DefaultFunctionExtractor(configureLambdas)
    )
)

fun basicTypeDiscovery(configureLambdas: ConfigureLambdaHandler) = CompositeTypeDiscovery(listOf(FunctionLambdaTypeDiscovery(configureLambdas), FunctionReturnTypeDiscovery(), SupertypeDiscovery()))
