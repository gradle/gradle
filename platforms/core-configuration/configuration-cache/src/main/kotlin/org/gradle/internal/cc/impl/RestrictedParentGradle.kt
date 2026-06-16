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
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
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
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.Path
import java.io.File
import java.util.function.Supplier

/**
 * A highly restrictive view of a parent [Gradle] instance, used when a project reaches its build's
 * [Gradle.getParent].
 *
 * Only shared services and truly immutable data (versions, directories, build identity) are exposed.
 * Any other access reports an Isolated Projects problem against the referrer project, so that reaching
 * into a parent build's mutable model is flagged rather than silently allowed.
 */
internal
class RestrictedParentGradle(
    gradle: GradleInternal,
    private val referrerProject: ProjectIdentity,
    private val ipProblems: IsolatedProjectsProblemsReporter
) : GradleInternal {

    internal
    val delegate: GradleInternal = when (gradle) {
        // 'unwrapping' ensures that there are no chains of delegation
        is RestrictedParentGradle -> gradle.delegate
        else -> gradle
    }

    private fun onMutableStateAccess(what: String) {
        ipProblems.report {
            problem {
                text("Project ")
                reference(referrerProject.buildTreePath)
                text(" cannot access Gradle.$what on parent build ")
                reference(delegate.identityPath.asString())
            }
                .exception { message -> message.capitalized() }
                .build()
        }
    }

    private fun shouldNotBeUsed(): Nothing {
        throw UnsupportedOperationException("This internal method should not be used.")
    }

    // region accessible: shared services and immutable data
    override fun getSharedServices(): BuildServiceRegistry =
        delegate.sharedServices

    override fun getGradleVersion(): String =
        delegate.gradleVersion

    override fun getGradleUserHomeDir(): File =
        delegate.gradleUserHomeDir

    override fun getGradleHomeDir(): File? =
        delegate.gradleHomeDir

    override fun getBuildPath(): String =
        delegate.buildPath

    override fun getIdentityPath(): Path =
        delegate.identityPath

    override fun isRootBuild(): Boolean =
        delegate.isRootBuild

    override fun getPublicBuildPath(): PublicBuildPath =
        delegate.publicBuildPath

    override fun contextualize(description: String): String =
        delegate.contextualize(description)

    override fun getConfigurationTargetIdentifier(): ConfigurationTargetIdentifier =
        delegate.configurationTargetIdentifier

    override fun getGradle(): Gradle = this

    override fun getParent(): GradleInternal? =
        delegate.parent?.let { delegateParent -> RestrictedParentGradle(delegateParent, referrerProject, ipProblems) }

    override fun getRoot(): GradleInternal =
        when (val root = delegate.root) {
            delegate -> this
            else -> RestrictedParentGradle(root, referrerProject, ipProblems)
        }
    // endregion accessible

    // region mutable state
    override fun getRootProject(): ProjectInternal {
        onMutableStateAccess("getRootProject")
        return delegate.owner.rootProject.mutableModel
    }

    override fun getTaskGraph(): TaskExecutionGraphInternal {
        onMutableStateAccess("getTaskGraph")
        return delegate.taskGraph
    }

    override fun getDefaultProjectState(): ProjectState {
        onMutableStateAccess("getDefaultProjectState")
        return delegate.defaultProjectState
    }

    override fun getProjectEvaluationBroadcaster(): ProjectEvaluationListener {
        onMutableStateAccess("getProjectEvaluationBroadcaster")
        return delegate.projectEvaluationBroadcaster
    }

    override fun getSettings(): SettingsInternal {
        onMutableStateAccess("getSettings")
        return delegate.settings
    }

    override fun getBuildListenerBroadcaster(): BuildListener {
        onMutableStateAccess("getBuildListenerBroadcaster")
        return delegate.buildListenerBroadcaster
    }

    override fun getServices(): ServiceRegistry {
        onMutableStateAccess("getServices")
        return delegate.services
    }

    override fun getStartParameter(): StartParameterInternal {
        onMutableStateAccess("getStartParameter")
        return delegate.startParameter
    }

    override fun getProjectRegistry(): ProjectRegistry {
        onMutableStateAccess("getProjectRegistry")
        return delegate.projectRegistry
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
        return delegate.providers
    }

    override fun getPlugins(): PluginContainer {
        onMutableStateAccess("getPlugins")
        return delegate.plugins
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
        return delegate.pluginManager
    }

    override fun getExtensions(): ExtensionContainer {
        onMutableStateAccess("getExtensions")
        return delegate.extensions
    }
    // endregion mutable state

    // region already reported as configuration cache problem, no need to override
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun buildFinished(closure: Closure<*>) {
        delegate.buildFinished(closure)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun buildFinished(action: Action<in BuildResult>) {
        delegate.buildFinished(action)
    }

    override fun addBuildListener(buildListener: BuildListener) {
        delegate.addBuildListener(buildListener)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun useLogger(logger: Any) {
        delegate.useLogger(logger)
    }
    // endregion

    // region internal-only members that must not be reached through this view
    override fun getOwner(): BuildState {
        shouldNotBeUsed()
    }

    override fun setDefaultProjectState(defaultProject: ProjectState) {
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

    override fun includedBuilds(): List<IncludedBuildInternal> {
        shouldNotBeUsed()
    }

    override fun resetState() {
        shouldNotBeUsed()
    }
    // endregion
}
