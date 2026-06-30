/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.isolate.actions.services

import org.gradle.api.IsolatedAction
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.internal.build.BuildIncluder
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.isolate.graph.IsolatedActionDeserializer
import org.gradle.internal.isolate.graph.IsolatedActionSerializer
import org.gradle.internal.isolate.graph.SerializedIsolatedActionGraph
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.invocation.GradleLifecycleActionExecutor
import org.gradle.invocation.IsolatedProjectEvaluationListenerProvider


private
typealias IsolatedProjectAction = IsolatedAction<in Project>


private
typealias IsolatedProjectActionList = Collection<IsolatedProjectAction>


internal
class DefaultIsolatedProjectEvaluationListenerProvider(
    private val userCodeApplicationContext: UserCodeApplicationContext
) : IsolatedProjectEvaluationListenerProvider, GradleLifecycleActionExecutor {

    private
    val beforeProject = mutableListOf<IsolatedProjectAction>()

    private
    val afterProject = mutableListOf<IsolatedProjectAction>()

    private
    var eagerBeforeProject: EagerBeforeProject? = null

    override fun beforeProject(action: IsolatedProjectAction) {
        // TODO:isolated encode Application instances as part of the Environment to avoid waste
        beforeProject.add(withUserCodeApplicationContext(action))
    }

    override fun afterProject(action: IsolatedProjectAction) {
        afterProject.add(withUserCodeApplicationContext(action))
    }

    private
    fun withUserCodeApplicationContext(action: IsolatedProjectAction): IsolatedProjectAction =
        userCodeApplicationContext.current()?.let { context ->
            IsolatedProjectAction {
                val target = this
                context.reapply {
                    action.execute(target)
                }
            }
        } ?: action

    override fun executeBeforeProjectFor(project: Project) {
        eagerBeforeProject?.execute(project)
    }

    override fun isolateFor(gradle: Gradle): ProjectEvaluationListener? = when {
        beforeProject.isEmpty() && afterProject.isEmpty() -> null
        else -> {
            val actions = isolateActions(gradle)
            if (beforeProject.isNotEmpty()) {
                eagerBeforeProject = EagerBeforeProject(gradle, actions)
            }
            clearCallbacks()
            IsolatedProjectEvaluationListener(gradle, actions)
        }
    }

    override fun clear() {
        clearCallbacks()
        eagerBeforeProject = null
    }

    private fun clearCallbacks() {
        beforeProject.clear()
        afterProject.clear()
    }

    private
    fun isolateActions(gradle: Gradle): SerializedIsolatedActionGraph<IsolatedProjectActions> =
        isolate(
            IsolatedProjectActions(beforeProject, afterProject),
            IsolateOwners.OwnerGradle(gradle)
        )

    private
    fun isolate(actions: IsolatedProjectActions, owner: IsolateOwner) =
        IsolatedActionSerializer(owner, owner.serviceOf(), owner.serviceOf<IsolatedActionCodecsFactory>())
            .serialize(actions)
}


private
sealed class IsolatedProjectActionsState {
    data class BeforeProjectExecuted(
        val afterProject: IsolatedProjectActionList
    ) : IsolatedProjectActionsState()

    object AfterProjectExecuted : IsolatedProjectActionsState()

    companion object {
        fun beforeProjectExecuted(
            afterProject: IsolatedProjectActionList,
        ): IsolatedProjectActionsState = BeforeProjectExecuted(afterProject)

        fun afterProjectExecuted(): IsolatedProjectActionsState = AfterProjectExecuted
    }
}

private
class EagerBeforeProject(
    private val gradle: Gradle,
    private val isolated: SerializedIsolatedActionGraph<IsolatedProjectActions>
) : IsolatedAction<Project> {

    override fun execute(target: Project) {
        // Execute only if project just loaded
        if (target.getLifecycleActionsState() != null) return

        val actions = isolatedActions(gradle, isolated)
        val state = IsolatedProjectActionsState.beforeProjectExecuted(actions.afterProject)
        target.setLifecycleActionsState(state)
        executeAll(actions.beforeProject, target, gradle)
    }
}

private
data class IsolatedProjectActions(
    val beforeProject: IsolatedProjectActionList,
    val afterProject: IsolatedProjectActionList
)


