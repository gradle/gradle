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

package org.gradle.internal.declarativedsl.evaluationSchema

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisStatementFilter
import com.h0tk3y.kotlin.staticObjectNotation.analysis.analyzeEverything
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RuntimeCustomAccessors
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RuntimeFunctionResolver
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RuntimePropertyResolver
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.ConfigureLambdaHandler
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.kotlinFunctionAsConfigureLambda
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.plus
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.treatInterfaceAsConfigureLambda
import org.gradle.api.Action


class EvaluationSchema(
    val analysisSchema: AnalysisSchema,
    val analysisStatementFilter: AnalysisStatementFilter = analyzeEverything,
    val configureLambdas: ConfigureLambdaHandler = kotlinFunctionAsConfigureLambda.plus(treatInterfaceAsConfigureLambda(Action::class)),
    val runtimePropertyResolvers: List<RuntimePropertyResolver> = emptyList(),
    val runtimeFunctionResolvers: List<RuntimeFunctionResolver> = emptyList(),
    val runtimeCustomAccessors: List<RuntimeCustomAccessors> = emptyList()
)
