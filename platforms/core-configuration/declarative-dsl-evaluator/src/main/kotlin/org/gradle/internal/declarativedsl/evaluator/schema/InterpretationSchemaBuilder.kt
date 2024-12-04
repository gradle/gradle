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

package org.gradle.internal.declarativedsl.evaluator.schema

import org.gradle.declarative.dsl.evaluation.InterpretationSequence


interface InterpretationSchemaBuilder {
    fun getEvaluationSchemaForScript(
        scriptContext: DeclarativeScriptContext,
    ): InterpretationSchemaBuildingResult
}


sealed interface InterpretationSchemaBuildingResult {
    class InterpretationSequenceAvailable(val sequence: InterpretationSequence) : InterpretationSchemaBuildingResult
    data object SchemaNotBuilt : InterpretationSchemaBuildingResult
}


sealed interface DeclarativeScriptContext {
    data object SettingsScript : DeclarativeScriptContext

    data object ProjectScript : DeclarativeScriptContext

    data object UnknownScript : DeclarativeScriptContext
}
