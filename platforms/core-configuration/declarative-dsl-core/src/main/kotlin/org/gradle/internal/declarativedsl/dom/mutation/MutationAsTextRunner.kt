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

package org.gradle.internal.declarativedsl.dom.mutation

import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.dom.resolution.documentWithResolution
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse


class MutationAsTextRunner {
    private
    val modelMutationPlanner = DefaultModelToDocumentMutationPlanner()

    private
    val documentToTextMutationPlanner = DocumentTextMutationPlanner()

    fun runMutation(
        mutationDefinition: MutationDefinition,
        mutationArguments: MutationArgumentContainer,
        applicationTarget: TextMutationApplicationTarget,
    ): MutationRunResult {
        val schema = applicationTarget.documentEvaluationSchema

        if (!mutationDefinition.isCompatibleWithSchema(schema.analysisSchema)) {
            return MutationRunResult(emptyList(), issues = listOf(MutationRunIssue.IncompatibleMutation))
        }

        var currentDocument = applicationTarget.documentWithResolution
        val originalSourceIdentifier = currentDocument.document.sourceIdentifier
        val mutationSequence = mutationDefinition.defineModelMutationSequence(schema.analysisSchema)

        var hasIssuesInSteps = false

        val stepResults = buildList {
            mutationSequence.map { modelMutation ->
                val plan = modelMutationPlanner.planModelMutation(
                    schema.analysisSchema,
                    currentDocument,
                    modelMutation,
                    mutationArguments
                )

                if (plan.modelMutationIssues.isNotEmpty()) {
                    add(ModelMutationStepResult.ModelMutationFailed(currentDocument, modelMutation, plan.modelMutationIssues))
                    hasIssuesInSteps = true
                } else {
                    val documentMutations = plan.documentMutations
                    val textMutation = documentToTextMutationPlanner.planDocumentMutations(currentDocument.document, documentMutations)

                    // ignore document-to-text mutation failures for now?

                    val newText = textMutation.newText
                    val newLanguageTree = DefaultLanguageTreeBuilder().build(parse(newText), SourceIdentifier(originalSourceIdentifier.fileIdentifier + "-mutated"))
                    val newDocument = documentWithResolution(schema.analysisSchema, newLanguageTree, schema.operationGenerationId, schema.analysisStatementFilter)
                    add(
                        ModelMutationStepResult.ModelMutationStepApplied(
                            currentDocument,
                            modelMutation,
                            plan,
                            textMutation,
                            newDocument
                        )
                    )
                    currentDocument = newDocument
                }
            }
        }
        return MutationRunResult(stepResults = stepResults, issues = if (hasIssuesInSteps) listOf(MutationRunIssue.HasStepFailure) else emptyList())
    }
}


data class TextMutationApplicationTarget(
    val documentWithResolution: DocumentWithResolution,
    val documentEvaluationSchema: EvaluationSchema
)


data class MutationRunResult(
    val stepResults: List<ModelMutationStepResult>,
    val issues: List<MutationRunIssue>
)

sealed interface MutationRunIssue {
    data object IncompatibleMutation : MutationRunIssue
    data object HasStepFailure : MutationRunIssue
}


sealed interface ModelMutationStepResult {
    val resolvedDocumentBeforeMutation: DocumentWithResolution
    val modelMutation: ModelMutationRequest

    data class ModelMutationFailed(
        override val resolvedDocumentBeforeMutation: DocumentWithResolution,
        override val modelMutation: ModelMutationRequest,
        val issues: List<ModelMutationIssue>
    ) : ModelMutationStepResult

    data class ModelMutationStepApplied(
        override val resolvedDocumentBeforeMutation: DocumentWithResolution,
        override val modelMutation: ModelMutationRequest,
        val modelMutationPlan: ModelMutationPlan,
        val documentTextMutationPlan: DocumentTextMutationPlan,
        val resolvedDocumentAfterMutation: DocumentWithResolution
    ) : ModelMutationStepResult {
        val newDocumentText: String
            get() = documentTextMutationPlan.newText
    }
}
