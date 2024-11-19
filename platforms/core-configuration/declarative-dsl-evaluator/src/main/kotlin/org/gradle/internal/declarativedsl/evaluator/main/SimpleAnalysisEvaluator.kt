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

import org.gradle.declarative.dsl.evaluation.InterpretationSequence
import org.gradle.declarative.dsl.evaluation.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.evaluator.defaults.ApplyModelDefaultsHandler
import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsDefinitionCollector
import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsRepository
import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsResolutionResults
import org.gradle.internal.declarativedsl.evaluator.defaults.defaultsForAllUsedSoftwareTypes
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepResult
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepRunner
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult
import org.gradle.internal.declarativedsl.evaluator.schema.DeclarativeScriptContext
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult
import java.io.File


class AnalysisSequenceResult(
    val stepResults: Map<InterpretationSequenceStep, EvaluationResult<AnalysisStepResult>>
)


class SimpleAnalysisEvaluator(
    val schemaBuilder: InterpretationSchemaBuilder
) {
    companion object {
        fun withSchema(settings: InterpretationSequence, project: InterpretationSequence): SimpleAnalysisEvaluator =
            SimpleAnalysisEvaluator(PrebuiltInterpretationSchemaBuilder(settings, project))
    }

    private
    val stepRunner = AnalysisStepRunner()

    private
    val modelDefaultsStorage = ModelDefaultsStorage()

    private
    val analysisContext = AnalysisStepContext(
        supportedDocumentChecks = emptyList(), // TODO: move the settings blocks check here,
        supportedResolutionResultHandlers = listOf(
            ModelDefaultsDefinitionCollector(modelDefaultsStorage),
            // Note that for this evaluator, which only analyzes the script (but does not apply conversion), we add the model defaults
            // to the resolution result so that they are visible to clients after analysis.  We instead do this application as part
            // of the conversion step runner (during plugin application) for normal script evaluation.
            AllSoftwareTypesApplyModelDefaultsHandler(modelDefaultsStorage)
        )
    )

    fun evaluate(
        scriptFileName: String,
        scriptSource: String
    ): AnalysisSequenceResult {
        val scriptContext = scriptContextFromFileName(scriptFileName)
        return when (val built = schemaBuilder.getEvaluationSchemaForScript(scriptContext)) {
            InterpretationSchemaBuildingResult.SchemaNotBuilt -> AnalysisSequenceResult(emptyMap())
            is InterpretationSchemaBuildingResult.InterpretationSequenceAvailable -> AnalysisSequenceResult(
                built.sequence.steps.associateWith {
                    stepRunner.runInterpretationSequenceStep(scriptFileName, scriptSource, it, analysisContext)
                }
            )
        }
    }

    private
    fun scriptContextFromFileName(fileName: String) = when (File(fileName).name) {
        "build.gradle.dcl", "build.gradle.kts" -> DeclarativeScriptContext.ProjectScript
        "settings.gradle.dcl", "settings.gradle.kts" -> DeclarativeScriptContext.SettingsScript
        else -> DeclarativeScriptContext.UnknownScript
    }
}


private
class AllSoftwareTypesApplyModelDefaultsHandler(val modelDefaultsRepository: ModelDefaultsRepository) : ApplyModelDefaultsHandler {
    override fun getDefaultsResolutionResults(resolutionResult: ResolutionResult): List<ModelDefaultsResolutionResults> =
        defaultsForAllUsedSoftwareTypes(modelDefaultsRepository, resolutionResult)
}
