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

import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.schemaBuilder.CompositeFunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.CompositePropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.CompositeTypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import kotlin.reflect.KClass


internal
fun buildEvaluationSchema(
    topLevelReceiverType: KClass<*>,
    component: EvaluationSchemaComponent,
    analysisStatementFilter: AnalysisStatementFilter
): EvaluationSchema {
    return EvaluationSchema(
        schemaFromTypes(
            topLevelReceiverType,
            listOf(topLevelReceiverType),
            configureLambdas = gradleConfigureLambdas,
            propertyExtractor = CompositePropertyExtractor(component.propertyExtractors()),
            functionExtractor = CompositeFunctionExtractor(component.functionExtractors()),
            typeDiscovery = CompositeTypeDiscovery(component.typeDiscovery())
        ),
        analysisStatementFilter = analysisStatementFilter,
        documentChecks = component.documentChecks(),
        runtimePropertyResolvers = component.runtimePropertyResolvers(),
        runtimeFunctionResolvers = component.runtimeFunctionResolvers(),
        runtimeCustomAccessors = component.runtimeCustomAccessors()
    )
}
