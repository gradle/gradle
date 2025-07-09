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

package org.gradle.internal.cc.impl.promo

import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.TaskInternal
import org.gradle.api.logging.Logging
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * This class handles configuration cache promo message at the end of the build.
 * <p>
 * The promo is shown unless:
 * <ul>
 *     <li>An incompatible API is used at configuration time (in other words, if a CC problem would be emitted by it).</li>
 *     <li>A {@code noCompatibleWithConfigurationCache} task is present in the main task graph.</li>
 * </ul>
 *
 * Instances of this class are thread-safe.
 */
@ServiceScope(Scope.BuildTree::class)
class ConfigurationCachePromoHandler(
    private val buildRegistry: BuildStateRegistry,
    private val documentationRegistry: DocumentationRegistry
) : RootBuildLifecycleListener, ProblemsListener {
    @Volatile
    private var hasProblems = false

    override fun afterStart() {
        // We can't reach out to the task graph when the build is finished.
        // We cannot collect the state of the tasks in the whenReady callback to avoid racing with user-specified ones, which may modify the compatibility state.
        // We cannot listen for the task execution either, because incompatible tasks may not run (e.g. they may have onlyIf {false}).
        // Note that skipping tasks with "-x" excludes them from the graph, so we still nudge. CC behaves similarly.
        buildRegistry.rootBuild.mutableModel.taskGraph.addExecutionListener { graph ->
            hasProblems = hasProblems || graph.hasIncompatibleTasks()
        }
    }

    override fun beforeComplete() {
        if (hasProblems) {
            return
        }

        val docUrl = documentationRegistry.getDocumentationFor("configuration_cache_enabling")
        // TODO(mlopatkin): finalize the message
        Logging.getLogger(ConfigurationCachePromoHandler::class.java).lifecycle("Consider enabling configuration cache to speed up this build: $docUrl")
    }

    override fun onProblem(problem: PropertyProblem) {
        hasProblems = true
    }

    override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) {
        hasProblems = true
    }

    override fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener = this

    override fun forTask(task: Task): ProblemsListener = this

    override fun onExecutionTimeProblem(problem: PropertyProblem) = onProblem(problem)

    private fun TaskExecutionGraph.hasIncompatibleTasks() = allTasks.any { !(it as TaskInternal).isCompatibleWithConfigurationCache }
}
