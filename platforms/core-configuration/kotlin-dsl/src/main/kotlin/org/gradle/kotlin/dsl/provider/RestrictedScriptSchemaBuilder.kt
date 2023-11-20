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

package org.gradle.kotlin.dsl.provider

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import org.gradle.api.internal.initialization.ClassLoaderScope

internal
interface RestrictedScriptSchemaBuilder {
    fun getAnalysisSchemaForScript(
        targetInstance: Any,
        classLoaderScope: ClassLoaderScope,
        scriptContext: RestrictedScriptContext
    ) : ScriptSchemaBuildingResult
}

internal
sealed interface ScriptSchemaBuildingResult {
    class SchemaAvailable(val schema: AnalysisSchema) : ScriptSchemaBuildingResult
    object SchemaNotBuilt : ScriptSchemaBuildingResult
}

internal
sealed interface RestrictedScriptContext {
    object SettingsScript : RestrictedScriptContext
    object UnknownScript : RestrictedScriptContext
}
