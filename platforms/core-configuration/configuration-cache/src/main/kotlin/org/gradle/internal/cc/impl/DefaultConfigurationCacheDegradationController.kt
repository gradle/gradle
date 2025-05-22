/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.Task
import org.gradle.api.internal.ConfigurationCacheDegradationController
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.TaskInternal
import org.gradle.api.provider.Provider
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.plan.TaskNode
import org.gradle.internal.configuration.problems.PropertyTrace
import java.util.concurrent.ConcurrentHashMap


class DefaultConfigurationCacheDegradationController : ConfigurationCacheDegradationController {

    private val tasksDegradationRequests = ConcurrentHashMap<PropertyTrace, List<Provider<String>>>()
    val currentDegradationReasons = mutableMapOf<PropertyTrace, List<String>>()

    override fun requireConfigurationCacheDegradation(task: Task, reason: Provider<String>) {
        val trace = PropertyTrace.Task(GeneratedSubclasses.unpackType(task), (task as TaskInternal).identityPath.path)
        tasksDegradationRequests.compute(trace) { _, reasons -> reasons?.plus(reason) ?: listOf(reason) }
    }

    fun shouldDegradeGracefully(executionPlan: FinalizedExecutionPlan): Boolean {
        if (tasksDegradationRequests.isEmpty()) return false
        executionPlan.contents.scheduledNodes.visitNodes { scheduled, _ ->
            for (node in scheduled) {
                if (node is TaskNode) {
                    val task = node.task
                    val trace = PropertyTrace.Task(GeneratedSubclasses.unpackType(task), task.identityPath.path)
                    val taskDegradationReasons = tasksDegradationRequests[trace]
                        ?.mapNotNull { it.orNull }
                        ?.sorted()

                    if (!taskDegradationReasons.isNullOrEmpty()) {
                        currentDegradationReasons[trace] = taskDegradationReasons
                    }
                }
            }
        }
        return currentDegradationReasons.isNotEmpty()
    }
}
