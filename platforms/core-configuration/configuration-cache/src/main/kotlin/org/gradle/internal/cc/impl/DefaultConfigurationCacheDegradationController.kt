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

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.internal.ConfigurationCacheDegradationController
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.provider.Provider
import org.gradle.execution.plan.TaskNode
import org.gradle.internal.cc.impl.services.DeferredRootBuildGradle
import org.gradle.internal.service.scopes.Scope.BuildTree
import org.gradle.internal.service.scopes.ServiceScope
import java.util.concurrent.ConcurrentHashMap

@ServiceScope(BuildTree::class)
internal class DefaultConfigurationCacheDegradationController(
    private val deferredRootBuildGradle: DeferredRootBuildGradle,
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
) : ConfigurationCacheDegradationController {

    private val tasksDegradationRequests = ConcurrentHashMap<Task, List<Provider<String>>>()
    val degradationReasons by lazy(::collectDegradationReasons)

    override fun requireConfigurationCacheDegradation(task: Task, reason: Provider<String>) {
        if (!configurationTimeBarrier.isAtConfigurationTime) {
            throw GradleException("Configuration cache degradation requests are accepted only at configuration time")
        }
        tasksDegradationRequests.compute(task) { _, reasons -> reasons?.plus(reason) ?: listOf(reason) }
    }

    private fun collectDegradationReasons(): Map<Task, List<String>> {
        val result = mutableMapOf<Task, List<String>>()
        if (tasksDegradationRequests.isNotEmpty()) {
            deferredRootBuildGradle.gradle.taskGraph.visitScheduledNodes { scheduledNodes, _ ->
                scheduledNodes.filterIsInstance<TaskNode>().map { it.task }.forEach { task ->
                    val taskDegradationReasons = tasksDegradationRequests[task]
                        ?.mapNotNull { it.orNull }
                        ?.sorted()

                    if (!taskDegradationReasons.isNullOrEmpty()) {
                        result[task] = taskDegradationReasons
                    }
                }
            }
        }
        return result
    }
}
