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

package org.gradle.configurationcache

import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.ProjectDependencyObservedListener
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedProjectConfiguration
import org.gradle.api.internal.project.ProjectState
import org.gradle.execution.plan.Node
import org.gradle.internal.build.BuildState
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scopes.Build::class)
class RelevantProjectsRegistry(
    private val build: BuildState
) : ProjectDependencyObservedListener {
    private
    val targetProjects = mutableSetOf<ProjectState>()

    fun relevantProjects(nodes: List<Node>): Set<ProjectState> =
        targetProjects +
            nodes.mapNotNullTo(mutableListOf()) { node ->
                val project = node.owningProject
                if (project != null && project.owner.owner != build) {
                    null
                } else {
                    node.owningProject?.owner
                }
            }

    override fun dependencyObserved(consumingProject: ProjectState?, targetProject: ProjectState, requestedState: ConfigurationInternal.InternalState, target: ResolvedProjectConfiguration) {
        targetProjects.add(targetProject)
    }
}
