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

package org.gradle.declarative.dsl.tooling.builders

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.declarative.dsl.evaluation.InterpretationSequence
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel
import org.gradle.internal.build.BuildState
import org.gradle.internal.declarativedsl.evaluationSchema.DefaultInterpretationSequence
import org.gradle.internal.declarativedsl.evaluationSchema.SimpleInterpretationSequenceStep
import org.gradle.internal.declarativedsl.evaluator.GradleProcessInterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.LoadedSettingsScriptContext
import org.gradle.internal.declarativedsl.evaluator.schema.DefaultEvaluationSchema
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult
import org.gradle.internal.declarativedsl.evaluator.schema.DeclarativeScriptContext
import org.gradle.plugin.software.internal.SoftwareTypeRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import java.io.Serializable


class DeclarativeSchemaModelBuilder(private val softwareTypeRegistry: SoftwareTypeRegistry) : ToolingModelBuilder, BuildScopeModelBuilder {

    override fun create(target: BuildState): Any {
        // Make sure the project tree has been loaded and can be queried (but not necessarily configured)
        target.ensureProjectsLoaded()

        val schemaBuilder = GradleProcessInterpretationSchemaBuilder(softwareTypeRegistry)

        val settings = target.mutableModel.settings
        val settingsContext = LoadedSettingsScriptContext(settings, settings.classLoaderScope, settings.settingsScript)

        val settingsSequence = schemaBuilder.getEvaluationSchemaForScript(settingsContext)
            .sequenceOrError().analysisOnly()
        val projectSequence = schemaBuilder.getEvaluationSchemaForScript(DeclarativeScriptContext.ProjectScript)
            .sequenceOrError().analysisOnly()

        return DefaultDeclarativeSchemaModel(settingsSequence, projectSequence)
    }

    private
    fun InterpretationSchemaBuildingResult.sequenceOrError() = when (this) {
        is InterpretationSchemaBuildingResult.InterpretationSequenceAvailable -> sequence
        // This is rather an unexpected case, should not happen in normal operation.
        InterpretationSchemaBuildingResult.SchemaNotBuilt -> throw GradleException("schema not available")
    }

    private
    fun InterpretationSequence.analysisOnly(): InterpretationSequence = DefaultInterpretationSequence(
        steps.map { step ->
            val evaluationSchema = step.evaluationSchemaForStep
            val analysisOnlySchema = DefaultEvaluationSchema(evaluationSchema.analysisSchema, evaluationSchema.analysisStatementFilter)
            SimpleInterpretationSequenceStep(step.stepIdentifier, step.features) { analysisOnlySchema }
        }
    )

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel"

    override fun buildAll(modelName: String, project: Project): Any {
        error("Model should be built before the configuration phase")
    }
}


private
class DefaultDeclarativeSchemaModel(
    private val settingsSequence: InterpretationSequence,
    private val projectSequence: InterpretationSequence
) : DeclarativeSchemaModel, Serializable {

    override fun getSettingsSequence(): InterpretationSequence = settingsSequence

    override fun getProjectSequence(): InterpretationSequence = projectSequence

    override fun getProjectSchema(): AnalysisSchema {
        return projectSequence.steps.single().evaluationSchemaForStep.analysisSchema
    }
}
