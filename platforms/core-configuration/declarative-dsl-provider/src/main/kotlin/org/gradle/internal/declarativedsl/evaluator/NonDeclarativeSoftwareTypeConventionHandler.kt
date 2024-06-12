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

package org.gradle.internal.declarativedsl.evaluator

import org.gradle.declarative.dsl.evaluation.InterpretationSequenceStep
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature
import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.ResolutionTrace
import org.gradle.internal.declarativedsl.conventions.softwareTypeRegistryBasedConventionRepositoryWithContext
import org.gradle.internal.declarativedsl.evaluator.conventions.ConventionApplicationHandler
import org.gradle.internal.declarativedsl.evaluator.conversion.AnalysisAndConversionStepRunner
import org.gradle.internal.declarativedsl.evaluator.conversion.ConversionStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepResult
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.Evaluated
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated.StageFailure.AssignmentErrors
import org.gradle.internal.declarativedsl.evaluator.runner.InterpretationSequenceStepRunner
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTraceElement
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTracer
import org.gradle.internal.declarativedsl.project.projectInterpretationSequenceStep
import org.gradle.plugin.software.internal.ConventionHandler
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


/**
 * A {@link ConventionHandler} for applying declarative conventions in a non-declarative context (e.g. a plugin applied from a non-declarative script).
 */
class NonDeclarativeSoftwareTypeConventionHandler(softwareTypeRegistry: SoftwareTypeRegistry) : ConventionHandler {
    private
    val step = projectInterpretationSequenceStep(softwareTypeRegistry)
    private
    val conventionRepository = softwareTypeRegistryBasedConventionRepositoryWithContext(softwareTypeRegistry)

    override fun apply(target: Any, softwareTypeName: String) {
        val analysisStepRunner = ApplyConventionsOnlyAnalysisStepRunner()
        val analysisStepContext = AnalysisStepContext(
            emptySet(),
            setOf(
                ConventionApplicationHandler(conventionRepository) { listOf(conventionRepository.findConventions(softwareTypeName)).requireNoNulls() }
            )
        )

        val result = AnalysisAndConversionStepRunner(analysisStepRunner)
            .runInterpretationSequenceStep(
                "<none>",
                "",
                step,
                ConversionStepContext(target, analysisStepContext)
            )

        when (result) {
            is Evaluated -> Unit
            is NotEvaluated -> {
                // TODO: What kind of errors can actually come out of the conversion step?  Should we have a better exception type since we're
                //  unlikely to have analysis errors?
                throw DeclarativeDslNotEvaluatedException("", result.stageFailures)
            }
        }
    }
}


class ApplyConventionsOnlyAnalysisStepRunner : InterpretationSequenceStepRunner<AnalysisStepContext, AnalysisStepResult> {
    override fun runInterpretationSequenceStep(
        scriptIdentifier: String,
        scriptSource: String,
        step: InterpretationSequenceStep,
        stepContext: AnalysisStepContext
    ): EvaluationResult<AnalysisStepResult> {
        val failureReasons = mutableListOf<NotEvaluated.StageFailure>()

        // Create a synthetic top level receiver
        val topLevelBlock = Block(emptyList(), emptySourceData())
        val topLevelReceiver = ObjectOrigin.TopLevelReceiver(step.evaluationSchemaForStep.analysisSchema.topLevelReceiverType, topLevelBlock)

        // Apply resolution handlers (which includes the convention handler)
        val postProcessingFeatures = step.features.filterIsInstance<InterpretationStepFeature.ResolutionResultPostprocessing>()
        val resultHandlers = stepContext.supportedResolutionResultHandlers.filter { processor -> postProcessingFeatures.any(processor::shouldHandleFeature) }
        val resolutionResult = resultHandlers.fold(emptyResolutionResultForReceiver(topLevelReceiver)) { acc, it -> it.processResolutionResult(acc) }

        // Create an analysis result
        val assignmentTrace = AssignmentTracer { AssignmentResolver() }.produceAssignmentTrace(resolutionResult)
        val assignmentErrors = assignmentTrace.elements.filterIsInstance<AssignmentTraceElement.FailedToRecordAssignment>()
        if (assignmentErrors.isNotEmpty()) {
            failureReasons += AssignmentErrors(assignmentErrors)
        }
        val languageTreeResult = LanguageTreeResult(emptyList(), topLevelBlock, emptyList(), emptyList())
        val analysisResult = AnalysisStepResult(step.evaluationSchemaForStep, languageTreeResult, resolutionResult, emptyResolutionTrace(), assignmentTrace)

        return when {
            failureReasons.isNotEmpty() -> NotEvaluated(failureReasons, partialStepResult = analysisResult)
            else -> Evaluated(analysisResult)
        }
    }
}


private
fun emptyResolutionTrace() = object : ResolutionTrace {
    override fun assignmentResolution(assignment: Assignment): ResolutionTrace.ResolutionOrErrors<AssignmentRecord> {
        throw UnsupportedOperationException()
    }

    override fun expressionResolution(expr: Expr): ResolutionTrace.ResolutionOrErrors<ObjectOrigin> {
        throw UnsupportedOperationException()
    }
}


private
fun emptyResolutionResultForReceiver(receiver: ObjectOrigin.TopLevelReceiver) = ResolutionResult(
    receiver,
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList()
)


private
fun emptySourceData() = object : SourceData {
    override val sourceIdentifier: SourceIdentifier
        get() = SourceIdentifier("<none>")
    override val indexRange: IntRange
        get() = IntRange.EMPTY
    override val lineRange: IntRange
        get() = IntRange.EMPTY
    override val startColumn: Int
        get() = -1
    override val endColumn: Int
        get() = -1

    override fun text(): String = "<none>"
}
