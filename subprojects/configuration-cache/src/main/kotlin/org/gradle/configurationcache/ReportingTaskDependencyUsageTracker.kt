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

package org.gradle.configurationcache

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDependencyUsageTracker
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.configurationcache.problems.ProblemFactory
import org.gradle.configurationcache.problems.ProblemsListener
import org.gradle.configurationcache.problems.StructuredMessage


/** Reports all usages of the tracked TaskDependency APIs as problems using the [problems] listener.
 * Also checks which tasks in the API return value come from the other projects and tracks the projects coupling using the [coupledProjectsListener]. */
internal
class ReportingTaskDependencyUsageTracker(
    private val referrer: ProjectInternal,
    private val coupledProjectsListener: CoupledProjectsListener,
    private val problems: ProblemsListener,
    private val problemFactory: ProblemFactory
) : TaskDependencyUsageTracker {
    override fun onTaskDependencyUsage(taskDependencies: Set<Task>) {
        checkForCoupledProjects(taskDependencies)
        reportProjectIsolationProblemOnApiUsage()
    }

    private
    fun checkForCoupledProjects(taskDependencies: Set<Task>) {
        taskDependencies.forEach { task ->
            val otherProject = task.project as ProjectInternal
            coupledProjectsListener.onProjectReference(referrer.owner, otherProject.owner)
        }
    }

    private
    fun reportProjectIsolationProblemOnApiUsage() {
        val message = StructuredMessage.build {
            text("Project ")
            reference(referrer.identityPath.toString())
            text(" cannot access task dependencies directly")
        }
        val exception = InvalidUserCodeException(message.toString().capitalized())
        val problem = problemFactory.problem(message, exception)
        problems.onProblem(problem)
    }
}
