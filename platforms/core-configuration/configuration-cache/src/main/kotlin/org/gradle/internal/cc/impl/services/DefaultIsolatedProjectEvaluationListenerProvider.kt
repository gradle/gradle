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
import org.gradle.invocation.IsolatedProjectEvaluationListenerProvider
import java.util.concurrent.atomic.AtomicReference


private
typealias IsolatedProjectAction = IsolatedAction<in Project>


private
typealias IsolatedProjectActionList = Collection<IsolatedProjectAction>


internal
class DefaultIsolatedProjectEvaluationListenerProvider(
    private val userCodeApplicationContext: UserCodeApplicationContext
) : IsolatedProjectEvaluationListenerProvider {

    private
    val beforeProject = mutableListOf<IsolatedProjectAction>()

    private
    val afterProject = mutableListOf<IsolatedProjectAction>()

    private
    val allprojects = mutableListOf<IsolatedProjectAction>()

    private
    var serializedIsolatedActions: SerializedIsolatedActionGraph<IsolatedProjectActions>? = null

    private
    var allprojectsAction: AtomicReference<Allprojects?> = AtomicReference(null)

    override fun beforeProject(action: IsolatedProjectAction) {
        // TODO:isolated encode Application instances as part of the Environment to avoid waste
        beforeProject.add(withUserCodeApplicationContext(action))
    }

    override fun afterProject(action: IsolatedProjectAction) {
        afterProject.add(withUserCodeApplicationContext(action))
    }

    override fun allprojects(action: IsolatedProjectAction) {
        allprojects.add(withUserCodeApplicationContext(action))
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

    override fun isolateFor(gradle: Gradle): ProjectEvaluationListener? = when {
        beforeProject.isEmpty() && afterProject.isEmpty() && allprojects.isEmpty() -> null
        else -> IsolatedProjectEvaluationListener(gradle, getSerializedIsolatedActions(gradle))

    }

    override fun executeAllprojectsFor(project: Project) {
        if (allprojects.isEmpty()) return

        allprojectsAction.get()?.let {
            it.execute(project)
            return
        }

        // This method is declared thread-safe, but `serializedIsolatedActions` is not treated as a shared resource.
        // Concurrent access to this method is only possible after all projects have been "loaded", which means
        // `isolateFor(Gradle)` has been called. At this point, `serializedIsolatedActions` does not undergo
        // any concurrent mutations.
        // Only way, when `serializedIsolatedActions` can be mutated in this method is using `gradle.allprojects` API,
        // which guarantees sequential execution. Thus, there is no concurrent modification of `serializedIsolatedActions`.
        val newAllprojectAction = Allprojects(project.gradle, getSerializedIsolatedActions(project.gradle))
        if (allprojectsAction.compareAndSet(null, newAllprojectAction)) {
            newAllprojectAction.execute(project)
        } else {
            allprojectsAction.get()?.execute(project)
        }
    }

    override fun clear() {
        beforeProject.clear()
        afterProject.clear()
        allprojects.clear()
        serializedIsolatedActions = null
        allprojectsAction.set(null)
    }

    private
    fun getSerializedIsolatedActions(gradle: Gradle): SerializedIsolatedActionGraph<IsolatedProjectActions> {
        serializedIsolatedActions = serializedIsolatedActions ?: isolateActions(gradle)
        return serializedIsolatedActions!!
    }

    private
    fun isolateActions(gradle: Gradle): SerializedIsolatedActionGraph<IsolatedProjectActions> =
        isolate(
            IsolatedProjectActions(allprojects, beforeProject, afterProject),
            IsolateOwners.OwnerGradle(gradle)
        )

    private
    fun isolate(actions: IsolatedProjectActions, owner: IsolateOwner) =
        IsolatedActionSerializer(owner, owner.serviceOf(), owner.serviceOf())
            .serialize(actions)
}


private
data class IsolatedProjectActions(
    val allprojects: IsolatedProjectActionList,
    val beforeProject: IsolatedProjectActionList,
    val afterProject: IsolatedProjectActionList
)


private
sealed class IsolatedProjectActionsState {
    data class AllprojectsExecuted(
        val beforeProject: IsolatedProjectActionList,
        val afterProject: IsolatedProjectActionList
    ) : IsolatedProjectActionsState()

    data class BeforeProjectExecuted(
        val afterProject: IsolatedProjectActionList
    ) : IsolatedProjectActionsState()

    object AfterProjectExecuted : IsolatedProjectActionsState()

    companion object {
        fun allprojectsExecuted(
            beforeProject: IsolatedProjectActionList,
            afterProject: IsolatedProjectActionList
        ): IsolatedProjectActionsState = AllprojectsExecuted(beforeProject, afterProject)

        fun beforeProjectExecuted(
            beforeProject: IsolatedProjectActionList,
        ): IsolatedProjectActionsState = BeforeProjectExecuted(beforeProject)

        fun afterProjectExecuted(): IsolatedProjectActionsState = AfterProjectExecuted
    }
}


private
class Allprojects(
    private val gradle: Gradle,
    private val isolated: SerializedIsolatedActionGraph<IsolatedProjectActions>
) : IsolatedAction<Project> {

    override fun execute(target: Project) {
        // Execute only if project just loaded
        if (target.peekSingletonProperty<IsolatedProjectActionsState>() != null) return

        val actions = isolatedActions(gradle, isolated)
        val state = IsolatedProjectActionsState.allprojectsExecuted(actions.beforeProject, actions.afterProject)
        target.setSingletonProperty(state)
        executeAll(actions.allprojects, target)
    }
}


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
                executeAll(actions.allprojects, project)
                executeAll(actions.beforeProject, project)
            }

            // Project was configured eagerly by `allprojects`
            is IsolatedProjectActionsState.AllprojectsExecuted -> {
                project.setSingletonProperty(IsolatedProjectActionsState.beforeProjectExecuted(state.afterProject))
                executeAll(state.beforeProject, project)
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
