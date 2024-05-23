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

package org.gradle.configurationcache.services

import org.gradle.api.IsolatedAction
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.invocation.Gradle
import org.gradle.internal.extensions.core.popSingletonProperty
import org.gradle.internal.extensions.core.setSingletonProperty
import org.gradle.configurationcache.isolation.IsolatedActionDeserializer
import org.gradle.configurationcache.isolation.IsolatedActionSerializer
import org.gradle.configurationcache.isolation.SerializedIsolatedActionGraph
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.configurationcache.serialization.IsolateOwners
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

    override fun isolateFor(gradle: Gradle): ProjectEvaluationListener? = when {
        beforeProject.isEmpty() && afterProject.isEmpty() -> null
        else -> {
            val isolate = isolate(
                IsolatedProjectActions(beforeProject, afterProject),
                IsolateOwners.OwnerGradle(gradle)
            )
            clear()
            IsolatedProjectEvaluationListener(gradle, isolate)
        }
    }

    override fun clear() {
        beforeProject.clear()
        afterProject.clear()
    }

    private
    fun isolate(actions: IsolatedProjectActions, owner: IsolateOwner) =
        IsolatedActionSerializer(owner, owner.serviceOf(), owner.serviceOf())
            .serialize(actions)
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

    override fun beforeEvaluate(project: Project) {
        val actions = isolatedActions()
        // preserve isolate semantics between `beforeProject` and `afterProject`
        project.setSingletonProperty(actions)
        executeAll(actions.beforeProject, project)
    }

    override fun afterEvaluate(project: Project, state: ProjectState) {
        val actions = project.popSingletonProperty<IsolatedProjectActions>()
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
