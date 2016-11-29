/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin

import org.gradle.script.lang.kotlin.support.KotlinBuildScriptDependenciesResolver

import org.gradle.api.Project
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.plugin.use.PluginDependenciesSpec

import org.jetbrains.kotlin.script.ScriptTemplateDefinition

@DslMarker
annotation class BuildScriptBlockMarker

/**
 * Base class for Kotlin build scripts.
 */
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
    scriptFilePattern = ".*\\.gradle\\.kts")
@BuildScriptBlockMarker
abstract class KotlinBuildScript(project: Project) : Project by project {

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    @Suppress("unused")
    open fun buildscript(@Suppress("unused_parameter") configuration: KotlinScriptHandler.() -> Unit) = Unit

    /**
     * Configures the plugin dependencies for this project.
     *
     * @see [PluginDependenciesSpec]
     */
    @Suppress("unused")
    fun plugins(@Suppress("unused_parameter") configuration: KotlinPluginDependenciesHandler.() -> Unit) = Unit

    inline fun apply(crossinline configuration: ObjectConfigurationAction.() -> Unit) =
        project.apply({ it.configuration() })
}

