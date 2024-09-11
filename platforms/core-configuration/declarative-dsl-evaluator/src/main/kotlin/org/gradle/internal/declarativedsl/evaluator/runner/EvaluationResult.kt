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

import org.gradle.internal.declarativedsl.analysis.ResolutionError
import org.gradle.internal.declarativedsl.evaluator.schema.DeclarativeScriptContext
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailure
import org.gradle.internal.declarativedsl.language.SingleFailureResult
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTraceElement


sealed interface EvaluationResult<out R : StepResult> {
    class Evaluated<R : StepResult>(val stepResult: R) : EvaluationResult<R>

    class NotEvaluated<R : StepResult>(
        val stageFailures: List<StageFailure>,
        // This could have been a supertype's property, but always distinguishing between a successful result and a failing one is useful.
        val partialStepResult: R
    ) : EvaluationResult<R> {
        sealed interface StageFailure {
            data class NoSchemaAvailable(val scriptContext: DeclarativeScriptContext) : StageFailure
            object NoParseResult : StageFailure
            data class FailuresInLanguageTree(val failures: List<SingleFailureResult>) : StageFailure
            data class FailuresInResolution(val errors: List<ResolutionError>) : StageFailure
            data class DocumentCheckFailures(val failures: List<DocumentCheckFailure>) : StageFailure
            data class AssignmentErrors(val usages: List<AssignmentTraceElement.FailedToRecordAssignment>) : StageFailure
        }
    }
}


val <R : StepResult> EvaluationResult<R>.stepResultOrPartialResult
    get() = when (this) {
        is EvaluationResult.Evaluated -> stepResult
        is EvaluationResult.NotEvaluated -> partialStepResult
    }
