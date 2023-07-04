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

import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.execution.EntryTaskSelector
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.buildtree.BuildTreeWorkController
import org.gradle.internal.buildtree.BuildTreeWorkExecutor
import org.gradle.internal.buildtree.BuildTreeWorkPreparer


class ConfigurationCacheAwareBuildTreeWorkController(
    private val workPreparer: BuildTreeWorkPreparer,
    private val workExecutor: BuildTreeWorkExecutor,
    private val workGraph: BuildTreeWorkGraphController,
    private val cache: BuildTreeConfigurationCache,
    private val buildRegistry: BuildStateRegistry,
    private val startParameter: ConfigurationCacheStartParameter,
) : BuildTreeWorkController {

    override fun scheduleAndRunRequestedTasks(taskSelector: EntryTaskSelector?): ExecutionResult<Void> {
        val graphBuilder: BuildTreeWorkGraphBuilder? = taskSelector?.let { selector ->
            { buildState ->
                addFinalization(buildState, selector::postProcessExecutionPlan)
            }
        }
        val executionResult = workGraph.withNewWorkGraph { graph ->
            val result = cache.loadOrScheduleRequestedTasks(graph, graphBuilder) {
                workPreparer.scheduleRequestedTasks(graph, taskSelector)
            }
            if (!result.wasLoadedFromCache && !result.entryDiscarded && startParameter.loadAfterStore) {
                // Load the work graph from cache instead
                null
            } else {
                workExecutor.execute(result.graph)
            }
        }
        if (executionResult != null) {
            return executionResult
        }

        cache.finalizeCacheEntry()
        buildRegistry.visitBuilds { build ->
            build.beforeModelReset().rethrow()
        }
        buildRegistry.visitBuilds { build ->
            build.resetModel()
        }

        return workGraph.withNewWorkGraph { graph ->
            val finalizedGraph = cache.loadRequestedTasks(graph, graphBuilder)
            workExecutor.execute(finalizedGraph)
        }
    }
}
