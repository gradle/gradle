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

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.configurationcache.problems.ProblemFactory
import org.gradle.configurationcache.problems.ProblemsListener
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.plan.Node
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.internal.build.ExecutionResult
import org.gradle.util.Path
import java.util.Objects
import java.util.function.Consumer


internal
class CrossProjectConfigurationReportingTaskExecutionGraph(
    taskGraph: TaskExecutionGraphInternal,
    private val referrerProject: ProjectInternal,
    private val problems: ProblemsListener,
    private val crossProjectModelAccess: CrossProjectModelAccess,
    private val coupledProjectsListener: CoupledProjectsListener,
    private val problemFactory: ProblemFactory
) : TaskExecutionGraphInternal {

    private
    val delegate: TaskExecutionGraphInternal = when (taskGraph) {
        // 'unwrapping' ensures that there are no chains of delegation
        is CrossProjectConfigurationReportingTaskExecutionGraph -> taskGraph.delegate
        else -> taskGraph
    }

    override fun addTaskExecutionGraphListener(listener: TaskExecutionGraphListener) {
        delegate.addTaskExecutionGraphListener(listener.wrap())
    }

    override fun removeTaskExecutionGraphListener(listener: TaskExecutionGraphListener) {
        delegate.removeTaskExecutionGraphListener(listener.wrap())
    }

    override fun whenReady(closure: Closure<*>) {
        delegate.whenReady(CrossProjectModelAccessTrackingClosure(closure, referrerProject, crossProjectModelAccess))
    }

    override fun whenReady(action: Action<TaskExecutionGraph>) {
        delegate.whenReady(action.wrap())
    }

    override fun findTask(path: String?): Task? {
        return delegate.findTask(path).also { task ->
            if (task == null) {
                // check whether the path refers to a different project
                val parentPath = path?.let(Path::path)?.parent?.path
                if (parentPath != referrerProject.path) {
                    // even though the task was not found, the current project is coupled with the other project:
                    // if the configuration of that project changes, the result of this call might be different
                    val coupledProjects = listOfNotNull(parentPath?.let { referrerProject.findProject(it) })
                    reportCrossProjectTaskAccess(coupledProjects, path)
                }
            } else {
                checkCrossProjectTaskAccess(task)
            }
        }
    }

    override fun hasTask(path: String): Boolean {
        return findTask(path) != null
    }

    private
    fun checkCrossProjectTaskAccess(task: Task) {
        val taskOwner = task.project as ProjectInternal
        if (!taskOwner.isReferrerProject) {
            val coupledProjects = listOf(taskOwner)
            reportCrossProjectTaskAccess(coupledProjects, task.path)
        }
    }

    override fun hasTask(task: Task): Boolean {
        checkCrossProjectTaskAccess(task)
        return delegate.hasTask(task)
    }

    override fun getAllTasks(): MutableList<Task> {
        val result = delegate.allTasks
        observingTasksMaybeFromOtherProjects(result)
        return result
    }

    override fun getDependencies(task: Task): MutableSet<Task> {
        checkCrossProjectTaskAccess(task)
        val result = delegate.getDependencies(task)
        observingTasksMaybeFromOtherProjects(result)
        return result
    }

    override
    fun getFilteredTasks(): MutableSet<Task> {
        val result = delegate.filteredTasks
        observingTasksMaybeFromOtherProjects(result)
        return result
    }

    private
    fun observingTasksMaybeFromOtherProjects(tasks: Collection<Task>) {
        val otherProjects = tasks.mapNotNullTo(LinkedHashSet(tasks.size / 8)) { task ->
            (task.project as? ProjectInternal)?.takeIf { project -> !project.isReferrerProject }
        }
        reportCrossProjectTaskAccess(otherProjects)
    }

    private
    val Project.isReferrerProject: Boolean
        get() = this is ProjectInternal && identityPath == referrerProject.identityPath

    private
    fun reportCrossProjectTaskAccess(coupledProjects: Iterable<ProjectInternal>, requestPath: String? = null) {
        reportCoupledProjects(coupledProjects)
        reportProjectIsolationProblem(requestPath)
    }

    private
    fun reportCoupledProjects(coupledProjects: Iterable<ProjectInternal>) {
        coupledProjects.forEach { other ->
            coupledProjectsListener.onProjectReference(referrerProject.owner, other.owner)
        }
    }

    private
    fun reportProjectIsolationProblem(requestPath: String?) {
        val problem = problemFactory.problem {
            text("Project ")
            reference(referrerProject.identityPath.toString())
            text(" cannot access the tasks in the task graph that were created by other projects")
        }.exception { message ->
            // As the exception message is not used for grouping, we can safely add the exact task name to it:
            message.capitalized() + if (requestPath != null) "; tried to access '$requestPath'" else '"'
        }.build()
        problems.onProblem(problem)
    }

    private
    fun TaskExecutionGraphListener.wrap() = CrossProjectAccessTrackingTaskExecutionGraphListener(this, referrerProject, crossProjectModelAccess)

    private
    class CrossProjectAccessTrackingTaskExecutionGraphListener(
        private val delegate: TaskExecutionGraphListener,
        private val referrerProject: ProjectInternal,
        private val crossProjectModelAccess: CrossProjectModelAccess
    ) : TaskExecutionGraphListener {

        override fun graphPopulated(graph: TaskExecutionGraph) {
            val wrappedGraph = crossProjectModelAccess.taskGraphForProject(referrerProject, graph as TaskExecutionGraphInternal)
            delegate.graphPopulated(wrappedGraph)
        }

        override fun equals(other: Any?): Boolean =
            (other as? CrossProjectAccessTrackingTaskExecutionGraphListener)?.javaClass == javaClass &&
                other.delegate == delegate &&
                other.referrerProject == referrerProject

        override fun hashCode(): Int = Objects.hash(delegate, referrerProject)
        override fun toString(): String = "CrossProjectAccessTrackingTaskExecutionGraphListener(delegate = $delegate, referrerProject = $referrerProject)"
    }

    private
    fun Action<in TaskExecutionGraph>.wrap(): Action<TaskExecutionGraph> = Action {
        require(this@Action is TaskExecutionGraphInternal) { "Expected the TaskExecutionGraph instance to be TaskExecutionGraphInternal" }
        val wrappedGraph = crossProjectModelAccess.taskGraphForProject(referrerProject, this@Action)
        this@wrap.execute(wrappedGraph)
    }

    // region overridden by delegation

    override fun populate(plan: FinalizedExecutionPlan) {
        delegate.populate(plan)
    }

    override fun execute(plan: FinalizedExecutionPlan): ExecutionResult<Void> =
        delegate.execute(plan)

    override fun visitScheduledNodes(visitor: Consumer<MutableList<Node>>) =
        delegate.visitScheduledNodes(visitor)

    override fun size(): Int = delegate.size()

    @Deprecated("Deprecated in Java")
    override fun addTaskExecutionListener(@Suppress("DEPRECATION") listener: org.gradle.api.execution.TaskExecutionListener) {
        @Suppress("DEPRECATION")
        delegate.addTaskExecutionListener(listener)
    }

    @Deprecated("Deprecated in Java")
    override fun removeTaskExecutionListener(@Suppress("DEPRECATION") listener: org.gradle.api.execution.TaskExecutionListener) {
        @Suppress("DEPRECATION")
        delegate.removeTaskExecutionListener(listener)
    }

    @Deprecated("Deprecated in Java")
    override fun beforeTask(closure: Closure<*>) {
        @Suppress("DEPRECATION")
        delegate.beforeTask(closure)
    }

    @Deprecated("Deprecated in Java")
    override fun beforeTask(action: Action<Task>) {
        @Suppress("DEPRECATION")
        delegate.beforeTask(action)
    }

    @Deprecated("Deprecated in Java")
    override fun afterTask(closure: Closure<*>) {
        @Suppress("DEPRECATION")
        delegate.afterTask(closure)
    }

    @Deprecated("Deprecated in Java")
    override fun afterTask(action: Action<Task>) {
        @Suppress("DEPRECATION")
        delegate.afterTask(action)
    }

    // endregion

    override fun resetState() {
        throw UnsupportedOperationException()
    }
}
