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
import org.gradle.internal.Describables
import org.gradle.internal.cc.impl.services.DeferredRootBuildGradle
import org.gradle.internal.model.StateTransitionController
import org.gradle.internal.model.StateTransitionControllerFactory
import org.gradle.internal.service.scopes.Scope.BuildTree
import org.gradle.internal.service.scopes.ServiceScope
import java.util.function.Supplier

@ServiceScope(BuildTree::class)
internal class DefaultConfigurationCacheDegradationController(
    private val deferredRootBuildGradle: DeferredRootBuildGradle,
    stateTransitionControllerFactory: StateTransitionControllerFactory
) : ConfigurationCacheDegradationController {

    private val stateTransitionController = stateTransitionControllerFactory.newController(
        Describables.of("state of CC degradation"),
        State.CollectingDegradationRequests
    )

    private enum class State : StateTransitionController.State {
        CollectingDegradationRequests,
        DegradationDecisionMade
    }

    private val tasksDegradationRequests = HashMap<Task, List<Provider<String>>>()
    val degradationReasons by lazy(::collectDegradationReasons)

    override fun requireConfigurationCacheDegradation(task: Task, reason: Provider<String>) {
        stateTransitionController.inState(State.CollectingDegradationRequests) {
            tasksDegradationRequests.compute(task) { _, reasons -> reasons?.plus(reason) ?: listOf(reason) }
        }
    }

    private fun collectDegradationReasons(): Map<Task, List<String>> =
        stateTransitionController.transition(State.CollectingDegradationRequests, State.DegradationDecisionMade, Supplier {
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
            result
        })
}
