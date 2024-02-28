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

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequence


internal
interface InterpretationSchemaBuilder {
    fun getEvaluationSchemaForScript(
        targetInstance: Any,
        scriptContext: RestrictedScriptContext,
    ): InterpretationSchemaBuildingResult
}


internal
sealed interface InterpretationSchemaBuildingResult {
    class InterpretationSequenceAvailable(val sequence: InterpretationSequence) : InterpretationSchemaBuildingResult
    object SchemaNotBuilt : InterpretationSchemaBuildingResult
}


internal
sealed interface RestrictedScriptContext {
    sealed interface ScriptDependentContext : RestrictedScriptContext {
        val targetScope: ClassLoaderScope
        val scriptSource: ScriptSource
    }

    object SettingsScript : RestrictedScriptContext
    object PluginsBlock : RestrictedScriptContext

    class ProjectScript(
        override val targetScope: ClassLoaderScope,
        override val scriptSource: ScriptSource
    ) : ScriptDependentContext

    object UnknownScript : RestrictedScriptContext
}
