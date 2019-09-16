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

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginAware
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.plugins.PluginManager

import java.io.File


/**
 * Facilitates the implementation of the [Gradle] interface by delegation via subclassing.
 *
 * So we can avoid Kotlin's [implementation by delegation](https://kotlinlang.org/docs/reference/delegation.html#implementation-by-delegation)
 * until all required interfaces have been compiled with Java 8 parameter names (otherwise parameter names
 * are lost in the exposed implementation).
 *
 * Once the required interfaces are compiled with Java 8 parameter names these classes can be removed in favor
 * of Kotlin's implementation by delegation.
 */
// TODO:kotlin-dsl merge with `InitScriptApi`
abstract class GradleDelegate : PluginAware {

    internal
    abstract val delegate: Gradle

    val gradleVersion: String
        get() = delegate.gradleVersion

    val gradleUserHomeDir: File
        get() = delegate.gradleUserHomeDir

    val gradleHomeDir: File?
        get() = delegate.gradleHomeDir

    // TODO:kotlin-dsl remove
    fun getParent(): Gradle? =
        delegate.parent

    val rootProject: Project
        get() = delegate.rootProject

    fun rootProject(action: Action<in Project>) =
        delegate.rootProject(action)

    fun allprojects(action: Action<in Project>) =
        delegate.allprojects(action)

    val taskGraph: TaskExecutionGraph
        get() = delegate.taskGraph

    val startParameter: StartParameter
        get() = delegate.startParameter

    fun addProjectEvaluationListener(listener: ProjectEvaluationListener): ProjectEvaluationListener =
        delegate.addProjectEvaluationListener(listener)

    fun removeProjectEvaluationListener(listener: ProjectEvaluationListener) =
        delegate.removeProjectEvaluationListener(listener)

    // TODO:kotlin-dsl remove
    fun beforeProject(closure: Closure<Any>) =
        delegate.beforeProject(closure)

    fun beforeProject(action: Action<in Project>) =
        delegate.beforeProject(action)

    // TODO:kotlin-dsl remove
    fun afterProject(closure: Closure<Any>) =
        delegate.afterProject(closure)

    fun afterProject(action: Action<in Project>) =
        delegate.afterProject(action)

    // TODO:kotlin-dsl remove
    fun buildStarted(closure: Closure<Any>) =
        delegate.buildStarted(closure)

    fun buildStarted(action: Action<in Gradle>) =
        delegate.buildStarted(action)

    // TODO:kotlin-dsl remove
    fun settingsEvaluated(closure: Closure<Any>) =
        delegate.settingsEvaluated(closure)

    fun settingsEvaluated(action: Action<in Settings>) =
        delegate.settingsEvaluated(action)

    // TODO:kotlin-dsl remove
    fun projectsLoaded(closure: Closure<Any>) =
        delegate.projectsLoaded(closure)

    fun projectsLoaded(action: Action<in Gradle>) =
        delegate.projectsLoaded(action)

    // TODO:kotlin-dsl remove
    fun projectsEvaluated(closure: Closure<Any>) =
        delegate.projectsEvaluated(closure)

    fun projectsEvaluated(action: Action<in Gradle>) =
        delegate.projectsEvaluated(action)

    // TODO:kotlin-dsl remove
    fun buildFinished(closure: Closure<Any>) =
        delegate.buildFinished(closure)

    fun buildFinished(action: Action<in BuildResult>) =
        delegate.buildFinished(action)

    fun addBuildListener(buildListener: BuildListener) =
        delegate.addBuildListener(buildListener)

    fun addListener(listener: Any) =
        delegate.addListener(listener)

    fun removeListener(listener: Any) =
        delegate.removeListener(listener)

    fun useLogger(logger: Any) =
        delegate.useLogger(logger)

    val gradle: Gradle
        get() = delegate.gradle

    val includedBuilds: MutableCollection<IncludedBuild>
        get() = delegate.includedBuilds

    fun includedBuild(name: String): IncludedBuild =
        delegate.includedBuild(name)

    // TODO:kotlin-dsl remove
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

    // TODO:kotlin-dsl remove
    override fun getPluginManager(): PluginManager =
        delegate.pluginManager
}
