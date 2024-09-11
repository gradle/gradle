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

package org.gradle.internal.declarativedsl.evaluator.conversion

import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.declarative.dsl.evaluation.OperationGenerationId
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver


class DefaultEvaluationAndConversionSchema(
    override val analysisSchema: AnalysisSchema,
    override val analysisStatementFilter: AnalysisStatementFilter = analyzeEverything,
    override val operationGenerationId: OperationGenerationId = DefaultOperationGenerationId.finalEvaluation,
    override val runtimePropertyResolvers: List<RuntimePropertyResolver> = listOf(),
    override val runtimeFunctionResolvers: List<RuntimeFunctionResolver> = listOf(),
    override val runtimeCustomAccessors: List<RuntimeCustomAccessors> = emptyList(),
) : EvaluationAndConversionSchema
