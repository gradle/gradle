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

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Builder
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.HasDefaultValue
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.FqName
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility


fun schemaFromTypes(
    topLevelReceiver: KClass<*>,
    types: Iterable<KClass<*>>,
    externalFunctions: List<KFunction<*>> = emptyList(),
    externalObjects: Map<FqName, KClass<*>> = emptyMap(),
    defaultImports: List<FqName> = emptyList(),
    configureLambdas: ConfigureLambdaHandler = kotlinFunctionAsConfigureLambda,
    propertyExtractor: PropertyExtractor = DefaultPropertyExtractor(isPublicAndRestricted),
    functionExtractor: FunctionExtractor = DefaultFunctionExtractor(configureLambdas, isPublicAndRestricted),
    typeDiscovery: TypeDiscovery = TypeDiscovery.none
): AnalysisSchema =
    DataSchemaBuilder(typeDiscovery, propertyExtractor, functionExtractor).schemaFromTypes(
        topLevelReceiver, types, externalFunctions, externalObjects, defaultImports
    )


val isPublic: MemberFilter = MemberFilter { member: KCallable<*> ->
    member.visibility == KVisibility.PUBLIC
}


val isPublicAndRestricted: MemberFilter = MemberFilter { member: KCallable<*> ->
    member.visibility == KVisibility.PUBLIC &&
        member.annotationsWithGetters.any {
            it is Builder || it is Configuring || it is Adding || it is Restricted || it is HasDefaultValue
        }
}
