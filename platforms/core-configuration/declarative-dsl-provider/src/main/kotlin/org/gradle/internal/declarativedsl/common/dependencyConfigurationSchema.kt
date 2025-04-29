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

package org.gradle.internal.declarativedsl.common

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.plugins.jvm.PlatformDependencyModifiers
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal.DefaultUnknown
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.FixedTypeDiscovery
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.ifConversionSupported
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import kotlin.reflect.typeOf


internal
fun EvaluationSchemaBuilder.dependencyCollectors() {
    val component = DependencyCollectorsComponent()

    registerAnalysisSchemaComponent(component)
    ifConversionSupported {
        registerObjectConversionComponent(component)
    }
}


/**
 * Introduces functions for registering dependencies, such as `implementation(...)`, as member functions of
 * types with getters returning [DependencyCollector] in the schema.
 * Resolves such functions at runtime, if used with object conversion.
 */
private
class DependencyCollectorsComponent : AnalysisSchemaComponent, ObjectConversionComponent {
    private
    val dependencyCollectorFunctionExtractorAndRuntimeResolver = DependencyCollectorFunctionExtractorAndRuntimeResolver(
        gavDependencyParam = { host -> DefaultDataParameter("dependency", host.modelTypeRef(typeOf<String>()), false, DefaultUnknown) },
        dependencyParam = { host -> DefaultDataParameter("dependency", host.modelTypeRef(typeOf<Dependency>()), false, DefaultUnknown) },
    )

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        dependencyCollectorFunctionExtractorAndRuntimeResolver
    )

    override fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = listOf(
        dependencyCollectorFunctionExtractorAndRuntimeResolver
    )

    override fun typeDiscovery(): List<TypeDiscovery> {
        // External Dependency is used by the runtime PlatformDependencyModifiers function resolver, and isn't added otherwise
        return listOf(FixedTypeDiscovery(PlatformDependencyModifiers::class, listOf(ExternalDependency::class)))
    }
}
