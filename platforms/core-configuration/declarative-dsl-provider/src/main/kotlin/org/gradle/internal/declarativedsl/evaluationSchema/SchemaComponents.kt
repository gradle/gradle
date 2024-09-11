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

import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.declarative.dsl.evaluation.OperationGenerationId
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.evaluator.schema.DefaultEvaluationSchema
import org.gradle.internal.declarativedsl.evaluator.conversion.DefaultEvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluator.conversion.EvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver
import org.gradle.internal.declarativedsl.schemaBuilder.CompositeFunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.CompositePropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.CompositeTypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import kotlin.reflect.KClass


internal
interface EvaluationSchemaBuilder {
    fun registerAnalysisSchemaComponent(analysisSchemaComponent: AnalysisSchemaComponent)
    val typeDiscoveries: List<TypeDiscovery>
    val propertyExtractors: List<PropertyExtractor>
    val functionExtractors: List<FunctionExtractor>
}


internal
fun EvaluationSchemaBuilder.ifConversionSupported(thenConfigure: EvaluationAndConversionSchemaBuilder.() -> Unit) {
    if (this@ifConversionSupported is EvaluationAndConversionSchemaBuilder)
        thenConfigure()
}


internal
interface EvaluationAndConversionSchemaBuilder : EvaluationSchemaBuilder {
    fun registerObjectConversionComponent(objectConversionComponent: ObjectConversionComponent)
    val runtimePropertyResolvers: List<RuntimePropertyResolver>
    val runtimeFunctionResolvers: List<RuntimeFunctionResolver>
    val runtimeCustomAccessors: List<RuntimeCustomAccessors>
}


internal
fun buildEvaluationSchema(
    topLevelReceiverType: KClass<*>,
    analysisStatementFilter: AnalysisStatementFilter,
    operationGenerationId: OperationGenerationId = DefaultOperationGenerationId.finalEvaluation,
    schemaComponents: EvaluationSchemaBuilder.() -> Unit
): EvaluationSchema = DefaultEvaluationSchema(
    analysisSchema(topLevelReceiverType, DefaultEvaluationSchemaBuilder().apply(schemaComponents)),
    analysisStatementFilter,
    operationGenerationId
)


internal
fun buildEvaluationAndConversionSchema(
    topLevelReceiverType: KClass<*>,
    analysisStatementFilter: AnalysisStatementFilter,
    operationGenerationId: OperationGenerationId = DefaultOperationGenerationId.finalEvaluation,
    schemaComponents: EvaluationAndConversionSchemaBuilder.() -> Unit
): EvaluationAndConversionSchema {
    val builder = DefaultEvaluationAndConversionSchemaBuilder().apply(schemaComponents)
    val analysisSchema = analysisSchema(topLevelReceiverType, builder)
    return DefaultEvaluationAndConversionSchema(
        analysisSchema,
        analysisStatementFilter,
        operationGenerationId,
        runtimePropertyResolvers = builder.runtimePropertyResolvers,
        runtimeFunctionResolvers = builder.runtimeFunctionResolvers,
        runtimeCustomAccessors = builder.runtimeCustomAccessors
    )
}


/**
 * Provides grouping capabilities for features used in schema building.
 */
internal
interface AnalysisSchemaComponent {
    fun typeDiscovery(): List<TypeDiscovery> = listOf()
    fun propertyExtractors(): List<PropertyExtractor> = listOf()
    fun functionExtractors(): List<FunctionExtractor> = listOf()
}


/**
 * Provides grouping capabilities for features used in DCL-to-JVM object conversion.
 */
internal
interface ObjectConversionComponent {
    fun runtimePropertyResolvers(): List<RuntimePropertyResolver> = listOf()
    fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = listOf()
    fun runtimeCustomAccessors(): List<RuntimeCustomAccessors> = listOf()
}


private
fun analysisSchema(
    topLevelReceiverType: KClass<*>,
    builder: EvaluationSchemaBuilder
): AnalysisSchema {
    val analysisSchema = schemaFromTypes(
        topLevelReceiverType,
        listOf(topLevelReceiverType),
        configureLambdas = gradleConfigureLambdas,
        propertyExtractor = CompositePropertyExtractor(builder.propertyExtractors),
        functionExtractor = CompositeFunctionExtractor(builder.functionExtractors),
        typeDiscovery = CompositeTypeDiscovery(builder.typeDiscoveries)
    )
    return analysisSchema
}


private
open class DefaultEvaluationSchemaBuilder : EvaluationSchemaBuilder {
    private
    val analysisSchemaComponents = mutableListOf<AnalysisSchemaComponent>()

    override val typeDiscoveries: List<TypeDiscovery>
        get() = analysisSchemaComponents.flatMap { it.typeDiscovery() }

    override val propertyExtractors: List<PropertyExtractor>
        get() = analysisSchemaComponents.flatMap { it.propertyExtractors() }

    override val functionExtractors: List<FunctionExtractor>
        get() = analysisSchemaComponents.flatMap { it.functionExtractors() }

    override fun registerAnalysisSchemaComponent(analysisSchemaComponent: AnalysisSchemaComponent) {
        analysisSchemaComponents += analysisSchemaComponent
    }
}


private
open class DefaultEvaluationAndConversionSchemaBuilder : DefaultEvaluationSchemaBuilder(), EvaluationAndConversionSchemaBuilder {
    private
    val objectConversionComponents = mutableListOf<ObjectConversionComponent>()

    override val runtimePropertyResolvers: List<RuntimePropertyResolver>
        get() = objectConversionComponents.flatMap { it.runtimePropertyResolvers() }

    override val runtimeFunctionResolvers: List<RuntimeFunctionResolver>
        get() = objectConversionComponents.flatMap { it.runtimeFunctionResolvers() }

    override val runtimeCustomAccessors: List<RuntimeCustomAccessors>
        get() = objectConversionComponents.flatMap { it.runtimeCustomAccessors() }


    override fun registerObjectConversionComponent(objectConversionComponent: ObjectConversionComponent) {
        objectConversionComponents += objectConversionComponent
    }
}
