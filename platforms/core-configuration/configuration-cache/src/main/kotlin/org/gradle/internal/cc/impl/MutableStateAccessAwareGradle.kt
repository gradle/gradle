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
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.internal.project.ProjectState
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
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.Path
import java.io.File
import java.util.function.Supplier


/**
 * Abstract wrapper for [GradleInternal] that defines which methods access mutable state.
 *
 * Subclasses implement [onMutableStateAccess] to provide specific reporting behavior
 * (e.g., cross-build or cross-project isolation problem reporting).
 *
 * @see CrossBuildConfigurationReportingGradle
 * @see CrossProjectConfigurationReportingGradle
 */
internal
abstract class MutableStateAccessAwareGradle(
    gradle: GradleInternal,
) : GradleInternal {

    protected val delegate: GradleInternal = when (gradle) {
        // 'unwrapping' ensures that there are no chains of delegation
        is MutableStateAccessAwareGradle -> gradle.delegate
        else -> gradle
    }

    protected abstract fun onMutableStateAccess(what: String)

    private fun shouldNotBeUsed(): Nothing {
        throw UnsupportedOperationException("This internal method should not be used.")
    }

    // region abstract - subclass-specific wrapping
    abstract override fun getParent(): GradleInternal?
    abstract override fun getRoot(): GradleInternal
    // endregion abstract

    // region immutable - just delegate
    override fun isRootBuild(): Boolean = delegate.isRootBuild

    override fun getIdentityPath(): Path = delegate.identityPath

    override fun contextualize(description: String): String = delegate.contextualize(description)

    override fun getPublicBuildPath(): PublicBuildPath = delegate.publicBuildPath

    override fun getBuildPath(): String = delegate.buildPath

    override fun getGradleVersion(): String = delegate.gradleVersion

    override fun getGradleUserHomeDir(): File = delegate.gradleUserHomeDir

    override fun getGradleHomeDir(): File? = delegate.gradleHomeDir

    override fun getGradle(): Gradle = this

    override fun getConfigurationTargetIdentifier(): ConfigurationTargetIdentifier =
        delegate.getConfigurationTargetIdentifier()

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

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun useLogger(logger: Any) {
        delegate.useLogger(logger)
    }
    // endregion immutable

    // region shouldNotBeUsed - internal methods that should not be called through the wrapper
    override fun setDefaultProjectState(defaultProject: ProjectState) {
        shouldNotBeUsed()
    }

    override fun getOwner(): BuildState {
        shouldNotBeUsed()
    }

    override fun attachSettings(settings: SettingsState?) {
        shouldNotBeUsed()
    }

    override fun setClassLoaderScope(classLoaderScope: Supplier<out ClassLoaderScope>) {
        shouldNotBeUsed()
    }

    override fun getClassLoaderScope(): ClassLoaderScope {
        shouldNotBeUsed()
    }

    override fun baseProjectClassLoaderScope(): ClassLoaderScope {
        shouldNotBeUsed()
    }

    override fun setBaseProjectClassLoaderScope(classLoaderScope: ClassLoaderScope) {
        shouldNotBeUsed()
    }

    override fun setIncludedBuilds(includedBuilds: Collection<IncludedBuildInternal>) {
        shouldNotBeUsed()
    }

    override fun resetState() {
        shouldNotBeUsed()
    }

    override fun includedBuilds(): List<IncludedBuildInternal> {
        shouldNotBeUsed()
    }
    // endregion shouldNotBeUsed

    // region mutable state - report + delegate
    override fun getDefaultProjectState(): ProjectState {
        onMutableStateAccess("getDefaultProjectState")
        return delegate.defaultProjectState
    }

    override fun getRootProject(): Project {
        onMutableStateAccess("getRootProject")
        return delegate.getRootProject()
    }

    override fun getTaskGraph(): TaskExecutionGraphInternal {
        onMutableStateAccess("getTaskGraph")
        return delegate.taskGraph
    }

    override fun getProjectEvaluationBroadcaster(): ProjectEvaluationListener {
        onMutableStateAccess("getProjectEvaluationBroadcaster")
        return delegate.getProjectEvaluationBroadcaster()
    }

    override fun getSettings(): SettingsInternal {
        onMutableStateAccess("getSettings")
        return delegate.settings
    }

    override fun getBuildListenerBroadcaster(): BuildListener {
        onMutableStateAccess("getBuildListenerBroadcaster")
        return delegate.getBuildListenerBroadcaster()
    }

    override fun getServices(): ServiceRegistry {
        // TODO:isolated is a violation?
        return delegate.services
    }

    override fun getStartParameter(): StartParameterInternal {
        // TODO:isolated is a violation?
        onMutableStateAccess("getStartParameter")
        return delegate.startParameter
    }

    override fun getProjectRegistry(): ProjectRegistry {
        onMutableStateAccess("getProjectRegistry")
        return delegate.getProjectRegistry()
    }

    override fun rootProject(action: Action<in Project>) {
        onMutableStateAccess("rootProject")
        delegate.rootProject(action)
    }

    override fun allprojects(action: Action<in Project>) {
        onMutableStateAccess("allprojects")
        delegate.allprojects(action)
    }

    override fun addProjectEvaluationListener(listener: ProjectEvaluationListener): ProjectEvaluationListener {
        onMutableStateAccess("addProjectEvaluationListener")
        return delegate.addProjectEvaluationListener(listener)
    }

    override fun removeProjectEvaluationListener(listener: ProjectEvaluationListener) {
        onMutableStateAccess("removeProjectEvaluationListener")
        delegate.removeProjectEvaluationListener(listener)
    }

    override fun getLifecycle(): GradleLifecycle {
        onMutableStateAccess("getLifecycle")
        return delegate.lifecycle
    }

    override fun beforeProject(closure: Closure<*>) {
        onMutableStateAccess("beforeProject")
        delegate.beforeProject(closure)
    }

    override fun beforeProject(action: Action<in Project>) {
        onMutableStateAccess("beforeProject")
        delegate.beforeProject(action)
    }

    override fun afterProject(closure: Closure<*>) {
        onMutableStateAccess("afterProject")
        delegate.afterProject(closure)
    }

    override fun afterProject(action: Action<in Project>) {
        onMutableStateAccess("afterProject")
        delegate.afterProject(action)
    }

    override fun beforeSettings(closure: Closure<*>) {
        onMutableStateAccess("beforeSettings")
        delegate.beforeSettings(closure)
    }

    override fun beforeSettings(action: Action<in Settings>) {
        onMutableStateAccess("beforeSettings")
        delegate.beforeSettings(action)
    }

    override fun settingsEvaluated(closure: Closure<*>) {
        onMutableStateAccess("settingsEvaluated")
        delegate.settingsEvaluated(closure)
    }

    override fun settingsEvaluated(action: Action<in Settings>) {
        onMutableStateAccess("settingsEvaluated")
        delegate.settingsEvaluated(action)
    }

    override fun projectsLoaded(closure: Closure<*>) {
        onMutableStateAccess("projectsLoaded")
        delegate.projectsLoaded(closure)
    }

    override fun projectsLoaded(action: Action<in Gradle>) {
        onMutableStateAccess("projectsLoaded")
        delegate.projectsLoaded(action)
    }

    override fun projectsEvaluated(closure: Closure<*>) {
        onMutableStateAccess("projectsEvaluated")
        delegate.projectsEvaluated(closure)
    }

    override fun projectsEvaluated(action: Action<in Gradle>) {
        onMutableStateAccess("projectsEvaluated")
        delegate.projectsEvaluated(action)
    }

    override fun addListener(listener: Any) {
        onMutableStateAccess("addListener")
        delegate.addListener(listener)
    }

    override fun removeListener(listener: Any) {
        onMutableStateAccess("removeListener")
        delegate.removeListener(listener)
    }

    override fun getSharedServices(): BuildServiceRegistry {
        onMutableStateAccess("getSharedServices")
        return delegate.getSharedServices()
    }

    override fun getIncludedBuilds(): Collection<IncludedBuild> {
        onMutableStateAccess("getIncludedBuilds")
        return delegate.includedBuilds
    }

    override fun includedBuild(name: String): IncludedBuild {
        onMutableStateAccess("includedBuild")
        return delegate.includedBuild(name)
    }

    override fun getProviders(): ProviderFactory {
        onMutableStateAccess("getProviders")
        return delegate.getProviders()
    }

    override fun getPlugins(): PluginContainer {
        onMutableStateAccess("getPlugins")
        return delegate.getPlugins()
    }

    override fun apply(closure: Closure<*>) {
        onMutableStateAccess("apply")
        delegate.apply(closure)
    }

    override fun apply(action: Action<in ObjectConfigurationAction>) {
        onMutableStateAccess("apply")
        delegate.apply(action)
    }

    override fun apply(options: Map<String, *>) {
        onMutableStateAccess("apply")
        delegate.apply(options)
    }

    override fun getPluginManager(): PluginManagerInternal {
        onMutableStateAccess("getPluginManager")
        return delegate.getPluginManager()
    }

    override fun getExtensions(): ExtensionContainer {
        onMutableStateAccess("getExtensions")
        return delegate.getExtensions()
    }
    // endregion mutable state
}
