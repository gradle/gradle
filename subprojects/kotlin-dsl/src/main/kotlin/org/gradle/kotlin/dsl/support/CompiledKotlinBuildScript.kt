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

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.invocation.Gradle

import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginAware

import org.gradle.kotlin.dsl.InitScriptApi
import org.gradle.kotlin.dsl.PluginDependenciesSpecScope
import org.gradle.kotlin.dsl.ScriptHandlerScope
import org.gradle.kotlin.dsl.support.delegates.PluginAwareDelegate


@ImplicitReceiver(Project::class)
open class CompiledKotlinBuildScript(
    private val host: KotlinScriptHost<Project>
) {
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
    @Suppress("unused")
    open fun buildscript(@Suppress("unused_parameter") block: ScriptHandlerScope.() -> Unit): Unit =
        internalError()

    /**
     * Configures the plugin dependencies for this project.
     *
     * @see [PluginDependenciesSpec]
     */
    @Suppress("unused")
    fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpecScope.() -> Unit): Unit =
        invalidPluginsCall()

    /**
     * Applies zero or more plugins or scripts.
     * <p>
     * The given action is used to configure an [ObjectConfigurationAction], which “builds” the plugin application.
     * <p>
     * @param action the action to configure an [ObjectConfigurationAction] with before “executing” it
     * @see [PluginAware.apply]
     */
    // Method is only required to disambiguate from the standard `T.apply(T.() -> Unit)` combinator
    fun apply(action: Action<in ObjectConfigurationAction>) =
        host.target.apply(action)
}


/**
 * Base class for `buildscript` block evaluation on scripts targeting Project.
 */
@ImplicitReceiver(Project::class)
abstract class CompiledKotlinBuildscriptBlock(host: KotlinScriptHost<Project>) : CompiledKotlinBuildScript(host) {

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    override fun buildscript(block: ScriptHandlerScope.() -> Unit) {
        buildscript.configureWith(block)
    }
}


/**
 * Base class for `buildscript` block evaluation on scripts targeting Settings.
 */
@ImplicitReceiver(Settings::class)
abstract class CompiledKotlinSettingsBuildscriptBlock(
    host: KotlinScriptHost<Settings>
) : CompiledKotlinSettingsScript(host) {

    /**
     * Configures the build script classpath for settings.
     *
     * @see [Settings.buildscript]
     */
    override fun buildscript(block: ScriptHandlerScope.() -> Unit) {
        buildscript.configureWith(block)
    }
}


@ImplicitReceiver(Gradle::class)
abstract class CompiledKotlinInitScript(
    private val host: KotlinScriptHost<Gradle>
) : InitScriptApi(host.target), PluginAware by PluginAwareDelegate(host) {

    /**
     * The [ScriptHandler] for this script.
     */
    override val initscript: ScriptHandler
        get() = host.scriptHandler

    override val fileOperations
        get() = host.fileOperations

    override val processOperations
        get() = host.processOperations
}


/**
 * Base class for `initscript` block evaluation on scripts targeting Gradle.
 */
@ImplicitReceiver(Gradle::class)
abstract class CompiledKotlinInitscriptBlock(host: KotlinScriptHost<Gradle>) : CompiledKotlinInitScript(host) {

    /**
     * Configures the classpath of the init script.
     */
    override fun initscript(block: ScriptHandlerScope.() -> Unit) {
        initscript.configureWith(block)
    }
}


internal
fun invalidPluginsCall(): Nothing =
    throw Exception(
        "The plugins {} block must not be used here. "
            + "If you need to apply a plugin imperatively, please use apply<PluginType>() or apply(plugin = \"id\") instead."
    )
