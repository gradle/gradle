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
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import kotlin.script.dependencies.Environment
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptingHostConfiguration

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
    fun scriptResolverEnvironmentOf(context: ScriptConfigurationRefinementContext): Environment? =
        context
            .compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
            ?.get(ScriptingHostConfiguration.getEnvironment)
            ?.invoke()

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


internal
fun implicitImportsFrom(environment: Environment?) =
    environment.stringList(EnvironmentProperties.kotlinDslImplicitImports)

internal
fun Environment?.stringList(key: String) =
    string(key)?.split(':')
        ?: emptyList()

private
fun Environment?.string(key: String) =
    this?.get(key) as? String