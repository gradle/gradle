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
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.ProviderFactory
import org.gradle.caching.configuration.BuildCacheConfiguration
import org.gradle.plugin.management.PluginManagementSpec
import org.gradle.vcs.SourceControl

import java.io.File


/**
 * Facilitates the implementation of the [Settings] interface by delegation via subclassing.
 */
abstract class SettingsDelegate : Settings {

    internal
    abstract val delegate: Settings

    override fun getSettings(): Settings =
        delegate.settings

    override fun getBuildscript(): ScriptHandler =
        delegate.buildscript

    override fun getSettingsDir(): File =
        delegate.settingsDir

    override fun getRootDir(): File =
        delegate.rootDir

    override fun getRootProject(): ProjectDescriptor =
        delegate.rootProject

    override fun project(path: String): ProjectDescriptor =
        delegate.project(path)

    override fun findProject(path: String): ProjectDescriptor? =
        delegate.findProject(path)

    override fun project(projectDir: File): ProjectDescriptor =
        delegate.project(projectDir)

    override fun findProject(projectDir: File): ProjectDescriptor? =
        delegate.findProject(projectDir)

    override fun getGradle(): Gradle =
        delegate.gradle

    override fun includeBuild(rootProject: Any) =
        delegate.includeBuild(rootProject)

    override fun includeBuild(rootProject: Any, configuration: Action<ConfigurableIncludedBuild>) =
        delegate.includeBuild(rootProject, configuration)

    override fun enableFeaturePreview(name: String) =
        delegate.enableFeaturePreview(name)

    override fun getExtensions(): ExtensionContainer =
        delegate.extensions

    override fun getBuildCache(): BuildCacheConfiguration =
        delegate.buildCache

    override fun pluginManagement(pluginManagementSpec: Action<in PluginManagementSpec>) =
        delegate.pluginManagement(pluginManagementSpec)

    override fun getPluginManagement(): PluginManagementSpec =
        delegate.pluginManagement

    override fun sourceControl(configuration: Action<in SourceControl>) =
        delegate.sourceControl(configuration)

    override fun getPlugins(): PluginContainer =
        delegate.plugins

    override fun apply(closure: Closure<Any>) =
        delegate.apply(closure)

    override fun apply(action: Action<in ObjectConfigurationAction>) =
        delegate.apply(action)

    override fun apply(options: Map<String, *>) =
        delegate.apply(options)

    override fun getPluginManager(): PluginManager =
        delegate.pluginManager

    override fun include(vararg projectPaths: String?) =
        delegate.include(*projectPaths)

    override fun includeFlat(vararg projectNames: String?) =
        @Suppress("deprecation")
        delegate.includeFlat(*projectNames)

    override fun getStartParameter(): StartParameter =
        delegate.startParameter

    override fun buildCache(action: Action<in BuildCacheConfiguration>) =
        delegate.buildCache(action)

    override fun getSourceControl(): SourceControl =
        delegate.sourceControl

    override fun getProviders(): ProviderFactory =
        delegate.providers

    override fun dependencyResolutionManagement(dependencyResolutionConfiguration: Action<in DependencyResolutionManagement>) =
        delegate.dependencyResolutionManagement(dependencyResolutionConfiguration)

    override fun getDependencyResolutionManagement(): DependencyResolutionManagement =
        delegate.dependencyResolutionManagement
}
