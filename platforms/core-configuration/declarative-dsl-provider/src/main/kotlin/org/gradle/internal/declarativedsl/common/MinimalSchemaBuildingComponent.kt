/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.declarativedsl.common

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.provider.Provider
import org.gradle.internal.declarativedsl.dependencycollectors.DependencyCollectorFunctionExtractorAndRuntimeResolver
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.gradleConfigureLambdas
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultFunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultPropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.GetterBasedConfiguringFunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedCallable
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection
import org.gradle.internal.declarativedsl.schemaBuilder.isValidNestedModelType
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Defines a minimal set of features for Declarative DSL evaluation.
 * The only Gradle-related customization in this component are [gradleConfigureLambdas] and NDOC & Provider type awareness in [GetterBasedConfiguringFunctionExtractor].
 * Besides, no custom Gradle APIs are considered as schema contributors.
 */
class MinimalSchemaBuildingComponent : AnalysisSchemaComponent {
    override fun propertyExtractors(): List<PropertyExtractor> =
        listOf(DefaultPropertyExtractor(includePredicate = ::isValidBasicProperty))
    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        DefaultFunctionExtractor(configureLambdas = gradleConfigureLambdas),
        GetterBasedConfiguringFunctionExtractor(::isValidNestedGradleModelType)
    )
}

private fun isValidNestedGradleModelType(type: SupportedTypeProjection.SupportedType): Boolean =
    (type.classifier as? KClass<*>)?.let {
        isValidNestedModelType(type) && !it.isSubclassOf(Provider::class) &&
            /**
             * For NDOCs, we generate the configuring functions with synthetic types in [org.gradle.internal.declarativedsl.ndoc.ContainersSchemaComponent]
             * TODO replace this check with a generic schema member inclusion tracking mechanism
             */
            !it.isSubclassOf(NamedDomainObjectContainer::class) &&
            !it.isSubclassOf(DependencyCollector::class)
    } == true

/**
 * Exclude properties imported as dependency collectors.
 * TODO: find a better communication mechanism for the schema builder components to coordinate importing a member
 */
private fun isValidBasicProperty(owner: KClass<*>, callable: SupportedCallable): Boolean =
    !DependencyCollectorFunctionExtractorAndRuntimeResolver.Companion.isDependencyCollectorPropertyOrGetter(owner, callable)
