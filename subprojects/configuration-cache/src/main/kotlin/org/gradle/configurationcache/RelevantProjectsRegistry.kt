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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.execution.plan.Node
import org.gradle.initialization.ProjectAccessHandler
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scopes.Build::class)
class RelevantProjectsRegistry : ProjectAccessHandler {
    private
    val targetProjects = mutableSetOf<ProjectInternal>()

    fun relevantProjects(nodes: List<Node>): List<ProjectInternal> {
        return (targetProjects + nodes.mapNotNullTo(mutableListOf()) { node ->
            node.owningProject
                ?.takeIf { it.parent != null }
        }).toList()
    }

    override fun beforeRequestingTaskByPath(targetProject: ProjectInternal) {
    }

    override fun beforeResolvingProjectDependency(dependencyProject: ProjectInternal) {
        targetProjects.add(dependencyProject)
    }
}