private
class IsolatedProjectEvaluationListener(
    private val gradle: Gradle,
    private val isolated: SerializedIsolatedActionGraph<IsolatedProjectActions>
) : ProjectEvaluationListener {

    override fun beforeEvaluate(project: Project) =
        when (val state = project.getLifecycleActionsState()) {
            null -> {
                val actions = isolatedActions(gradle, isolated)
                // preserve isolate semantics between `beforeProject` and `afterProject`
                project.setLifecycleActionsState(IsolatedProjectActionsState.beforeProjectExecuted(actions.afterProject))
                executeAll(actions.beforeProject, project, gradle)
            }

            is IsolatedProjectActionsState.BeforeProjectExecuted -> {
                // beforeProject was executed eagerly
            }

            else -> error("Unexpected isolated actions state $state")
        }

    override fun afterEvaluate(project: Project, state: ProjectState) {
        val actionsState = project.getLifecycleActionsState()

        require(actionsState is IsolatedProjectActionsState.BeforeProjectExecuted) {
            "afterEvaluate action cannot execute before beforeEvaluate"
        }
        project.setLifecycleActionsState(IsolatedProjectActionsState.afterProjectExecuted())
        executeAll(actionsState.afterProject, project, gradle)
    }
}


private
fun executeAll(actions: IsolatedProjectActionList, project: Project, gradle: Gradle) {
    for (action in actions) {
        try {
            action.execute(project)
        } catch (t: Throwable) {
            rethrowWithLifecyclePluginHintIfApplicable(t, gradle)
        }
    }
}


private
fun rethrowWithLifecyclePluginHintIfApplicable(t: Throwable, gradle: Gradle): Nothing {
    val toThrow = lifecyclePluginHintFor(t, gradle) ?: t
    throw toThrow
}


private
fun lifecyclePluginHintFor(t: Throwable, gradle: Gradle): Throwable? {
    val gradleInternal = gradle as? GradleInternal ?: return null
    if (!hasIncludedPluginBuilds(gradleInternal)) return null
    val unknown = findUnknownPluginInCauseChain(t) ?: return null
    val pluginId = unknown.pluginId ?: return null
    val documentationRegistry = gradleInternal.services.get(DocumentationRegistry::class.java)
    return UnknownPluginException(unknown.message + lifecyclePluginHint(pluginId, documentationRegistry), pluginId).also { it.initCause(t) }
}


private
fun hasIncludedPluginBuilds(gradle: GradleInternal): Boolean {
    val buildIncluder = gradle.services.find(BuildIncluder::class.java) as? BuildIncluder ?: return false
    return buildIncluder.registeredPluginBuilds.isNotEmpty()
}


private
tailrec fun findUnknownPluginInCauseChain(t: Throwable?): UnknownPluginException? = when (t) {
    null -> null
    is UnknownPluginException -> t
    else -> findUnknownPluginInCauseChain(t.cause.takeUnless { it === t })
}


private
fun lifecyclePluginHint(pluginId: String, documentationRegistry: DocumentationRegistry): String = "\n" +
    "If this plugin is provided by a build registered via `pluginManagement.includeBuild(...)`, " +
    "either move the `gradle.lifecycle.beforeProject` registration into a settings convention plugin " +
    "published from that build (recommended), or declare the plugin in the settings `plugins {}` block " +
    "(e.g. `plugins { id(\"$pluginId\") apply false }`) to make it available to `gradle.lifecycle` callbacks.\n" +
    documentationRegistry.getDocumentationRecommendationFor("information", "isolated_projects", "sec:lifecycle_callbacks_with_included_plugin_builds")


private
fun isolatedActions(
    gradle: Gradle,
    isolated: SerializedIsolatedActionGraph<IsolatedProjectActions>
) = IsolateOwners.OwnerGradle(gradle).let { owner ->
    IsolatedActionDeserializer(owner, owner.serviceOf(), owner.serviceOf<IsolatedActionCodecsFactory>())
        .deserialize(isolated)
}

private
fun Project.getLifecycleActionsState() = uncheckedCast<ProjectInternal>().lifecycleActionsState

private
fun Project.setLifecycleActionsState(state: IsolatedProjectActionsState?) {
    uncheckedCast<ProjectInternal>().setLifecycleActionsState(state)
}
