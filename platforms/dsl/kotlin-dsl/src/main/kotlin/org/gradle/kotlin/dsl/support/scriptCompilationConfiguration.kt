/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.support

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.kotlin.dsl.provider.PrecompiledScriptsEnvironment.EnvironmentProperties
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.util.PropertiesCollection

object PluginsBlockCompilationConfiguration : ScriptCompilationConfiguration(
    {
        implicitReceivers(emptyList())
        defaultImportsForScript()
    })

object InitScriptCompilationConfiguration : ScriptCompilationConfiguration(
    {
        implicitReceivers(Gradle::class)
        defaultImportsForScript()
    })

object SettingsScriptCompilationConfiguration : ScriptCompilationConfiguration(
    {
        implicitReceivers(Settings::class)
        defaultImportsForScript()
    })

object BuildScriptCompilationConfiguration : ScriptCompilationConfiguration(
    {
        implicitReceivers(Project::class)
        defaultImportsForScript()
    })


fun ScriptCompilationConfiguration.Builder.defaultImportsForScript() {
    refineConfiguration {
        beforeCompiling { context ->
            val environment = scriptResolverEnvironmentOf(context)
            require(environment != null)
            context.compilationConfiguration.with {
                val v = implicitImportsFrom(environment)
                defaultImports(v)
            }.asSuccess()
        }
    }
}


// Looked up by name, because `getRefinementEnvironment` is only available since Kotlin 2.5,
// and `getEnvironment` is deprecated.
internal
val refinementEnvironmentKey = environmentKey("getRefinementEnvironment")

internal
val legacyEnvironmentKey = environmentKey("getEnvironment")

private
fun environmentKey(name: String) = PropertiesCollection.Key<() -> Map<String, Any?>?>(name)

internal
fun scriptResolverEnvironmentOf(context: ScriptConfigurationRefinementContext): Map<String, Any?>? =
    context.compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]?.let { hostConfiguration ->
        listOf(refinementEnvironmentKey, legacyEnvironmentKey).firstNotNullOfOrNull { hostConfiguration[it]?.invoke() }
    }

internal
fun implicitImportsFrom(environment: Map<String, Any?>?) =
    environment.stringList(EnvironmentProperties.kotlinDslImplicitImports)

internal
fun Map<String, Any?>?.stringList(key: String) =
    string(key)?.split(':')
        ?: emptyList()

private
fun Map<String, Any?>?.string(key: String) =
    this?.get(key) as? String