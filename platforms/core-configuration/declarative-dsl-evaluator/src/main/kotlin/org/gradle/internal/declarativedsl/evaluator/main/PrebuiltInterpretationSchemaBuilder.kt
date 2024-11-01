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
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult
import org.gradle.internal.declarativedsl.evaluator.schema.DeclarativeScriptContext


internal
class PrebuiltInterpretationSchemaBuilder(
    private val settingsSequence: InterpretationSequence,
    private val projectSequence: InterpretationSequence
) : InterpretationSchemaBuilder {
    override fun getEvaluationSchemaForScript(scriptContext: DeclarativeScriptContext): InterpretationSchemaBuildingResult = when (scriptContext) {
        DeclarativeScriptContext.ProjectScript -> InterpretationSchemaBuildingResult.InterpretationSequenceAvailable(projectSequence)
        DeclarativeScriptContext.SettingsScript -> InterpretationSchemaBuildingResult.InterpretationSequenceAvailable(settingsSequence)
        DeclarativeScriptContext.UnknownScript -> InterpretationSchemaBuildingResult.SchemaNotBuilt
    }
}
