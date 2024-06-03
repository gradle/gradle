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

package org.gradle.internal.declarativedsl.evaluationSchema

import org.gradle.internal.declarativedsl.checks.DocumentCheck
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery


/**
 * Provides grouping capabilities for features used in schema building.
 */
internal
interface EvaluationSchemaComponent {
    fun typeDiscovery(): List<TypeDiscovery> = listOf()
    fun propertyExtractors(): List<PropertyExtractor> = listOf()
    fun functionExtractors(): List<FunctionExtractor> = listOf()

    fun documentChecks(): List<DocumentCheck> = listOf()

    fun runtimePropertyResolvers(): List<RuntimePropertyResolver> = listOf()
    fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = listOf()
    fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf()
}


internal
operator fun EvaluationSchemaComponent.plus(other: EvaluationSchemaComponent) = CompositeEvaluationSchemaComponent(
    buildList {
        fun include(component: EvaluationSchemaComponent) =
            if (component is CompositeEvaluationSchemaComponent) addAll(component.components) else add(component)

        include(this@plus)
        include(other)
    }
)


internal
class CompositeEvaluationSchemaComponent(
    internal val components: List<EvaluationSchemaComponent>
) : EvaluationSchemaComponent {
    init {
        components.forEach { check(it !is CompositeEvaluationSchemaComponent) { "Composite schema components are not allowed to be nested" } }
    }

    override fun typeDiscovery(): List<TypeDiscovery> = components.flatMap(EvaluationSchemaComponent::typeDiscovery)

    override fun propertyExtractors(): List<PropertyExtractor> = components.flatMap(EvaluationSchemaComponent::propertyExtractors)

    override fun functionExtractors(): List<FunctionExtractor> = components.flatMap(EvaluationSchemaComponent::functionExtractors)

    override fun documentChecks(): List<DocumentCheck> = components.flatMap(EvaluationSchemaComponent::documentChecks)

    override fun runtimePropertyResolvers(): List<RuntimePropertyResolver> = components.flatMap(EvaluationSchemaComponent::runtimePropertyResolvers)

    override fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = components.flatMap(EvaluationSchemaComponent::runtimeFunctionResolvers)

    override fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = components.flatMap(EvaluationSchemaComponent::runtimeCustomAccessors)
}
