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
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepRunner
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult
import org.gradle.internal.declarativedsl.evaluator.schema.RestrictedScriptContext
import org.gradle.internal.declarativedsl.evaluator.conventions.ConventionApplicationHandler
import org.gradle.internal.declarativedsl.evaluator.conventions.ConventionDefinitionCollector
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepResult
import java.io.File


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
    val conventionStorage = ConventionStorage()

    private
    val analysisContext = AnalysisStepContext(
        supportedDocumentChecks = emptyList(), // TODO: move the settings blocks check here,
        supportedResolutionResultHandlers = listOf(ConventionDefinitionCollector(conventionStorage), ConventionApplicationHandler(conventionStorage))
    )

    fun evaluate(
        scriptSource: String,
        scriptFileName: String
    ): Map<String, EvaluationResult<AnalysisStepResult>> {
        val scriptContext = scriptContextFromFileName(scriptFileName)
        return when (val built = schemaBuilder.getEvaluationSchemaForScript(scriptContext)) {
            InterpretationSchemaBuildingResult.SchemaNotBuilt -> emptyMap()
            is InterpretationSchemaBuildingResult.InterpretationSequenceAvailable -> {
                built.sequence.steps.associate {
                    it.stepIdentifier to stepRunner.runInterpretationSequenceStep(scriptFileName, scriptSource, it, analysisContext)
                }
            }
        }
    }

    private
    fun scriptContextFromFileName(fileName: String) = when (File(fileName).name) {
        "build.gradle.dcl" -> RestrictedScriptContext.ProjectScript
        "settings.gradle.dcl" -> object : RestrictedScriptContext.SettingsScript {}
        else -> RestrictedScriptContext.UnknownScript
    }
}
