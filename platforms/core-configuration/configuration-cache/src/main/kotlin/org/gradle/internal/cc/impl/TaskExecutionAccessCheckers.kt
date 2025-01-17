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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.internal.evaluation.EvaluationContext
import org.gradle.api.internal.tasks.TaskExecutionAccessChecker
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.internal.execution.WorkExecutionTracker


/**
 * A state of the cached configuration, as much as [AbstractTaskProjectAccessChecker] is concerned.
 */
internal
fun interface WorkGraphLoadingState {
    /**
     * If the work graph being executed was loaded from the configuration cache.
     */
    fun isLoadedFromCache(): Boolean
}


internal
abstract class AbstractTaskProjectAccessChecker(
    private val graphLoadingState: WorkGraphLoadingState,
    private val broadcaster: TaskExecutionAccessListener,
    private val workExecutionTracker: WorkExecutionTracker
) : TaskExecutionAccessChecker {
    override fun notifyProjectAccess(task: TaskInternal) {
        if (shouldReportExecutionTimeAccess(task)) {
            broadcaster.onProjectAccess("Task.project", task, currentTask())
        }
    }

    override fun notifyTaskDependenciesAccess(task: TaskInternal, invocationDescription: String) {
        if (shouldReportExecutionTimeAccess(task)) {
            broadcaster.onTaskDependenciesAccess(invocationDescription, task, currentTask())
        }
    }

    override fun notifyConventionAccess(task: TaskInternal, invocationDescription: String) {
        if (shouldReportExecutionTimeAccess(task)) {
            broadcaster.onConventionAccess(invocationDescription, task, currentTask())
        }
    }

    private
    fun currentTask() = workExecutionTracker.currentTask.orElse(null)

    private
    fun shouldReportExecutionTimeAccess(task: TaskInternal): Boolean {
        return isTaskExecutionTime(task) && !currentEvaluationShouldBeReducedByStore()
    }

    /**
     * Checks if the current evaluation could be performed when storing the configuration cache, but happens at execution time because store didn't happen.
     * There are several reasons for this: the configuration cache can be disabled completely, or an incompatible task may be present in task graph.
     * Either way, errors here are false positives: the failing tasks are CC-compatible when CC actually stores them.
     */
    private
    fun currentEvaluationShouldBeReducedByStore() : Boolean {
        // If we've loaded from the cache, then all stores already happened. Everything not reduced by this point should be reported.
        if (graphLoadingState.isLoadedFromCache()) {
            return false
        }
        // Check if we're evaluating something, and it can be reduced to value by CC store.
        // TODO(mlopatkin) This oversimplifies things a lot. We're getting false negatives for e.g. value sources and providers created at execution time.
        //  However, tracking such providers properly has a significant cost, so we prefer it to performance penalty or false positives.
        return EvaluationContext.current().isEvaluating()
    }

    protected
    abstract fun isTaskExecutionTime(task: TaskInternal): Boolean
}


internal
object TaskExecutionAccessCheckers {

    class TaskStateBased(
        graphLoadingState: WorkGraphLoadingState,
        broadcaster: TaskExecutionAccessListener,
        workExecutionTracker: WorkExecutionTracker
    ) : AbstractTaskProjectAccessChecker(graphLoadingState, broadcaster, workExecutionTracker) {
        override fun isTaskExecutionTime(task: TaskInternal): Boolean = task.state.executing
    }

    class ConfigurationTimeBarrierBased(
        private val configurationTimeBarrier: ConfigurationTimeBarrier,
        graphLoadingState: WorkGraphLoadingState,
        broadcaster: TaskExecutionAccessListener,
        workExecutionTracker: WorkExecutionTracker
    ) : AbstractTaskProjectAccessChecker(graphLoadingState, broadcaster, workExecutionTracker) {

        override fun isTaskExecutionTime(task: TaskInternal): Boolean =
            !configurationTimeBarrier.isAtConfigurationTime && !Workarounds.canAccessProjectAtExecutionTime(task)
    }
}
