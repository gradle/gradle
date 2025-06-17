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

import com.google.common.collect.ImmutableMap
import org.gradle.api.Task
import org.gradle.api.internal.ConfigurationCacheDegradationController
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.internal.service.scopes.Scope.BuildTree
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.vcs.internal.VcsMappingsStore
import java.util.concurrent.ConcurrentHashMap

@ServiceScope(BuildTree::class)
internal class DefaultConfigurationCacheDegradationController(
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val vcsMappingsStore: VcsMappingsStore
) : ConfigurationCacheDegradationController {

    private val logger = Logging.getLogger(DefaultConfigurationCacheDegradationController::class.java)
    private val tasksDegradationRequests = ConcurrentHashMap<Task, List<Provider<String>>>()
    private var taskDegradationReasons = ImmutableMap.of<Task, List<String>>()
    private var featureDegradationReasons = ImmutableMap.of<String, List<String>>()

    val hasDegradationReasons: Boolean
        get() = taskDegradationReasons.isNotEmpty()
            || featureDegradationReasons.isNotEmpty()

    val hasTaskDegradationReasons: Boolean
        get() = taskDegradationReasons.isNotEmpty()

    val degradedTaskCount: Int
        get() = taskDegradationReasons.size

    fun getDegradationReasonsForTask(task: Task): List<String>? =
        taskDegradationReasons[task]

    fun visitDegradedTasks(consumer: (Task, List<String>) -> Unit) {
        taskDegradationReasons.forEach(consumer)
    }

    fun visitDegradedFeatures(consumer: (String, List<String>) -> Unit) {
        featureDegradationReasons.forEach(consumer)
    }

    override fun requireConfigurationCacheDegradation(task: Task, reason: Provider<String>) {
        if (!configurationTimeBarrier.isAtConfigurationTime) {
            logger.debug("Configuration cache degradation request of task {} is ignored at execution time", task.path)
            return
        }
        tasksDegradationRequests.compute(task) { _, reasons ->
            reasons?.plus(reason) ?: listOf(reason)
        }
    }

    fun collectDegradationReasons() {
        collectFeatureDegradationReasons()
        collectTaskDegradationReasons()
    }

    private fun collectTaskDegradationReasons() {
        if (tasksDegradationRequests.isNotEmpty()) {
            val builder = ImmutableMap.builderWithExpectedSize<Task, List<String>>(tasksDegradationRequests.size)
            for ((task, reasons) in tasksDegradationRequests) {
                if (workGraphContains(task)) {
                    val reasonsInEffect = reasons
                        .mapNotNull { it.orNull }
                        .sorted()

                    if (reasonsInEffect.isNotEmpty()) {
                        builder.put(task, reasonsInEffect)
                    }
                }
            }
            taskDegradationReasons = builder.build()
        }
    }

    private fun collectFeatureDegradationReasons() {
        if (isSourceDependenciesUsed()) {
            featureDegradationReasons = ImmutableMap.of(
                "source dependencies",
                listOf("Source dependencies are not compatible yet")
            )
        }
    }

    private fun workGraphContains(task: Task): Boolean =
        task.project.gradle.taskGraph.hasTask(task)

    private fun isSourceDependenciesUsed(): Boolean =
        vcsMappingsStore.asResolver().hasRules()
}
