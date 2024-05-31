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

import org.gradle.declarative.dsl.evaluation.InterpretationSequenceStep
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature.DocumentChecks
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.toDocument
import org.gradle.internal.declarativedsl.dom.resolution.resolutionContainer
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated.StageFailure.AssignmentErrors
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated.StageFailure.DocumentCheckFailures
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated.StageFailure.FailuresInLanguageTree
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTraceElement
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTracer
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse


open class AnalysisStepRunner : InterpretationSequenceStepRunner<AnalysisStepContext, AnalysisStepResult> {
    override fun runInterpretationSequenceStep(
        scriptIdentifier: String,
        scriptSource: String,
        step: InterpretationSequenceStep,
        stepContext: AnalysisStepContext
    ): EvaluationResult<AnalysisStepResult> {
        val failureReasons = mutableListOf<NotEvaluated.StageFailure>()

        val evaluationSchema = step.evaluationSchemaForStep

        val resolver = tracingCodeResolver(evaluationSchema.operationGenerationId, evaluationSchema.analysisStatementFilter)

        val languageModel = languageModelFromLightParser(scriptIdentifier, scriptSource)

        if (languageModel.allFailures.isNotEmpty()) {
            failureReasons += FailuresInLanguageTree(languageModel.allFailures)
        }
        val initialResolution = resolver.resolve(evaluationSchema.analysisSchema, languageModel.imports, languageModel.topLevelBlock)
        if (initialResolution.errors.isNotEmpty()) {
            failureReasons += NotEvaluated.StageFailure.FailuresInResolution(initialResolution.errors)
        }

        val postProcessingFeatures = step.features.filterIsInstance<InterpretationStepFeature.ResolutionResultPostprocessing>()
        val resultHandlers = stepContext.supportedResolutionResultHandlers.filter { processor -> postProcessingFeatures.any(processor::shouldHandleFeature) }
        val resolution = resultHandlers.fold(initialResolution) { acc, it -> it.processResolutionResult(acc) }

        val document = languageModel.toDocument()
        val documentResolutionContainer = resolutionContainer(evaluationSchema.analysisSchema, resolver.trace, document)

        val checkFeatures = step.features.filterIsInstance<DocumentChecks>()
        val checkResults = stepContext.supportedDocumentChecks.filter { checkFeatures.any(it::shouldHandleFeature) }
            .flatMap { it.detectFailures(document, documentResolutionContainer) }

        if (checkResults.isNotEmpty()) {
            failureReasons += DocumentCheckFailures(checkResults)
        }

        val assignmentTrace = assignmentTrace(resolution)
        val assignmentErrors = assignmentTrace.elements.filterIsInstance<AssignmentTraceElement.FailedToRecordAssignment>()
        if (assignmentErrors.isNotEmpty()) {
            failureReasons += AssignmentErrors(assignmentErrors)
        }

        val analysisResult = AnalysisStepResult(evaluationSchema, languageModel, resolution, resolver.trace, assignmentTrace)

        return when {
            failureReasons.isNotEmpty() -> NotEvaluated(failureReasons, partialStepResult = analysisResult)
            else -> EvaluationResult.Evaluated(analysisResult)
        }
    }

    private
    fun assignmentTrace(result: ResolutionResult) =
        AssignmentTracer { AssignmentResolver() }.produceAssignmentTrace(result)

    private
    fun languageModelFromLightParser(scriptIdentifier: String, scriptSource: String): LanguageTreeResult {
        val parsedTree = parse(scriptSource)
        return languageTreeBuilder.build(parsedTree, SourceIdentifier(scriptIdentifier))
    }

    private
    val languageTreeBuilder = DefaultLanguageTreeBuilder()
}
