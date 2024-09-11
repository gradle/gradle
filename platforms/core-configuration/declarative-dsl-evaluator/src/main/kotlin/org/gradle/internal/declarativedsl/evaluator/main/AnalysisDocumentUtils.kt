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

package org.gradle.internal.declarativedsl.evaluator.main

import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature.ResolutionResultPostprocessing.ApplyModelDefaults
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature.ResolutionResultPostprocessing.DefineModelDefaults
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.toDocument
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlay
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.dom.resolution.resolutionContainer
import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsDocumentTransformation
import org.gradle.internal.declarativedsl.evaluator.defaults.findUsedSoftwareTypeNames
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepResult
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult
import org.gradle.internal.declarativedsl.evaluator.runner.stepResultOrPartialResult


object AnalysisDocumentUtils {
    fun documentWithModelDefaults(modelDefaultsSequenceResult: AnalysisSequenceResult, mainSequenceResult: AnalysisSequenceResult): DocumentOverlayResult? {
        val usedModelDefaults = mainSequenceResult.modelDefaultsConsumingStep()?.stepResultOrPartialResult?.usedSoftwareTypeNames()
            ?: return null

        val modelDefaults = modelDefaultsSequenceResult.extractModelDefaultsDocument(usedModelDefaults) ?: return null
        val modelDefaultsConsumingDocument = mainSequenceResult.modelDefaultsConsumingDocument() ?: return null

        return DocumentOverlay.overlayResolvedDocuments(modelDefaults, modelDefaultsConsumingDocument)
    }

    fun AnalysisStepResult.resolvedDocument(): DocumentWithResolution {
        val evaluationSchema = evaluationSchema
        val document = languageTreeResult.toDocument()
        val resolutionContainer = resolutionContainer(evaluationSchema.analysisSchema, resolutionTrace, document)
        return DocumentWithResolution(document, resolutionContainer)
    }

    fun AnalysisStepResult.usedSoftwareTypeNames(): Set<String> =
        findUsedSoftwareTypeNames(resolutionResult)

    fun AnalysisSequenceResult.extractModelDefaultsDocument(forSoftwareTypes: Set<String>): DocumentWithResolution? {
        val modelDefaultsStep = stepResults.entries.singleOrNull { (step, _) -> step.features.any { it is DefineModelDefaults } }
        val modelDefaultsEvaluated = modelDefaultsStep?.value
        val originalDocument = modelDefaultsEvaluated?.stepResultOrPartialResult?.resolvedDocument()
            ?: return null
        val transformedDocument = ModelDefaultsDocumentTransformation.extractDefaults(originalDocument.document, originalDocument.resolutionContainer, forSoftwareTypes)
        return DocumentWithResolution(transformedDocument, originalDocument.resolutionContainer)
    }

    fun AnalysisSequenceResult.modelDefaultsConsumingDocument(): DocumentWithResolution? =
        modelDefaultsConsumingStep()?.stepResultOrPartialResult?.resolvedDocument()

    private
    fun AnalysisSequenceResult.modelDefaultsConsumingStep(): EvaluationResult<AnalysisStepResult>? =
        stepResults.entries.singleOrNull { (step, _) -> step.features.any { it is ApplyModelDefaults } }?.value
}
