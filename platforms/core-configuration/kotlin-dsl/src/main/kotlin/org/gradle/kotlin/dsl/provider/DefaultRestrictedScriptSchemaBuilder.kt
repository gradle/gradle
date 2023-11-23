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

import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.schemaFromTypes
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.execution.PluginDependencySpecWithProperties
import org.gradle.kotlin.dsl.execution.PluginsTopLevelReceiver
import org.gradle.kotlin.dsl.execution.RestrictedPluginDependenciesSpecScope
import org.gradle.kotlin.dsl.provider.ScriptSchemaBuildingResult.SchemaAvailable

internal
class DefaultRestrictedScriptSchemaBuilder : RestrictedScriptSchemaBuilder {
    override fun getAnalysisSchemaForScript(
        targetInstance: Any,
        scriptContext: RestrictedScriptContext
    ): ScriptSchemaBuildingResult =
        when (scriptContext) {
            is RestrictedScriptContext.SettingsScript -> SchemaAvailable(schemaForSettingsScript)
            RestrictedScriptContext.PluginsBlock -> SchemaAvailable(schemaForPluginsBlock)
            is RestrictedScriptContext.UnknownScript -> ScriptSchemaBuildingResult.SchemaNotBuilt
        }

    private val schemaForSettingsScript by lazy {
        schemaFromTypes(
            Settings::class,
            listOf(Settings::class, ProjectDescriptor::class)
        )
    }

    private val schemaForPluginsBlock by lazy {
        schemaFromTypes(
            PluginsTopLevelReceiver::class,
            listOf(PluginsTopLevelReceiver::class, RestrictedPluginDependenciesSpecScope::class, PluginDependencySpecWithProperties::class)
        )
    }
}
