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

import com.google.common.collect.ImmutableList
import org.gradle.api.IsolatedAction
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.invocation.IsolatedProjectEvaluationListenerProvider


private
typealias IsolatedProjectAction = IsolatedAction<in Project>


internal
class DefaultIsolatedProjectEvaluationListenerProvider : IsolatedProjectEvaluationListenerProvider {

    private
    val actions = mutableListOf<IsolatedProjectAction>()

    override fun beforeProject(action: IsolatedProjectAction) {
        actions.add(action)
    }

    override fun isolate(): ProjectEvaluationListener? = when {
        actions.isEmpty() -> null
        else -> IsolatedProjectEvaluationListener(ImmutableList.copyOf(actions).also { actions.clear() })
    }

    override fun clear() {
        actions.clear()
    }
}


private
class IsolatedProjectEvaluationListener(private val isolated: ImmutableList<IsolatedProjectAction>) : ProjectEvaluationListener {
    override fun beforeEvaluate(project: Project) {
        for (action in isolated) {
            action.execute(project)
        }
    }

    override fun afterEvaluate(project: Project, state: ProjectState) {
    }
}
