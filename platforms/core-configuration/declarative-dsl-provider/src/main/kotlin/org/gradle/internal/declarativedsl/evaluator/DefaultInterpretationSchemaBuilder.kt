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

import org.gradle.internal.declarativedsl.schemaBuilder.kotlinFunctionAsConfigureLambda
import org.gradle.internal.declarativedsl.schemaBuilder.plus
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaBuilder.treatInterfaceAsConfigureLambda
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequence
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.evaluator.InterpretationSchemaBuildingResult.InterpretationSequenceAvailable
import org.gradle.internal.declarativedsl.evaluator.InterpretationSchemaBuildingResult.SchemaNotBuilt
import org.gradle.internal.declarativedsl.plugins.schemaForPluginsBlock
import org.gradle.internal.declarativedsl.project.projectInterpretationSequence
import org.gradle.plugin.management.PluginManagementSpec


internal
class DefaultInterpretationSchemaBuilder : InterpretationSchemaBuilder {
    override fun getEvaluationSchemaForScript(
        targetInstance: Any,
        scriptContext: RestrictedScriptContext,
    ): InterpretationSchemaBuildingResult =
        when (scriptContext) {
            is RestrictedScriptContext.UnknownScript -> SchemaNotBuilt
            RestrictedScriptContext.PluginsBlock -> simpleInterpretation("plugins", EvaluationSchema(schemaForPluginsBlock), targetInstance)
            is RestrictedScriptContext.SettingsScript -> simpleInterpretation("settings", EvaluationSchema(schemaForSettingsScript), targetInstance)
            is RestrictedScriptContext.ProjectScript ->
                InterpretationSequenceAvailable(projectInterpretationSequence(targetInstance as ProjectInternal, scriptContext.targetScope, scriptContext.scriptSource))
        }

    private
    val schemaForSettingsScript by lazy {
        schemaFromTypes(
            Settings::class,
            listOf(
                Settings::class,
                ProjectDescriptor::class,
                Action::class,
                PluginManagementSpec::class,
                DependencyResolutionManagement::class,
                RepositoryHandler::class,
                MavenArtifactRepository::class,
                ArtifactRepository::class
            ),
            configureLambdas = treatInterfaceAsConfigureLambda(Action::class).plus(kotlinFunctionAsConfigureLambda)
        )
    }

    private
    fun simpleInterpretation(id: String, schema: EvaluationSchema, target: Any) =
        InterpretationSequenceAvailable(
            InterpretationSequence(
                listOf(object : InterpretationSequenceStep<Any> {
                    override val stepIdentifier: String = id
                    override fun evaluationSchemaForStep(): EvaluationSchema = schema
                    override fun topLevelReceiver(): Any = target
                    override fun whenEvaluated(resultReceiver: Any) = Unit
                })
            )
        )
}
