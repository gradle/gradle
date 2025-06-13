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
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.execution.plan.TaskNode
import org.gradle.internal.cc.impl.services.DeferredRootBuildGradle
import org.gradle.internal.service.scopes.Scope.BuildTree
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.vcs.internal.VcsMappingsStore
import java.util.concurrent.ConcurrentHashMap

@ServiceScope(BuildTree::class)
internal class DefaultConfigurationCacheDegradationController(
    private val deferredRootBuildGradle: DeferredRootBuildGradle,
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val vcsMappingsStore: VcsMappingsStore
) : ConfigurationCacheDegradationController {

    private val logger = Logging.getLogger(DefaultConfigurationCacheDegradationController::class.java)
    private val tasksDegradationRequests = ConcurrentHashMap<Task, List<Provider<String>>>()
    private val collectedDegradationReasons = mutableMapOf<DegradationContext, List<String>>()

    val hasDegradationReasons: Boolean by lazy { collectedDegradationReasons.isNotEmpty() }

    val hasTaskDegradationReasons: Boolean by lazy {
        collectedDegradationReasons.any {
            e -> e.key is DegradationContext.Task
        }
    }

    val taskDegradationReasons: Map<Task, List<String>> by lazy {
        collectedDegradationReasons
            .filter { e -> e.key is DegradationContext.Task }
            .mapKeys { e -> (e.key as DegradationContext.Task).task }
    }

    val featureDegradationReasons: List<DegradationContext.Feature> by lazy {
        collectedDegradationReasons.keys.filterIsInstance<DegradationContext.Feature>()
    }

    override fun requireConfigurationCacheDegradation(task: Task, reason: Provider<String>) {
        if (!configurationTimeBarrier.isAtConfigurationTime) {
            logger.debug("Configuration cache degradation request of task {} is ignored at execution time", task.path)
            return
        }
        tasksDegradationRequests.compute(task) { _, reasons -> reasons?.plus(reason) ?: listOf(reason) }
    }

    fun collectDegradationReasons() {
        collectFeatureDegradationReasons()
        if (tasksDegradationRequests.isNotEmpty()) {
            deferredRootBuildGradle.gradle.taskGraph.visitScheduledNodes { scheduledNodes, _ ->
                scheduledNodes.filterIsInstance<TaskNode>().map { it.task }.forEach { task ->
                    val taskDegradationReasons = tasksDegradationRequests[task]
                        ?.mapNotNull { evaluateDegradationReason(it) }
                        ?.sorted()

                    if (!taskDegradationReasons.isNullOrEmpty()) {
                        collectedDegradationReasons[DegradationContext.Task(task)] = taskDegradationReasons
                    }
                }
            }
        }
    }

    private fun collectFeatureDegradationReasons() {
        if (isSourceDependenciesUsed()) {
            collectedDegradationReasons[DegradationContext.Feature("source dependencies")] = listOf("Source dependencies are not compatible yet")
        }
    }

    private fun evaluateDegradationReason(request: Provider<String>): String? = request.orNull

    private fun isSourceDependenciesUsed(): Boolean = vcsMappingsStore.asResolver().hasRules()

    sealed interface DegradationContext {
        data class Task(val task: org.gradle.api.Task) : DegradationContext
        data class Feature(val incompatibleFeatureName: String) : DegradationContext
    }

}
