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
import org.gradle.internal.declarativedsl.evaluator.schema.DeclarativeScriptContext
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult.InterpretationSequenceAvailable
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult.SchemaNotBuilt
import org.gradle.internal.declarativedsl.project.projectInterpretationSequence
import org.gradle.internal.declarativedsl.settings.settingsInterpretationSequence
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


class GradleProcessInterpretationSchemaBuilder(
    /** Accessed lazily, as there is no valid [SettingsInternal] reference to be injected when this service is created. */
    private val getSettings: () -> SettingsInternal,
    private val softwareTypeRegistry: SoftwareTypeRegistry,
) : InterpretationSchemaBuilder {
    override fun getEvaluationSchemaForScript(scriptContext: DeclarativeScriptContext): InterpretationSchemaBuildingResult =
        when (scriptContext) {
            is DeclarativeScriptContext.UnknownScript -> SchemaNotBuilt

            DeclarativeScriptContext.SettingsScript -> InterpretationSequenceAvailable(settingsInterpretationSequence(getSettings(), softwareTypeRegistry))

            DeclarativeScriptContext.ProjectScript -> InterpretationSequenceAvailable(projectInterpretationSequence(softwareTypeRegistry))
        }
}
