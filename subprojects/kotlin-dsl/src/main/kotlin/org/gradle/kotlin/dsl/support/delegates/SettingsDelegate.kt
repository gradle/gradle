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

package org.gradle.kotlin.dsl.support.delegates

import groovy.lang.Closure

import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.initialization.ConfigurableIncludedBuild
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginAware
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.plugins.PluginManager
import org.gradle.caching.configuration.BuildCacheConfiguration
import org.gradle.plugin.management.PluginManagementSpec
import org.gradle.vcs.SourceControl

import java.io.File


/**
 * Facilitates the implementation of the [Settings] interface by delegation via subclassing.
 *
 * See [GradleDelegate] for why this is currently necessary.
 */
// TODO:kotlin-dsl merge with SettingsScriptApi
abstract class SettingsDelegate : PluginAware, ExtensionAware {

    internal
    abstract val delegate: Settings

    val settings: Settings
        get() = delegate.settings

    open val buildscript: ScriptHandler
        get() = delegate.buildscript

    val settingsDir: File
        get() = delegate.settingsDir

    val rootDir: File
        get() = delegate.rootDir

    val rootProject: ProjectDescriptor
        get() = delegate.rootProject

    fun project(path: String): ProjectDescriptor =
        delegate.project(path)

    fun findProject(path: String): ProjectDescriptor? =
        delegate.findProject(path)

    fun project(projectDir: File): ProjectDescriptor =
        delegate.project(projectDir)

    fun findProject(projectDir: File): ProjectDescriptor? =
        delegate.findProject(projectDir)

    val gradle: Gradle
        get() = delegate.gradle

    fun includeBuild(rootProject: Any) =
        delegate.includeBuild(rootProject)

    fun includeBuild(rootProject: Any, configuration: Action<ConfigurableIncludedBuild>) =
        delegate.includeBuild(rootProject, configuration)

    fun enableFeaturePreview(name: String) =
        delegate.enableFeaturePreview(name)

    override fun getExtensions(): ExtensionContainer =
        delegate.extensions

    val buildCache: BuildCacheConfiguration
        get() = delegate.buildCache

    fun pluginManagement(pluginManagementSpec: Action<in PluginManagementSpec>) =
        delegate.pluginManagement(pluginManagementSpec)

    val pluginManagement: PluginManagementSpec
        get() = delegate.pluginManagement

    fun sourceControl(configuration: Action<in SourceControl>) =
        delegate.sourceControl(configuration)

    override fun getPlugins(): PluginContainer =
        delegate.plugins

    // TODO:kotlin-dsl remove
    override fun apply(closure: Closure<Any>) =
        delegate.apply(closure)

    override fun apply(action: Action<in ObjectConfigurationAction>) =
        delegate.apply(action)

    // TODO:kotlin-dsl remove
    override fun apply(options: Map<String, *>) =
        delegate.apply(options)

    override fun getPluginManager(): PluginManager =
        delegate.pluginManager

    fun include(vararg projectPaths: String) =
        delegate.include(*projectPaths)

    fun includeFlat(vararg projectNames: String) =
        delegate.includeFlat(*projectNames)

    val startParameter: StartParameter
        get() = delegate.startParameter

    fun buildCache(action: Action<in BuildCacheConfiguration>) =
        delegate.buildCache(action)

    val sourceControl: SourceControl
        get() = delegate.sourceControl
}
