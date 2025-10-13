/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.api.internal.artifacts.configurations.ProjectComponentObservationListener
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.execution.plan.Node
import org.gradle.internal.build.BuildState
import org.gradle.internal.service.scopes.ParallelListener
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.Path


@ParallelListener
@ServiceScope(Scope.Build::class)
class RelevantProjectsRegistry(
    private val build: BuildState,
    private val projectStateRegistry: ProjectStateRegistry
) : ProjectComponentObservationListener {

    private
    val targetProjects = newConcurrentHashSet<Path>()

    fun relevantProjects(nodes: List<Node>): Set<ProjectState> {
        val result = mutableSetOf<ProjectState>()
        for (project in targetProjects) {
            collect(projectStateRegistry.stateFor(project), result)
        }
        for (node in nodes) {
            val project = projectStateOf(node)
            if (project != null && isLocalProject(project)) {
                collect(project, result)
            }
        }
        return result
    }

    private
    fun collect(project: ProjectState, projects: MutableSet<ProjectState>) {
        if (!projects.add(project)) {
            return
        }
        val parent = project.parent
        if (parent != null) {
            collect(parent, projects)
        }
    }

    private
    fun projectStateOf(node: Node) = node.owningProject?.owner

    private
    fun isLocalProject(projectState: ProjectState) = projectState.owner === build

    override fun projectObserved(consumingProjectPath: Path?, targetProjectPath: Path) {
        targetProjects.add(targetProjectPath)
    }
}
