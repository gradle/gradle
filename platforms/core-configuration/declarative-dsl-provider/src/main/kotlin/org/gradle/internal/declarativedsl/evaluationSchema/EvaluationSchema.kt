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

import org.gradle.internal.declarativedsl.analysis.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.checks.DocumentCheck
import org.gradle.internal.declarativedsl.mappingToJvm.MemberFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.ReflectionRuntimePropertyResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver


internal
class EvaluationSchema(
    val analysisSchema: AnalysisSchema,
    val analysisStatementFilter: AnalysisStatementFilter = analyzeEverything,
    val documentChecks: List<DocumentCheck> = listOf(),
    val runtimePropertyResolvers: List<RuntimePropertyResolver> = listOf(ReflectionRuntimePropertyResolver),
    val runtimeFunctionResolvers: List<RuntimeFunctionResolver> = listOf(MemberFunctionResolver(gradleConfigureLambdas)),
    val runtimeCustomAccessors: List<RuntimeCustomAccessors> = emptyList(),
)
