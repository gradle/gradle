/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.services

import org.gradle.api.IsolatedAction
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.invocation.Gradle
import org.gradle.internal.extensions.core.setSingletonProperty
import org.gradle.internal.cc.impl.isolation.IsolatedActionDeserializer
import org.gradle.internal.cc.impl.isolation.IsolatedActionSerializer
import org.gradle.internal.cc.impl.isolation.SerializedIsolatedActionGraph
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.extensions.core.peekSingletonProperty
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
    var eagerBeforeProject : EagerBeforeProject? = null

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
        IsolatedActionSerializer(owner, owner.serviceOf(), owner.serviceOf())
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
        if (target.peekSingletonProperty<IsolatedProjectActionsState>() != null) return

        val actions = isolatedActions(gradle, isolated)
        val state = IsolatedProjectActionsState.beforeProjectExecuted(actions.afterProject)
        target.setSingletonProperty(state)
        executeAll(actions.beforeProject, target)
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
        when (val state = project.peekSingletonProperty<IsolatedProjectActionsState>()) {
            null -> {
                val actions = isolatedActions(gradle, isolated)

                // preserve isolate semantics between `beforeProject` and `afterProject`
                project.setSingletonProperty(IsolatedProjectActionsState.beforeProjectExecuted(actions.afterProject))
                executeAll(actions.beforeProject, project)
            }

            // beforeProject was executed eagerly
            is IsolatedProjectActionsState.BeforeProjectExecuted -> {
                // preserve isolate semantics between `beforeProject` and `afterProject`
                project.setSingletonProperty(IsolatedProjectActionsState.beforeProjectExecuted(state.afterProject))
            }

            else -> error("Unexpected isolated actions state $state")
        }

    override fun afterEvaluate(project: Project, state: ProjectState) {
        val actionsState = project.peekSingletonProperty<IsolatedProjectActionsState>()

        require(actionsState is IsolatedProjectActionsState.BeforeProjectExecuted) {
            "afterEvaluate action cannot execute before beforeEvaluate"
        }
        project.setSingletonProperty(IsolatedProjectActionsState.afterProjectExecuted())
        executeAll(actionsState.afterProject, project)
    }
}


private
fun executeAll(actions: IsolatedProjectActionList, project: Project) {
    for (action in actions) {
        action.execute(project)
    }
}


private
fun isolatedActions(
    gradle: Gradle,
    isolated: SerializedIsolatedActionGraph<IsolatedProjectActions>
) = IsolateOwners.OwnerGradle(gradle).let { owner ->
    IsolatedActionDeserializer(owner, owner.serviceOf(), owner.serviceOf())
        .deserialize(isolated)
}
