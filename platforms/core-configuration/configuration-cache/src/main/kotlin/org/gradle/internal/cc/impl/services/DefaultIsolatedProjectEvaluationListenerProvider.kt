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
import org.gradle.internal.extensions.core.popSingletonProperty
import org.gradle.internal.extensions.core.setSingletonProperty
import org.gradle.internal.cc.impl.isolation.IsolatedActionDeserializer
import org.gradle.internal.cc.impl.isolation.IsolatedActionSerializer
import org.gradle.internal.cc.impl.isolation.SerializedIsolatedActionGraph
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.invocation.IsolatedProjectEvaluationListenerProvider


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
    val projectsActions = mutableMapOf<String, IsolatedProjectActions>()

    private
    var serializedIsolatedActions: SerializedIsolatedActionGraph<IsolatedProjectActions>? = null

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
        serializedIsolatedActions != null -> serializedIsolatedActions
            ?.let { IsolatedProjectEvaluationListener(gradle, projectsActions, it) }
            ?: isolateFor(gradle)

        beforeProject.isEmpty() && afterProject.isEmpty() && allprojects.isEmpty() -> null
        else -> {
            val isolated = isolateActions(gradle)
            serializedIsolatedActions = isolated
            IsolatedProjectEvaluationListener(gradle, projectsActions, isolated)
        }
    }

    override fun isolateAllprojectsActionFor(gradle: Gradle): IsolatedAction<in Project>? = when {
        allprojects.isEmpty() -> null
        else -> {
            val isolated = serializedIsolatedActions ?: isolateActions(gradle)
            Allprojects(gradle, projectsActions, isolated)
        }
    }

    override fun clear() {
        beforeProject.clear()
        afterProject.clear()
        allprojects.clear()
        projectsActions.clear()
        serializedIsolatedActions = null
    }

    private
    fun isolateActions(owner: Gradle): SerializedIsolatedActionGraph<IsolatedProjectActions> =
        isolate(
            IsolatedProjectActions(allprojects, beforeProject, afterProject),
            IsolateOwners.OwnerGradle(owner)
        )

    private
    fun <T : Any> isolate(what: T, owner: IsolateOwner) =
        IsolatedActionSerializer(owner, owner.serviceOf(), owner.serviceOf())
            .serialize(what)
}


private
data class IsolatedProjectActions(
    val allprojects: IsolatedProjectActionList,
    val beforeProject: IsolatedProjectActionList,
    val afterProject: IsolatedProjectActionList
)


private
class Allprojects(
    private val gradle: Gradle,
    private val projectsActions: MutableMap<String, IsolatedProjectActions>,
    private val isolated: SerializedIsolatedActionGraph<IsolatedProjectActions>
) : IsolatedAction<Project> {

    override fun execute(target: Project) {
        // Check if allprojects already been applied
        if (projectsActions[target.path] != null) return

        val actions = IsolateOwners.OwnerGradle(gradle).let { owner ->
            IsolatedActionDeserializer(owner, owner.serviceOf(), owner.serviceOf())
                .deserialize(isolated)
        }
        projectsActions[target.path] = actions
        actions.allprojects.forEach { it.execute(target) }
    }
}


private
class IsolatedProjectEvaluationListener(
    private val gradle: Gradle,
    private val projectsActions: MutableMap<String, IsolatedProjectActions>,
    private val isolated: SerializedIsolatedActionGraph<IsolatedProjectActions>
) : ProjectEvaluationListener {

    override fun beforeEvaluate(project: Project) =
        when (val isolatedProjectActions = projectsActions[project.path]) {
            null -> {
                val actions = isolatedActions()
                // preserve isolate semantics between `beforeProject` and `afterProject`
                projectsActions[project.path] = actions

                project.setSingletonProperty(actions)
                executeAll(actions.allprojects, project)
                executeAll(actions.beforeProject, project)
            }

            // Project was configured eagerly by `allprojects`
            else -> {
                project.setSingletonProperty(isolatedProjectActions)
                executeAll(isolatedProjectActions.beforeProject, project)
            }
        }

    override fun afterEvaluate(project: Project, state: ProjectState) {
        val actions = project.popSingletonProperty<IsolatedProjectActions>()
        projectsActions.remove(project.path)
        require(actions != null) {
            "afterEvaluate action cannot execute before beforeEvaluate"
        }
        executeAll(actions.afterProject, project)
    }

    private
    fun executeAll(actions: IsolatedProjectActionList, project: Project) {
        for (action in actions) {
            action.execute(project)
        }
    }

    private
    fun isolatedActions() = IsolateOwners.OwnerGradle(gradle).let { owner ->
        IsolatedActionDeserializer(owner, owner.serviceOf(), owner.serviceOf())
            .deserialize(isolated)
    }
}
