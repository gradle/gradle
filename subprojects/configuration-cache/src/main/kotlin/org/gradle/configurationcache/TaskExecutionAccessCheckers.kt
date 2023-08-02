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

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.tasks.TaskExecutionAccessChecker
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.configurationcache.serialization.Workarounds
import org.gradle.internal.execution.WorkExecutionTracker


abstract class AbstractTaskProjectAccessChecker(
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

    protected
    abstract fun shouldReportExecutionTimeAccess(task: TaskInternal): Boolean
}


object TaskExecutionAccessCheckers {

    class TaskStateBased(broadcaster: TaskExecutionAccessListener, workExecutionTracker: WorkExecutionTracker) : AbstractTaskProjectAccessChecker(broadcaster, workExecutionTracker) {
        override fun shouldReportExecutionTimeAccess(task: TaskInternal): Boolean = task.state.executing
    }

    class ConfigurationTimeBarrierBased(
        private val configurationTimeBarrier: ConfigurationTimeBarrier,
        broadcaster: TaskExecutionAccessListener,
        workExecutionTracker: WorkExecutionTracker
    ) : AbstractTaskProjectAccessChecker(broadcaster, workExecutionTracker) {

        override fun shouldReportExecutionTimeAccess(task: TaskInternal): Boolean =
            !configurationTimeBarrier.isAtConfigurationTime && !Workarounds.canAccessProjectAtExecutionTime(task)
    }
}
