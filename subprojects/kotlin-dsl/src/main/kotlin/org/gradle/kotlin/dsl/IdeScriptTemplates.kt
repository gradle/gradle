/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl


import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.plugins.PluginAware
import org.gradle.kotlin.dsl.support.CompiledKotlinSettingsScript
import org.gradle.kotlin.dsl.support.DefaultKotlinScript
import org.gradle.kotlin.dsl.support.KotlinBuildScriptCompilationConfiguration
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.KotlinSettingsScriptCompilationConfiguration
import org.gradle.kotlin.dsl.support.defaultKotlinScriptHostForProject
import org.gradle.kotlin.dsl.support.internalError
import org.gradle.kotlin.dsl.support.invalidPluginsCall
import org.gradle.plugin.use.PluginDependenciesSpec
import kotlin.script.experimental.annotations.KotlinScript


/**
 * Base class for Kotlin build scripts.
 *
 * @since 6.0
 */
@GradleDsl
@KotlinScript(
    displayName = "Gradle Project Script",
    fileExtension = "gradle.kts",
    filePathPattern = ".*\\.gradle\\.kts",
    compilationConfiguration = KotlinBuildScriptCompilationConfiguration::class
)
@Incubating
open class KotlinBuildScript(
    private val host: KotlinScriptHost<Project>
) : DefaultKotlinScript(defaultKotlinScriptHostForProject(host.target)), PluginAware by host.target {

    /**
     * The [ScriptHandler] for this script.
     */
    val buildscript: ScriptHandler
        get() = host.scriptHandler

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    open fun buildscript(@Suppress("unused_parameter") block: ScriptHandlerScope.() -> Unit): Unit =
        internalError()

    /**
     * Configures the plugin dependencies for this project.
     *
     * @see [PluginDependenciesSpec]
     */
    open fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpec.() -> Unit): Unit =
        invalidPluginsCall()
}


/**
 * Base class for Kotlin settings scripts.
 *
 * @since 6.0
 */
@GradleDsl
@KotlinScript(
    displayName = "Gradle Settings Script",
    fileExtension = "gradle.kts",
    filePathPattern = "^.*/(settings|.+\\.settings)\\.gradle\\.kts$",
    compilationConfiguration = KotlinSettingsScriptCompilationConfiguration::class
)
@Incubating
abstract class KotlinSettingsScript(
    host: KotlinScriptHost<Settings>
) : CompiledKotlinSettingsScript(host) {

    /**
     * Configures the plugin dependencies for the project's settings.
     *
     * @see [PluginDependenciesSpec]
     * @since 6.0
     */
    @Incubating
    @Suppress("unused")
    open fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpecScope.() -> Unit): Unit =
        invalidPluginsCall()
}
