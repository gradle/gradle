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

package org.gradle.internal.declarativedsl.evaluator

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.declarative.dsl.evaluation.InterpretationSequence
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.defaults.softwareTypeRegistryBasedModelDefaultsRegistrar
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated.StageFailure.NoSchemaAvailable
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheck
import org.gradle.internal.declarativedsl.evaluator.defaults.ApplyModelDefaultsHandler
import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsDefinitionCollector
import org.gradle.internal.declarativedsl.evaluator.conversion.AnalysisAndConversionStepRunner
import org.gradle.internal.declarativedsl.evaluator.conversion.ConversionStepContext
import org.gradle.internal.declarativedsl.evaluator.conversion.ConversionStepResult
import org.gradle.internal.declarativedsl.evaluator.features.ResolutionResultHandler
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepRunner
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult
import org.gradle.internal.declarativedsl.evaluator.schema.DeclarativeScriptContext
import org.gradle.internal.declarativedsl.settings.SettingsBlocksCheck
import org.gradle.internal.declarativedsl.common.UnsupportedSyntaxFeatureCheck
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


interface DeclarativeKotlinScriptEvaluator {
    fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        targetScope: ClassLoaderScope,
    ): EvaluationResult<*>
}


internal
fun defaultDeclarativeScriptEvaluator(
    schemaBuilder: InterpretationSchemaBuilder,
    softwareTypeRegistry: SoftwareTypeRegistry
): DeclarativeKotlinScriptEvaluator = DefaultDeclarativeKotlinScriptEvaluator(
    schemaBuilder,
    documentChecks = setOf(SettingsBlocksCheck, UnsupportedSyntaxFeatureCheck),
    resolutionResultHandlers = setOf(
        ApplyModelDefaultsHandler.DO_NOTHING,
        ModelDefaultsDefinitionCollector(softwareTypeRegistryBasedModelDefaultsRegistrar(softwareTypeRegistry))
    )
)


internal
class DefaultDeclarativeKotlinScriptEvaluator(
    private val schemaBuilder: InterpretationSchemaBuilder,
    documentChecks: Iterable<DocumentCheck>,
    resolutionResultHandlers: Iterable<ResolutionResultHandler>
) : DeclarativeKotlinScriptEvaluator {

    private
    val stepRunner = AnalysisAndConversionStepRunner(AnalysisStepRunner())

    private
    val defaultAnalysisContext = AnalysisStepContext(documentChecks, resolutionResultHandlers)

    override fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        targetScope: ClassLoaderScope
    ): EvaluationResult<ConversionStepResult> {
        val scriptContext = scriptContextFor(target)
        return when (val built = schemaBuilder.getEvaluationSchemaForScript(scriptContext)) {
            InterpretationSchemaBuildingResult.SchemaNotBuilt -> NotEvaluated(listOf(NoSchemaAvailable(scriptContext)), ConversionStepResult.CannotRunStep)
            is InterpretationSchemaBuildingResult.InterpretationSequenceAvailable -> runInterpretationSequence(scriptSource, built.sequence, target)
        }
    }

    private
    fun runInterpretationSequence(
        scriptSource: ScriptSource,
        sequence: InterpretationSequence,
        target: Any
    ): EvaluationResult<ConversionStepResult> =
        sequence.steps.map { step ->
            stepRunner.runInterpretationSequenceStep(scriptSource.fileName, scriptSource.resource.text, step, ConversionStepContext(target, defaultAnalysisContext))
                .also { if (it is NotEvaluated) return it }
        }.lastOrNull() ?: NotEvaluated(stageFailures = emptyList(), partialStepResult = ConversionStepResult.CannotRunStep)

    private
    fun scriptContextFor(target: Any): DeclarativeScriptContext = when (target) {
        is Settings -> DeclarativeScriptContext.SettingsScript
        is Project -> DeclarativeScriptContext.ProjectScript
        else -> DeclarativeScriptContext.UnknownScript
    }
}
