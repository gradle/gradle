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

import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.execution.EntryTaskSelector
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeWorkController
import org.gradle.internal.buildtree.BuildTreeWorkExecutor
import org.gradle.internal.buildtree.BuildTreeWorkPreparer


class ConfigurationCacheAwareBuildTreeWorkController(
    private val workPreparer: BuildTreeWorkPreparer,
    private val workExecutor: BuildTreeWorkExecutor,
    private val workGraph: BuildTreeWorkGraphController,
    private val cache: BuildTreeConfigurationCache,
    private val buildRegistry: BuildStateRegistry,
    private val startParameter: BuildModelParameters,
) : BuildTreeWorkController {

    override fun scheduleAndRunRequestedTasks(taskSelector: EntryTaskSelector?): ExecutionResult<Void> {
        val scheduleTaskSelectorPostProcessing: BuildTreeWorkGraphBuilder? = taskSelector?.let { selector ->
            { rootBuildState ->
                addFinalization(rootBuildState, selector::postProcessExecutionPlan)
            }
        }
        val executionResult: ExecutionResult<Void>? = workGraph.withNewWorkGraph { graph ->
            val result = cache.loadOrScheduleRequestedTasks(
                graph = graph,
                graphBuilder = scheduleTaskSelectorPostProcessing
            ) { workPreparer.scheduleRequestedTasks(graph, taskSelector) }
            // There are four outcomes:
            // 1. CC miss, graph has been successfully stored. We don't try to execute the graph directly but store it first, discard, and then reload.
            // 2. Same as (1) but we also need tooling models. The model builders can be executed after the tasks (if any) in a build action,
            //    and these builders may access project state as well as the task state. Because of that we execute the prepared graph directly.
            // 3. CC miss, graph has been configured but the cached state discarded without failing the build (e.g. task.notCompatibleWithCC is used).
            //    We execute the build immediately using the prepared graph.
            // 4. CC hit: we've loaded the cached graph. We execute the build immediately using the loaded graph.
            if (!result.wasLoadedFromCache && !result.entryDiscarded && !startParameter.isRequiresToolingModels) {
                // This is the first outcome of the list above.
                // We don't want to fold the code below here so the "live" graph can be garbage collected before execution.
                null
            } else {
                workExecutor.execute(result.graph)
            }
        }
        if (executionResult != null) {
            // We have executed the work graph already.
            return executionResult
        }

        // Store and reload the graph for the execution.
        cache.finalizeCacheEntry()
        buildRegistry.visitBuilds { build ->
            build.beforeModelReset().rethrow()
        }
        buildRegistry.visitBuilds { build ->
            build.resetModel()
        }

        return workGraph.withNewWorkGraph { graph ->
            val finalizedGraph = cache.loadRequestedTasks(graph, scheduleTaskSelectorPostProcessing)
            workExecutor.execute(finalizedGraph)
        }
    }
}
