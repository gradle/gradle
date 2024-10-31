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

package org.gradle.internal.declarativedsl.evaluator

import org.gradle.internal.declarativedsl.evaluator.schema.DeclarativeScriptContext
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult

/**
 * Using the [delegate], builds and stores just one interpretation sequence for [DeclarativeScriptContext.ProjectScript] and just one
 * for [DeclarativeScriptContext.SettingsScript]. On further invocations with the same kinds of contexts, returns the previously built values.
 */
class MemoizedInterpretationSchemaBuilder(private val delegate: InterpretationSchemaBuilder) : InterpretationSchemaBuilder {

    var settingsSchema: InterpretationSchemaBuildingResult? = null
    var projectSchema: InterpretationSchemaBuildingResult? = null

    override fun getEvaluationSchemaForScript(scriptContext: DeclarativeScriptContext): InterpretationSchemaBuildingResult {
        val schemaStoringProperty = when (scriptContext) {
            DeclarativeScriptContext.ProjectScript -> ::projectSchema
            DeclarativeScriptContext.SettingsScript -> ::settingsSchema
            DeclarativeScriptContext.UnknownScript -> null
        }

        return schemaStoringProperty?.let { property ->
            property() ?: delegate.getEvaluationSchemaForScript(scriptContext).also { property.set(it) }
        } ?: InterpretationSchemaBuildingResult.SchemaNotBuilt
    }
}
