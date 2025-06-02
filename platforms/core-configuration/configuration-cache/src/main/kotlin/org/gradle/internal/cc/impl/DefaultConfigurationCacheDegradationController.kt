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
import org.gradle.api.provider.Provider
import org.gradle.execution.plan.TaskNode
import org.gradle.internal.cc.impl.services.DeferredRootBuildGradle
import org.gradle.internal.service.scopes.Scope.BuildTree
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.vcs.internal.VcsMappingsStore
import java.util.concurrent.ConcurrentHashMap

@ServiceScope(BuildTree::class)
internal class DefaultConfigurationCacheDegradationController(
    private val vcsMappingsStore: VcsMappingsStore,
    private val deferredRootBuildGradle: DeferredRootBuildGradle
) : ConfigurationCacheDegradationController {

    private val tasksDegradationRequests = ConcurrentHashMap<Task, List<Provider<String>>>()
    val degradationReasons by lazy(::collectDegradationReasons)

    override fun requireConfigurationCacheDegradation(task: Task, reason: Provider<String>) {
        tasksDegradationRequests.compute(task) { _, reasons -> reasons?.plus(reason) ?: listOf(reason) }
    }

    fun shouldDegradeGracefully(): Boolean = degradationReasons.isNotEmpty()

    private fun collectDegradationReasons(): List<DegradationReason> {
        val result = mutableListOf<DegradationReason>()
        if (isSourceDependenciesUsed()) {
            result.add(DegradationReason.BuildLogic("Source dependencies are used"))
        }
        if (tasksDegradationRequests.isNotEmpty()) {
            deferredRootBuildGradle.gradle.taskGraph.visitScheduledNodes { scheduledNodes, _ ->
                scheduledNodes.filterIsInstance<TaskNode>().map { it.task }.forEach { task ->
                    val taskDegradationReasons = tasksDegradationRequests[task]
                        ?.mapNotNull { it.orNull }
                        ?.sorted()

                    if (!taskDegradationReasons.isNullOrEmpty()) {
                        result.add(DegradationReason.Task(task, taskDegradationReasons))
                    }
                }
            }
        }
        return result
    }

    private fun isSourceDependenciesUsed(): Boolean = vcsMappingsStore.asResolver().hasRules()
}

sealed interface DegradationReason {
    data class Task(val task: org.gradle.api.Task, val reasons: List<String>) : DegradationReason
    data class BuildLogic(val reason: String) : DegradationReason
}
