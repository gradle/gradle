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

package org.gradle.internal.cc.impl

import com.google.common.collect.ImmutableList
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.project.CrossProjectConfigurator
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.internal.composite.IncludedBuildInternal
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.internal.extensions.stdlib.capitalized
import java.util.Objects
import org.gradle.api.internal.project.ProjectState as InternalProjectState

internal
class CrossProjectConfigurationReportingGradle(
    gradle: GradleInternal,
    private val referrerProject: ProjectIdentity,
    private val ipProblems: IsolatedProjectsProblemsReporter
) : MutableStateAccessAwareGradle(gradle) {

    private val crossProjectModelAccess: CrossProjectModelAccess = delegate.serviceOf()

    private val projectConfigurator: CrossProjectConfigurator = delegate.serviceOf()

    override fun onMutableStateAccess(what: String) {
        ipProblems.report {
            problem {
                text("Project ")
                reference(referrerProject.buildTreePath)
                text(" cannot access Gradle.$what on build ")
                reference(identityPath.asString())
            }
                .exception { message -> message.capitalized() }
                .build()
        }
    }

    override fun getParent(): GradleInternal? =
        delegate.parent?.let { delegateParent ->
            CrossProjectConfigurationReportingGradle(delegateParent, referrerProject, ipProblems)
        }

    override fun getRoot(): GradleInternal =
        when (val root = delegate.root) {
            delegate -> this
            else -> CrossProjectConfigurationReportingGradle(root, referrerProject, ipProblems)
        }

    override fun getSharedServices(): BuildServiceRegistry = delegate.sharedServices

    override fun getStartParameter(): StartParameterInternal = delegate.startParameter

    override fun includedBuilds(): List<IncludedBuildInternal> = ImmutableList.copyOf(delegate.includedBuilds())

    override fun getDefaultProjectState(): InternalProjectState = delegate.defaultProjectState

    override fun getGradle(): Gradle = this

    // region fine-grained cross-project model access tracking
    // These methods override the base class mutable state defaults with
    // cross-project-specific wrapping that serves as a fine-grained violation tracker.
    override fun getRootProject(): ProjectInternal =
        getCrossProjectRootProject()

    // Split out so it's clear we're not calling the @ForExternalUse method.
    private fun getCrossProjectRootProject(): ProjectInternal =
        crossProjectModelAccess.accessFromState(referrerProject, delegate.owner.rootProject)

    override fun rootProject(action: Action<in Project>) {
        delegate.rootProject(action.withCrossProjectModelAccessCheck())
    }

    override fun allprojects(action: Action<in Project>) {
        // Use the delegate's implementation of `rootProject` to ensure that the action is only invoked once the rootProject is available
        delegate.rootProject {
            // Instead of the rootProject's `allProjects`, collect the projects while still tracking the current referrer project
            val root = this@CrossProjectConfigurationReportingGradle.getCrossProjectRootProject()
            projectConfigurator.allprojects(crossProjectModelAccess.getAllprojects(referrerProject, root.projectIdentity), action)
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
        private val referrerProject: ProjectIdentity,
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
    // endregion fine-grained
}
