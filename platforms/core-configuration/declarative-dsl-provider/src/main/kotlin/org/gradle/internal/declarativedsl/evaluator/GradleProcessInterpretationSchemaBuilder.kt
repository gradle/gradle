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

import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult.InterpretationSequenceAvailable
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult.SchemaNotBuilt
import org.gradle.internal.declarativedsl.evaluator.schema.RestrictedScriptContext
import org.gradle.internal.declarativedsl.project.projectInterpretationSequence
import org.gradle.internal.declarativedsl.settings.settingsInterpretationSequence
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


class GradleProcessInterpretationSchemaBuilder(
    private val softwareTypeRegistry: SoftwareTypeRegistry
) : InterpretationSchemaBuilder {
    override fun getEvaluationSchemaForScript(scriptContext: RestrictedScriptContext): InterpretationSchemaBuildingResult =
        when (scriptContext) {
            is RestrictedScriptContext.UnknownScript -> SchemaNotBuilt

            is RestrictedScriptContext.SettingsScript -> {
                require(scriptContext is LoadedSettingsScriptContext) { "A ${LoadedSettingsScriptContext::class.simpleName} is needed to build the settings schema" }
                InterpretationSequenceAvailable(
                    settingsInterpretationSequence(scriptContext.settings, scriptContext.targetScope, scriptContext.scriptSource, softwareTypeRegistry)
                )
            }

            is RestrictedScriptContext.ProjectScript -> InterpretationSequenceAvailable(projectInterpretationSequence)
        }

    private
    val projectInterpretationSequence by lazy { projectInterpretationSequence(softwareTypeRegistry) }
}


data class LoadedSettingsScriptContext(
    val settings: SettingsInternal,
    val targetScope: ClassLoaderScope,
    val scriptSource: ScriptSource
) : RestrictedScriptContext.SettingsScript
