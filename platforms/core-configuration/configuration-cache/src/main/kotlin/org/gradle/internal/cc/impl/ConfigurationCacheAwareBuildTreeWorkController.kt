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

import org.gradle.api.logging.Logging
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.execution.EntryTaskSelector
import org.gradle.internal.Try
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeWorkController
import org.gradle.internal.buildtree.BuildTreeWorkExecutor
import org.gradle.internal.buildtree.BuildTreeWorkPreparer
import org.gradle.internal.cc.base.logger
import org.gradle.internal.cc.impl.heap.HeapDumper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class ConfigurationCacheAwareBuildTreeWorkController(
    private val workPreparer: BuildTreeWorkPreparer,
    private val workExecutor: BuildTreeWorkExecutor,
    private val workGraph: BuildTreeWorkGraphController,
    private val cache: BuildTreeConfigurationCache,
    private val buildRegistry: BuildStateRegistry,
    private val buildModelParameters: BuildModelParameters,
    heapDumpDir: String?,
) : BuildTreeWorkController {

    private val heapDumpBaseName = heapDumpDir
        ?.let { path ->
            "$path/${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))}"
        }

    override fun scheduleAndRunRequestedTasks(taskSelector: EntryTaskSelector?): ExecutionResult<Void> {
        val scheduleTaskSelectorPostProcessing: BuildTreeWorkGraphBuilder? = taskSelector?.let { selector ->
            { rootBuildState ->
                addFinalization(rootBuildState, selector::postProcessExecutionPlan)
            }
        }
        return Try.ofFailable {
            val cachedExecutionResult = loadAndRun(scheduleTaskSelectorPostProcessing, taskSelector)
            cachedExecutionResult ?: scheduleStoreAndRun(scheduleTaskSelectorPostProcessing, taskSelector)
        }.getOrMapFailure { ExecutionResult.failed(it) }
    }

    private fun loadAndRun(
        scheduleTaskSelectorPostProcessing: BuildTreeWorkGraphBuilder?,
        taskSelector: EntryTaskSelector?
    ): ExecutionResult<Void>? =
        workGraph.withNewWorkGraph { graph ->
            when (val outcome = cache.maybeLoadRequestedTasks(graph, scheduleTaskSelectorPostProcessing)) {
                is BuildTreeConfigurationCache.LoadOutcome.Reused -> {
                    maybeDumpHeap("cc-hit")
                    workExecutor.execute(outcome.graph)
                }

                BuildTreeConfigurationCache.LoadOutcome.Missed -> null

                is BuildTreeConfigurationCache.LoadOutcome.Discarded ->
                    rescheduleAfterDiscardedEntry(scheduleTaskSelectorPostProcessing, taskSelector, outcome.failure)
            }
        }

    private fun scheduleStoreAndRun(
        scheduleTaskSelectorPostProcessing: BuildTreeWorkGraphBuilder?,
        taskSelector: EntryTaskSelector?
    ): ExecutionResult<Void> {
        val executionResult: ExecutionResult<Void>? = workGraph.withNewWorkGraph { graph ->
            val outcome = cache.scheduleRequestedTasks(graph) { workPreparer.scheduleRequestedTasks(graph, taskSelector) }
            // The model builders can be executed after the tasks (if any) in a build action,
            // and these builders may access project state as well as the task state. Because of that we execute the prepared graph directly.
            if (outcome is BuildTreeConfigurationCache.ScheduleOutcome.Stored && !buildModelParameters.isModelBuilding) {
                // CC miss, graph has been successfully stored. We don't try to execute the graph directly but store it first, discard, and then reload.
                // We don't want to fold the code below here so the "live" graph can be garbage collected before execution.
                null
            } else {
                maybeDumpHeap("cc-hit")
                workExecutor.execute(outcome.graph)
            }
        }
        if (executionResult != null) {
            return executionResult
        }

        maybeDumpHeap("cc-miss-store")
        return storeAndReload(scheduleTaskSelectorPostProcessing)
    }

    private fun rescheduleAfterDiscardedEntry(
        scheduleTaskSelectorPostProcessing: BuildTreeWorkGraphBuilder?,
        taskSelector: EntryTaskSelector?,
        originalFailure: Throwable
    ): ExecutionResult<Void> {
        buildRegistry.resetModels()
        val executionResult = try {
            scheduleStoreAndRun(scheduleTaskSelectorPostProcessing, taskSelector)
        } catch (failure: Throwable) {
            logger.info("Discarding the configuration cache entry after a failed load", failure)
            return ExecutionResult.failed(originalFailure)
        }
        logger.warn("The configuration cache entry could not be loaded and has been discarded.", originalFailure)
        return executionResult
    }

    private fun storeAndReload(scheduleTaskSelectorPostProcessing: BuildTreeWorkGraphBuilder?): ExecutionResult<Void> {
        cache.finalizeCacheEntry()
        buildRegistry.resetModels()

        return workGraph.withNewWorkGraph { graph ->
            val (finalizedGraph, workGraphRestorationFailed) = cache.loadRequestedTasks(graph, scheduleTaskSelectorPostProcessing)
            maybeDumpHeap("cc-miss-load")
            if (workGraphRestorationFailed) {
                // The just-stored graph could not be fully restored, so its state is unreliable and must not be executed.
                // No tasks run, hence no execution-phase failures here; the restoration problem fails the build through
                // the configuration cache problem report at the end of the build (ConfigurationCacheProblems.report).
                ExecutionResult.succeeded()
            } else {
                workExecutor.execute(finalizedGraph)
            }
        }
    }

    private fun maybeDumpHeap(tag: String) {
        heapDumpBaseName?.let {
            val filePath = "$it-$tag.hprof"
            try {
                HeapDumper.dumpHeap(filePath)
            } catch (e: Exception) {
                Logging.getLogger(ConfigurationCacheAwareBuildTreeWorkController::class.java).apply {
                    error("Could not dump heap to file '$filePath'.", e)
                }
            }
        }
    }
}
