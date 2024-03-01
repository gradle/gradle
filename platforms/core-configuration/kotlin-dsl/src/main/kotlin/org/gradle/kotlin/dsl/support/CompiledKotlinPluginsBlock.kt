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

package org.gradle.kotlin.dsl.support

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.use.PluginDependenciesSpec


/**
 * Base class for `plugins` block evaluation for any target.
 */
open class CompiledKotlinPluginsBlock(
    private val host: KotlinScriptHost<ExtensionAware>,
    private val pluginDependencies: PluginDependenciesSpec,
) {

    fun plugins(configuration: PluginDependenciesSpecScope.() -> Unit) {
        PluginDependenciesSpecScopeInternal(host.objectFactory, pluginDependencies).configuration()
    }
}


/**
 * Base class for the evaluation of a `pluginManagement` block followed by a
 * `buildscript` block followed by a `plugins` block.
 *
 * @constructor Must match the constructor of the [CompiledKotlinBuildscriptAndPluginsBlock] the object!
 */
@ImplicitReceiver(Settings::class)
open class CompiledKotlinSettingsPluginManagementBlock(
    host: KotlinScriptHost<Settings>,
    private val pluginDependencies: PluginDependenciesSpec
) : CompiledKotlinSettingsScript(host) {

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    open fun buildscript(block: ScriptHandlerScope.() -> Unit) {
        ScriptHandlerScope(buildscript).block()
    }

    open fun plugins(configuration: PluginDependenciesSpecScope.() -> Unit) {
        PluginDependenciesSpecScope(pluginDependencies).configuration()
    }
}


/**
 * Base class for the evaluation of a `buildscript` block followed by a `plugins` block.
 *
 * @constructor Must match the constructor of the [CompiledKotlinSettingsPluginManagementBlock] object!
 */
@ImplicitReceiver(Project::class)
open class CompiledKotlinBuildscriptAndPluginsBlock(
    private val host: KotlinScriptHost<Project>,
    private val pluginDependencies: PluginDependenciesSpec
) : CompiledKotlinBuildScript(host) {

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    override fun buildscript(block: ScriptHandlerScope.() -> Unit) {
        ScriptHandlerScopeInternal(host.target, buildscript).block()
    }

    override fun plugins(block: PluginDependenciesSpecScope.() -> Unit) {
        PluginDependenciesSpecScopeInternal(host.objectFactory, pluginDependencies).block()
    }
}
