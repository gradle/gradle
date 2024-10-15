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

package org.gradle.internal.declarativedsl.evaluator.defaults

import org.gradle.api.Plugin
import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.ResolutionTrace
import org.gradle.internal.declarativedsl.defaults.softwareTypeRegistryBasedModelDefaultsRepository
import org.gradle.internal.declarativedsl.evaluator.DeclarativeDslNotEvaluatedException
import org.gradle.internal.declarativedsl.evaluator.conversion.AnalysisAndConversionStepRunner
import org.gradle.internal.declarativedsl.evaluator.conversion.ConversionStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.AbstractAnalysisStepRunner
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.Evaluated
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated
import org.gradle.internal.declarativedsl.evaluator.runner.ParseAndResolveResult
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SyntheticallyProduced
import org.gradle.internal.declarativedsl.project.projectInterpretationSequenceStep
import org.gradle.plugin.software.internal.ModelDefaultsHandler
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


/**
 * A {@link ConventionHandler} for applying declarative conventions.
 */
class DeclarativeModelDefaultsHandler(softwareTypeRegistry: SoftwareTypeRegistry) : ModelDefaultsHandler {
    private
    val step = projectInterpretationSequenceStep(softwareTypeRegistry)
    private
    val modelDefaultsRepository = softwareTypeRegistryBasedModelDefaultsRepository(softwareTypeRegistry)

    override fun <T : Any> apply(target: T, softwareTypeName: String, plugin: Plugin<in T>) {
        val analysisStepRunner = ApplyDefaultsOnlyAnalysisStepRunner()
        val analysisStepContext = AnalysisStepContext(
            emptySet(),
            setOf(SingleSoftwareTypeApplyModelDefaultsHandler(modelDefaultsRepository, softwareTypeName))
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
                // We shouldn't get any stage failures here, as we're only applying conventions that have already been analyzed as part of the
                // settings script evaluation.  However, if we do for some reason, we'll throw an exception.
                throw DeclarativeDslNotEvaluatedException("", result.stageFailures)
            }
        }
    }
}


private
class ApplyDefaultsOnlyAnalysisStepRunner : AbstractAnalysisStepRunner() {
    override fun parseAndResolve(evaluationSchema: EvaluationSchema, scriptIdentifier: String, scriptSource: String): ParseAndResolveResult {
        // Create a synthetic top level receiver
        val topLevelBlock = Block(emptyList(), SyntheticallyProduced)
        val languageTreeResult = LanguageTreeResult(emptyList(), topLevelBlock, emptyList(), emptyList())
        val topLevelReceiver = ObjectOrigin.TopLevelReceiver(evaluationSchema.analysisSchema.topLevelReceiverType, topLevelBlock)

        return ParseAndResolveResult(languageTreeResult, emptyResolutionResultForReceiver(topLevelReceiver), emptyResolutionTrace(), emptyList())
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
class SingleSoftwareTypeApplyModelDefaultsHandler(val modelDefaultsRepository: ModelDefaultsRepository, val softwareTypeName: String) : ApplyModelDefaultsHandler {
    override fun getDefaultsResolutionResults(resolutionResult: ResolutionResult): List<ModelDefaultsResolutionResults> {
        return listOf(modelDefaultsRepository.findDefaults(softwareTypeName)).requireNoNulls()
    }
}
