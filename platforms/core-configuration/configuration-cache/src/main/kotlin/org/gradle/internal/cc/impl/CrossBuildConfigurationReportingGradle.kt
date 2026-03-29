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

package org.gradle.internal.cc.impl

import groovy.lang.Closure
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.invocation.Gradle
import org.gradle.api.invocation.GradleLifecycle
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.configuration.ConfigurationTargetIdentifier
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.initialization.SettingsState
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.PublicBuildPath
import org.gradle.internal.composite.IncludedBuildInternal
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.Path
import java.io.File
import java.util.function.Supplier

internal
class CrossBuildConfigurationReportingGradle(
    gradle: GradleInternal,
    private val referrer: GradleInternal,
    private val problems: ProblemsListener,
    private val problemFactory: ProblemFactory,
) : GradleInternal {

    private fun onBuildMutableStateAccess(what: String) {
        val problem = problemFactory.problem {
            text("Build ")
            reference(referrer.identityPath.toString())
            text(" cannot access Gradle.$what on build ")
            reference(gradle.buildPath)
        }
            .exception { message -> message.capitalized() }
            .build()

        problems.onProblem(problem)
    }

    internal
    val delegate: GradleInternal = when (gradle) {
        // 'unwrapping' ensures that there are no chains of delegation
        is CrossBuildConfigurationReportingGradle -> gradle.delegate
        else -> gradle
    }

    override fun isRootBuild(): Boolean = delegate.isRootBuild

    override fun getIdentityPath(): Path = delegate.identityPath

    override fun contextualize(description: String): String = delegate.contextualize(description)

    override fun getPublicBuildPath(): PublicBuildPath = delegate.publicBuildPath

    override fun getBuildPath(): String = delegate.buildPath

    override fun getGradleVersion(): String = delegate.gradleVersion

    override fun getGradleUserHomeDir(): File = delegate.gradleUserHomeDir

    override fun getGradleHomeDir(): File? = delegate.gradleHomeDir

    override fun getGradle(): Gradle = this

    // region mutable state
    override fun getRootProject(): ProjectInternal {
        onBuildMutableStateAccess("getRootProject")
        return delegate.getRootProject()
    }

    override fun setRootProject(rootProject: ProjectInternal) {
        onBuildMutableStateAccess("setRootProject")
        delegate.setRootProject(rootProject)
    }

    override fun getParent(): GradleInternal? =
        delegate.parent?.let { delegateParent ->
            CrossBuildConfigurationReportingGradle(
                delegateParent,
                referrer,
                problems,
                problemFactory
            )
        }

    override fun getRoot(): GradleInternal =
        when (val root = delegate.root) {
            delegate -> this
            else -> CrossBuildConfigurationReportingGradle(
                root,
                referrer,
                problems,
                problemFactory
            )
        }

    override fun getOwner(): BuildState {
        onBuildMutableStateAccess("getOwner")
        return delegate.getOwner()
    }

    override fun getTaskGraph(): TaskExecutionGraphInternal {
        onBuildMutableStateAccess("getTaskGraph")
        return delegate.taskGraph
    }

    override fun getDefaultProject(): ProjectInternal {
        onBuildMutableStateAccess("getDefaultProject")
        return delegate.getDefaultProject()
    }

    override fun setDefaultProject(defaultProject: ProjectInternal) {
        onBuildMutableStateAccess("setDefaultProject")
        delegate.setDefaultProject(defaultProject)
    }

    override fun getProjectEvaluationBroadcaster(): ProjectEvaluationListener {
        onBuildMutableStateAccess("getProjectEvaluationBroadcaster")
        return delegate.getProjectEvaluationBroadcaster()
    }

    override fun getSettings(): SettingsInternal {
        onBuildMutableStateAccess("getSettings")
        return delegate.settings
    }

    override fun attachSettings(settings: SettingsState?) {
        onBuildMutableStateAccess("attachSettings")
        delegate.attachSettings(settings)
    }

    override fun getBuildListenerBroadcaster(): BuildListener {
        onBuildMutableStateAccess("getBuildListenerBroadcaster")
        return delegate.getBuildListenerBroadcaster()
    }

    override fun getServices(): ServiceRegistry {
        onBuildMutableStateAccess("getServices")
        return delegate.services
    }

    override fun setClassLoaderScope(classLoaderScope: Supplier<out ClassLoaderScope>) {
        onBuildMutableStateAccess("setClassLoaderScope")
        delegate.setClassLoaderScope(classLoaderScope)
    }

    override fun getClassLoaderScope(): ClassLoaderScope {
        onBuildMutableStateAccess("getClassLoaderScope")
        return delegate.getClassLoaderScope()
    }

    override fun setIncludedBuilds(includedBuilds: Collection<IncludedBuildInternal>) {
        onBuildMutableStateAccess("setIncludedBuilds")
        delegate.setIncludedBuilds(includedBuilds)
    }

    override fun baseProjectClassLoaderScope(): ClassLoaderScope {
        onBuildMutableStateAccess("baseProjectClassLoaderScope")
        return delegate.baseProjectClassLoaderScope()
    }

    override fun setBaseProjectClassLoaderScope(classLoaderScope: ClassLoaderScope) {
        onBuildMutableStateAccess("setBaseProjectClassLoaderScope")
        delegate.setBaseProjectClassLoaderScope(classLoaderScope)
    }

    override fun getStartParameter(): StartParameterInternal {
        onBuildMutableStateAccess("getStartParameter")
        return delegate.getStartParameter()
    }

    override fun getProjectRegistry(): ProjectRegistry {
        onBuildMutableStateAccess("getProjectRegistry")
        return delegate.getProjectRegistry()
    }

    override fun resetState() {
        // Should not be called
        throw UnsupportedOperationException()
    }

    override fun rootProject(action: Action<in Project>) {
        onBuildMutableStateAccess("rootProject")
        delegate.rootProject(action)
    }

    override fun allprojects(action: Action<in Project>) {
        onBuildMutableStateAccess("allprojects")
        delegate.allprojects(action)
    }

    override fun addProjectEvaluationListener(listener: ProjectEvaluationListener): ProjectEvaluationListener {
        onBuildMutableStateAccess("addProjectEvaluationListener")
        return delegate.addProjectEvaluationListener(listener)
    }

    override fun removeProjectEvaluationListener(listener: ProjectEvaluationListener) {
        onBuildMutableStateAccess("removeProjectEvaluationListener")
        delegate.removeProjectEvaluationListener(listener)
    }

    override fun getLifecycle(): GradleLifecycle {
        onBuildMutableStateAccess("getLifecycle")
        return delegate.lifecycle
    }

    override fun beforeProject(closure: Closure<*>) {
        onBuildMutableStateAccess("beforeProject")
        delegate.beforeProject(closure)
    }

    override fun beforeProject(action: Action<in Project>) {
        onBuildMutableStateAccess("beforeProject")
        delegate.beforeProject(action)
    }

    override fun afterProject(closure: Closure<*>) {
        onBuildMutableStateAccess("afterProject")
        delegate.afterProject(closure)
    }

    override fun afterProject(action: Action<in Project>) {
        onBuildMutableStateAccess("afterProject")
        delegate.afterProject(action)
    }

    override fun beforeSettings(closure: Closure<*>) {
        onBuildMutableStateAccess("beforeSettings")
        delegate.beforeSettings(closure)
    }

    override fun beforeSettings(action: Action<in Settings>) {
        onBuildMutableStateAccess("beforeSettings")
        delegate.beforeSettings(action)
    }

    override fun settingsEvaluated(closure: Closure<*>) {
        onBuildMutableStateAccess("settingsEvaluated")
        delegate.settingsEvaluated(closure)
    }

    override fun settingsEvaluated(action: Action<in Settings>) {
        onBuildMutableStateAccess("settingsEvaluated")
        delegate.settingsEvaluated(action)
    }

    override fun projectsLoaded(closure: Closure<*>) {
        onBuildMutableStateAccess("projectsLoaded")
        delegate.projectsLoaded(closure)
    }

    override fun projectsLoaded(action: Action<in Gradle>) {
        onBuildMutableStateAccess("projectsLoaded")
        delegate.projectsLoaded(action)
    }

    override fun projectsEvaluated(closure: Closure<*>) {
        onBuildMutableStateAccess("projectsEvaluated")
        delegate.projectsEvaluated(closure)
    }

    override fun projectsEvaluated(action: Action<in Gradle>) {
        onBuildMutableStateAccess("projectsEvaluated")
        delegate.projectsEvaluated(action)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun buildFinished(closure: Closure<*>) {
        // already reported as configuration cache problem, no need to override
        delegate.buildFinished(closure)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun buildFinished(action: Action<in BuildResult>) {
        // already reported as configuration cache problem, no need to override
        delegate.buildFinished(action)
    }

    override fun addBuildListener(buildListener: BuildListener) {
        // already reported as configuration cache problem, no need to override
        delegate.addBuildListener(buildListener)
    }

    override fun addListener(listener: Any) {
        onBuildMutableStateAccess("addListener")
        delegate.addListener(listener)
    }

    override fun removeListener(listener: Any) {
        onBuildMutableStateAccess("removeListener")
        delegate.removeListener(listener)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun useLogger(logger: Any) {
        delegate.useLogger(logger)
    }

    override fun getSharedServices(): BuildServiceRegistry {
        onBuildMutableStateAccess("getSharedServices")
        return delegate.getSharedServices()
    }

    override fun getIncludedBuilds(): Collection<IncludedBuild> {
        onBuildMutableStateAccess("getIncludedBuilds")
        return delegate.includedBuilds
    }

    override fun includedBuilds(): List<IncludedBuildInternal> {
        onBuildMutableStateAccess("includedBuilds")
        return delegate.includedBuilds()
    }

    override fun includedBuild(name: String): IncludedBuild {
        onBuildMutableStateAccess("includedBuild")
        return delegate.includedBuild(name)
    }

    override fun getConfigurationTargetIdentifier(): ConfigurationTargetIdentifier =
        delegate.getConfigurationTargetIdentifier()

    override fun getProviders(): ProviderFactory {
        onBuildMutableStateAccess("getProviders")
        return delegate.getProviders()
    }

    override fun getPlugins(): PluginContainer {
        onBuildMutableStateAccess("getPlugins")
        return delegate.getPlugins()
    }

    override fun apply(closure: Closure<*>) {
        onBuildMutableStateAccess("apply")
        delegate.apply(closure)
    }

    override fun apply(action: Action<in ObjectConfigurationAction>) {
        onBuildMutableStateAccess("apply")
        delegate.apply(action)
    }

    override fun apply(options: Map<String, *>) {
        onBuildMutableStateAccess("apply")
        delegate.apply(options)
    }

    override fun getPluginManager(): PluginManagerInternal {
        onBuildMutableStateAccess("getPluginManager")
        return delegate.getPluginManager()
    }

    override fun getExtensions(): ExtensionContainer {
        onBuildMutableStateAccess("getExtensions")
        return delegate.getExtensions()
    }
    // endregion mutable state
}
