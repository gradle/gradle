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

package org.gradle.internal.restricteddsl.evaluator

import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.kotlinFunctionAsConfigureLambda
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.plus
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.schemaFromTypes
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.treatInterfaceAsConfigureLambda
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.restricteddsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.restricteddsl.evaluationSchema.simpleInterpretationWith
import org.gradle.internal.restricteddsl.evaluator.InterpretationSchemaBuildingResult.InterpretationSequenceAvailable
import org.gradle.internal.restricteddsl.plugins.schemaForPluginsBlock
import org.gradle.internal.restricteddsl.project.projectInterpretationSequence
import org.gradle.plugin.management.PluginManagementSpec


internal
class DefaultInterpretationSchemaBuilder : InterpretationSchemaBuilder {
    override fun getEvaluationSchemaForScript(
        targetInstance: Any,
        scriptContext: RestrictedScriptContext,
    ): InterpretationSchemaBuildingResult =
        when (scriptContext) {
            is RestrictedScriptContext.SettingsScript ->
                InterpretationSequenceAvailable(simpleInterpretationWith(EvaluationSchema(schemaForSettingsScript), targetInstance))

            is RestrictedScriptContext.ProjectScript ->
                InterpretationSequenceAvailable(projectInterpretationSequence(targetInstance as ProjectInternal, scriptContext.targetScope, scriptContext.scriptSource))

            RestrictedScriptContext.PluginsBlock ->
                InterpretationSequenceAvailable(simpleInterpretationWith(EvaluationSchema(schemaForPluginsBlock), targetInstance))

            is RestrictedScriptContext.UnknownScript ->
                InterpretationSchemaBuildingResult.SchemaNotBuilt
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
}
