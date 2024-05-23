/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

import groovy.lang.Closure
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.CrossProjectConfigurator
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.invocation.Gradle
import org.gradle.api.invocation.GradleLifecycle
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.configuration.ConfigurationTargetIdentifier
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.initialization.SettingsState
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.PublicBuildPath
import org.gradle.internal.composite.IncludedBuildInternal
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.Path
import java.io.File
import java.util.Objects
import java.util.function.Supplier


class CrossProjectConfigurationReportingGradle private constructor(
    gradle: GradleInternal,
    private val referrerProject: ProjectInternal,
    private val crossProjectModelAccess: CrossProjectModelAccess,
    private val projectConfigurator: CrossProjectConfigurator
) : GradleInternal {

    private
    val delegate: GradleInternal = when (gradle) {
        // 'unwrapping' ensures that there are no chains of delegation
        is CrossProjectConfigurationReportingGradle -> gradle.delegate
        else -> gradle
    }

    override fun getParent(): GradleInternal? =
        delegate.parent?.let { delegateParent -> from(delegateParent, referrerProject) }

    override fun getRoot(): GradleInternal =
        when (val root = delegate.root) {
            delegate -> this
            else -> from(root, referrerProject)
        }

    override fun getRootProject(): ProjectInternal =
        crossProjectModelAccess.access(referrerProject, delegate.rootProject)

    override fun rootProject(action: Action<in Project>) {
        delegate.rootProject(action.withCrossProjectModelAccessCheck())
    }

    override fun allprojects(action: Action<in Project>) {
        // Use the delegate's implementation of `rootProject` to ensure that the action is only invoked once the rootProject is available
        delegate.rootProject {
            // Instead of the rootProject's `allProjects`, collect the projects while still tracking the current referrer project
            val root = this@CrossProjectConfigurationReportingGradle.rootProject
            projectConfigurator.allprojects(crossProjectModelAccess.getAllprojects(referrerProject, root), action)
        }
    }

    override fun addProjectEvaluationListener(listener: ProjectEvaluationListener): ProjectEvaluationListener {
        val result = CrossProjectModelAccessProjectEvaluationListener(listener, referrerProject, crossProjectModelAccess)
        delegate.addProjectEvaluationListener(result)
        return result
    }

    override fun removeProjectEvaluationListener(listener: ProjectEvaluationListener) {
        delegate.removeProjectEvaluationListener(CrossProjectModelAccessProjectEvaluationListener(listener, referrerProject, crossProjectModelAccess))
    }

    override fun projectsEvaluated(closure: Closure<*>) =
        delegate.projectsEvaluated(closure.withCrossProjectModelAccessChecks())

    override fun projectsEvaluated(action: Action<in Gradle>) =
        delegate.projectsEvaluated(action.withCrossProjectModelGradleAccessCheck())

    override fun beforeProject(closure: Closure<*>) {
        delegate.beforeProject(closure.withCrossProjectModelAccessChecks())
    }

    override fun beforeProject(action: Action<in Project>) {
        delegate.beforeProject(action.withCrossProjectModelAccessCheck())
    }

    override fun afterProject(closure: Closure<*>) {
        delegate.afterProject(closure.withCrossProjectModelAccessChecks())
    }

    override fun afterProject(action: Action<in Project>) {
        delegate.afterProject(action.withCrossProjectModelAccessCheck())
    }

    override fun getDefaultProject(): ProjectInternal = crossProjectModelAccess.access(referrerProject, delegate.defaultProject)

    override fun getGradle(): Gradle = this

    override fun getLifecycle(): GradleLifecycle =
        delegate.lifecycle

    override fun addListener(listener: Any) {
        delegate.addListener(maybeWrapListener(listener))
    }

    override fun removeListener(listener: Any) {
        delegate.removeListener(maybeWrapListener(listener))
    }

    override fun getTaskGraph(): TaskExecutionGraphInternal =
        crossProjectModelAccess.taskGraphForProject(referrerProject, delegate.taskGraph)

    override fun equals(other: Any?): Boolean =
        javaClass == (other as? CrossProjectConfigurationReportingGradle)?.javaClass &&
            other.delegate == delegate &&
            other.referrerProject == referrerProject

    override fun hashCode(): Int = Objects.hash(delegate, referrerProject)

    override fun toString(): String = "CrossProjectConfigurationReportingGradle($delegate)"

    override fun resetState() {
        // Should not be called
        throw UnsupportedOperationException()
    }

    internal
    companion object {
        fun from(gradle: GradleInternal, referrerProject: ProjectInternal): CrossProjectConfigurationReportingGradle {
            val parentCrossProjectModelAccess = gradle.serviceOf<CrossProjectModelAccess>()
            val parentCrossProjectConfigurator = gradle.serviceOf<CrossProjectConfigurator>()
            return CrossProjectConfigurationReportingGradle(gradle, referrerProject, parentCrossProjectModelAccess, parentCrossProjectConfigurator)
        }
    }

    private
    fun maybeWrapListener(listener: Any): Any = when (listener) {
        is ProjectEvaluationListener -> CrossProjectModelAccessProjectEvaluationListener(listener, referrerProject, crossProjectModelAccess)
        // all the supported listener types other than ProjectEvaluationListener are already reported as configuration cache problems in non-buildSrc builds
        else -> listener
    }

    private
    fun <T> Closure<T>.withCrossProjectModelAccessChecks(): Closure<T> =
        CrossProjectModelAccessTrackingClosure(this, referrerProject, crossProjectModelAccess)

    private
    fun Action<in Project>.withCrossProjectModelAccessCheck(): Action<Project> {
        val originalAction = this@withCrossProjectModelAccessCheck
        return Action<Project> {
            val originalProject = this@Action
            check(originalProject is ProjectInternal) { "Expected the projects in the model to be ProjectInternal" }
            originalAction.execute(crossProjectModelAccess.access(referrerProject, originalProject))
        }
    }

    private
    fun Action<in Gradle>.withCrossProjectModelGradleAccessCheck(): Action<Gradle> {
        val originalAction = this@withCrossProjectModelGradleAccessCheck
        return Action<Gradle> {
            val originalGradle = this@Action
            check(originalGradle is GradleInternal) { "Expected the Gradle instance to be GradleInternal" }
            originalAction.execute(crossProjectModelAccess.gradleInstanceForProject(referrerProject, originalGradle))
        }
    }

    private
    class CrossProjectModelAccessProjectEvaluationListener(
        private val delegate: ProjectEvaluationListener,
        private val referrerProject: ProjectInternal,
        private val crossProjectModelAccess: CrossProjectModelAccess
    ) : ProjectEvaluationListener {
        override fun beforeEvaluate(project: Project) {
            delegate.beforeEvaluate(crossProjectModelAccess.access(referrerProject, project as ProjectInternal))
        }

        override fun afterEvaluate(project: Project, state: ProjectState) {
            delegate.afterEvaluate(crossProjectModelAccess.access(referrerProject, project as ProjectInternal), state)
        }

        override fun equals(other: Any?): Boolean =
            javaClass == (other as? CrossProjectModelAccessProjectEvaluationListener)?.javaClass &&
                other.delegate == delegate &&
                other.referrerProject == referrerProject

        override fun hashCode(): Int = Objects.hash(delegate, referrerProject)

        override fun toString(): String = "CrossProjectModelAccessProjectEvaluationListener($delegate)"
    }

    // region delegated members
    override fun getPlugins(): PluginContainer =
        delegate.plugins

    override fun apply(closure: Closure<*>) =
        delegate.apply(closure)

    override fun apply(action: Action<in ObjectConfigurationAction>) =
        delegate.apply(action)

    override fun apply(options: MutableMap<String, *>) =
        delegate.apply(options)

    override fun getPluginManager(): PluginManagerInternal =
        delegate.pluginManager

    override fun getExtensions(): ExtensionContainer =
        delegate.extensions

    override fun getGradleVersion(): String =
        delegate.gradleVersion

    override fun getGradleUserHomeDir(): File =
        delegate.gradleUserHomeDir

    override fun getGradleHomeDir(): File? =
        delegate.gradleHomeDir

    override fun getStartParameter(): StartParameterInternal =
        delegate.startParameter

    override fun beforeSettings(closure: Closure<*>) =
        delegate.beforeSettings(closure)

    override fun beforeSettings(action: Action<in Settings>) =
        delegate.beforeSettings(action)

    override fun settingsEvaluated(closure: Closure<*>) =
        delegate.settingsEvaluated(closure)

    override fun settingsEvaluated(action: Action<in Settings>) =
        delegate.settingsEvaluated(action)

    override fun projectsLoaded(closure: Closure<*>) =
        delegate.projectsLoaded(closure)

    override fun projectsLoaded(action: Action<in Gradle>) =
        delegate.projectsLoaded(action)

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun buildFinished(closure: Closure<*>) =
        // already reported as configuration cache problem, no need to override
        delegate.buildFinished(closure)

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun buildFinished(action: Action<in BuildResult>) =
        // already reported as configuration cache problem, no need to override
        delegate.buildFinished(action)

    override fun addBuildListener(buildListener: BuildListener) =
        // already reported as configuration cache problem, no need to override
        delegate.addBuildListener(buildListener)

    override fun useLogger(logger: Any) =
        delegate.useLogger(logger)

    override fun getSharedServices(): BuildServiceRegistry =
        delegate.sharedServices

    override fun getIncludedBuilds(): MutableCollection<IncludedBuild> =
        delegate.includedBuilds

    override fun includedBuild(name: String): IncludedBuild =
        delegate.includedBuild(name)

    override fun getConfigurationTargetIdentifier(): ConfigurationTargetIdentifier =
        delegate.configurationTargetIdentifier

    override fun isRootBuild(): Boolean =
        delegate.isRootBuild

    override fun getOwner(): BuildState =
        delegate.owner

    override fun getProjectEvaluationBroadcaster(): ProjectEvaluationListener =
        delegate.projectEvaluationBroadcaster

    override fun getSettings(): SettingsInternal =
        delegate.settings

    override fun attachSettings(settings: SettingsState?) {
        delegate.attachSettings(settings)
    }

    override fun setDefaultProject(defaultProject: ProjectInternal) {
        delegate.defaultProject = defaultProject
    }

    override fun setRootProject(rootProject: ProjectInternal) {
        delegate.rootProject = rootProject
    }

    override fun getBuildListenerBroadcaster(): BuildListener =
        delegate.buildListenerBroadcaster

    override fun getServices(): ServiceRegistry =
        delegate.services

    override fun setClassLoaderScope(classLoaderScope: Supplier<out ClassLoaderScope>) {
        delegate.setClassLoaderScope(classLoaderScope)
    }

    override fun getClassLoaderScope(): ClassLoaderScope =
        delegate.classLoaderScope

    override fun setIncludedBuilds(includedBuilds: MutableCollection<out IncludedBuildInternal>) {
        delegate.setIncludedBuilds(includedBuilds)
    }

    override fun getIdentityPath(): Path =
        delegate.identityPath

    override fun contextualize(description: String): String =
        delegate.contextualize(description)

    override fun getPublicBuildPath(): PublicBuildPath =
        delegate.publicBuildPath

    override fun baseProjectClassLoaderScope(): ClassLoaderScope =
        delegate.baseProjectClassLoaderScope()

    override fun setBaseProjectClassLoaderScope(classLoaderScope: ClassLoaderScope) {
        delegate.setBaseProjectClassLoaderScope(classLoaderScope)
    }

    override fun getProjectRegistry(): ProjectRegistry<ProjectInternal> =
        delegate.projectRegistry

    override fun includedBuilds(): MutableList<out IncludedBuildInternal> =
        delegate.includedBuilds()
    //endregion delegated members
}
