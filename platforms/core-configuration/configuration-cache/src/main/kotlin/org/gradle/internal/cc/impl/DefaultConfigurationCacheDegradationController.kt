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
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.HoldsProjectState
import org.gradle.api.internal.project.taskfactory.TaskIdentity
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.service.scopes.Scope.BuildTree
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.vcs.internal.VcsMappingsStore
import java.util.concurrent.ConcurrentHashMap

@ServiceScope(BuildTree::class)
internal class DefaultConfigurationCacheDegradationController(
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val vcsMappingsStore: VcsMappingsStore,
    private val buildModelParameters: BuildModelParameters
) : ConfigurationCacheDegradationController, HoldsProjectState {

    private val logger = Logging.getLogger(DefaultConfigurationCacheDegradationController::class.java)
    private val tasksDegradationRequests = ConcurrentHashMap<Task, List<Provider<String>>>()

    val degradationDecision by lazy { collectDegradationReasons() }

    override fun requireConfigurationCacheDegradation(task: Task, reason: Provider<String>) {
        if (!configurationTimeBarrier.isAtConfigurationTime) {
            logger.debug("Configuration cache degradation request of task {} is ignored at execution time", task.path)
            return
        }
        tasksDegradationRequests.compute(task) { _, reasons ->
            reasons?.plus(reason) ?: listOf(reason)
        }
    }

    override fun discardAll() {
        tasksDegradationRequests.clear()
    }

    private fun collectDegradationReasons(): DegradationDecision =
        if (buildModelParameters.isModelBuilding) {
            DegradationDecision.shouldNotDegrade
        } else {
            DegradationDecision(collectTaskDegradationReasons(), collectFeatureDegradationReasons())
        }

    private fun collectTaskDegradationReasons(): Map<TaskIdentity<*>, List<String>> =
        if (tasksDegradationRequests.isNotEmpty()) {
            val builder = ImmutableMap.builderWithExpectedSize<TaskIdentity<*>, List<String>>(tasksDegradationRequests.size)
            for ((task, reasons) in tasksDegradationRequests) {
                if (workGraphContains(task)) {
                    val reasonsInEffect = reasons
                        .mapNotNull { it.orNull }
                        .sorted()

                    if (reasonsInEffect.isNotEmpty()) {
                        builder.put(task.identity, reasonsInEffect)
                    }
                }
            }
            builder.build()
        } else ImmutableMap.of()

    private fun collectFeatureDegradationReasons(): Map<String, List<String>> =
        if (isSourceDependenciesUsed()) {
            ImmutableMap.of(
                "source dependencies",
                listOf("Source dependencies are not compatible yet")
            )
        } else ImmutableMap.of()

    private fun workGraphContains(task: Task): Boolean =
        task.project.gradle.taskGraph.hasTask(task)

    private fun isSourceDependenciesUsed(): Boolean =
        vcsMappingsStore.asResolver().hasRules()

    internal data class DegradationDecision(
        private val taskDegradationReasons: Map<TaskIdentity<*>, List<String>>,
        private val featureDegradationReasons: Map<String, List<String>>
    ) {
        val shouldDegrade: Boolean
            get() = taskDegradationReasons.isNotEmpty() || featureDegradationReasons.isNotEmpty()

        val degradedTaskCount: Int
            get() = taskDegradationReasons.size

        fun degradationReasonForTask(taskIdentity: TaskIdentity<*>): List<String>? = taskDegradationReasons[taskIdentity]

        fun onDegradedTask(consumer: (TaskIdentity<*>, List<String>) -> Unit) {
            taskDegradationReasons.forEach(consumer)
        }

        fun onDegradedFeature(consumer: (String, List<String>) -> Unit) {
            featureDegradationReasons.forEach(consumer)
        }

        companion object {
            val shouldNotDegrade = DegradationDecision(ImmutableMap.of(), ImmutableMap.of())
        }
    }
}

private val Task.identity: TaskIdentity<*>
    get() = (this as TaskInternal).taskIdentity
