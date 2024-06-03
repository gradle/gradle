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

package org.gradle.internal.declarativedsl.evaluator.runner

import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.ResolutionTrace
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheck
import org.gradle.internal.declarativedsl.evaluator.features.ResolutionResultHandler
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTrace


interface StepContext


data class AnalysisStepContext(
    val supportedDocumentChecks: Iterable<DocumentCheck>,
    val supportedResolutionResultHandlers: Iterable<ResolutionResultHandler>
) : StepContext


data class AnalysisStepResult(
    val evaluationSchema: EvaluationSchema,
    val languageTreeResult: LanguageTreeResult,
    val resolutionResult: ResolutionResult,
    val resolutionTrace: ResolutionTrace,
    val assignmentTrace: AssignmentTrace
) : StepResult


interface StepResult
