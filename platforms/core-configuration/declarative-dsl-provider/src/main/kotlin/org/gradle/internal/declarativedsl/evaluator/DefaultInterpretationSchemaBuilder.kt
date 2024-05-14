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
import org.gradle.internal.declarativedsl.evaluator.InterpretationSchemaBuildingResult.InterpretationSequenceAvailable
import org.gradle.internal.declarativedsl.evaluator.InterpretationSchemaBuildingResult.SchemaNotBuilt
import org.gradle.internal.declarativedsl.project.projectInterpretationSequence
import org.gradle.internal.declarativedsl.settings.settingsInterpretationSequence
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


internal
class DefaultInterpretationSchemaBuilder(
    private val softwareTypeRegistry: SoftwareTypeRegistry
) : InterpretationSchemaBuilder {
    override fun getEvaluationSchemaForScript(
        targetInstance: Any,
        scriptContext: RestrictedScriptContext,
    ): InterpretationSchemaBuildingResult =
        when (scriptContext) {
            is RestrictedScriptContext.UnknownScript -> SchemaNotBuilt

            is RestrictedScriptContext.SettingsScript -> InterpretationSequenceAvailable(
                settingsInterpretationSequence(targetInstance as SettingsInternal, scriptContext.targetScope, scriptContext.scriptSource)
            )

            is RestrictedScriptContext.ProjectScript -> InterpretationSequenceAvailable(projectInterpretationSequence)
        }

    private
    val projectInterpretationSequence by lazy { projectInterpretationSequence(softwareTypeRegistry) }
}
