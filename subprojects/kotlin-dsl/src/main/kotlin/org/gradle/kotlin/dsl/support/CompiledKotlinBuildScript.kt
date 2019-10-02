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
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.logging.LoggingManager
import org.gradle.api.plugins.PluginAware

import org.gradle.kotlin.dsl.ScriptHandlerScope
import org.gradle.plugin.use.PluginDependenciesSpec


@ImplicitReceiver(Project::class)
open class CompiledKotlinBuildScript(
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
    @Suppress("unused")
    open fun buildscript(@Suppress("unused_parameter") block: ScriptHandlerScope.() -> Unit): Unit =
        internalError()

    /**
     * Configures the plugin dependencies for this project.
     *
     * @see [PluginDependenciesSpec]
     */
    @Suppress("unused")
    open fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpec.() -> Unit): Unit =
        invalidPluginsCall()
}


/**
 * Base class for `buildscript` block evaluation on scripts targeting Project.
 */
@ImplicitReceiver(Project::class)
open class CompiledKotlinBuildscriptBlock(
    host: KotlinScriptHost<Project>
) : CompiledKotlinBuildScript(host) {

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
open class CompiledKotlinSettingsBuildscriptBlock(
    host: KotlinScriptHost<Settings>
) : CompiledKotlinSettingsScript(host) {

    /**
     * Configures the build script classpath for settings.
     *
     * @see [Settings.getBuildscript]
     */
    fun buildscript(block: ScriptHandlerScope.() -> Unit) {
        buildscript.configureWith(block)
    }
}


@ImplicitReceiver(Gradle::class)
open class CompiledKotlinInitScript(
    private val host: KotlinScriptHost<Gradle>
) : DefaultKotlinScript(InitScriptHost(host)), PluginAware by PluginAwareScript(host) {

    /**
     * The [ScriptHandler] for this script.
     */
    open val initscript: ScriptHandler
        get() = host.scriptHandler

    private
    class InitScriptHost(val host: KotlinScriptHost<Gradle>) : Host {
        override fun getLogger(): Logger = Logging.getLogger(Gradle::class.java)
        override fun getLogging(): LoggingManager = host.target.serviceOf()
        override fun getFileOperations(): FileOperations = host.fileOperations
        override fun getProcessOperations(): ProcessOperations = host.processOperations
    }
}


/**
 * Base class for `initscript` block evaluation on scripts targeting Gradle.
 */
@ImplicitReceiver(Gradle::class)
open class CompiledKotlinInitscriptBlock(
    host: KotlinScriptHost<Gradle>
) : CompiledKotlinInitScript(host) {

    /**
     * Configures the classpath of the init script.
     */
    fun initscript(block: ScriptHandlerScope.() -> Unit) {
        initscript.configureWith(block)
    }
}


internal
fun invalidPluginsCall(): Nothing =
    throw Exception(
        "The plugins {} block must not be used here. "
            + "If you need to apply a plugin imperatively, please use apply<PluginType>() or apply(plugin = \"id\") instead."
    )
