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

import org.gradle.BuildResult
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.TaskInternal
import org.gradle.api.logging.Logging
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.initialization.layout.ResolvedBuildLayout
import org.gradle.internal.Factory
import org.gradle.internal.InternalBuildAdapter
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.cc.impl.DefaultConfigurationCacheDegradationController
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * This class handles configuration cache promo message at the end of the build.
 *
 * The promo is shown unless:
 *   - An incompatible API is used at configuration time (in other words, if a CC problem would be emitted by it).
 *   - A `noCompatibleWithConfigurationCache` task is present in the main task graph.
 *   - Graceful degradation is requested.
 *   - Build fails.
 *   - Build runs without a project (`gradle init` or `gradle help` outside project directory)
 *
 * Instances of this class are thread-safe.
 */
@ServiceScope(Scope.BuildTree::class)
internal class ConfigurationCachePromoHandler(
    private val buildRegistry: BuildStateRegistry,
    private val degradationController: DefaultConfigurationCacheDegradationController,
    private val documentationRegistry: DocumentationRegistry
) : RootBuildLifecycleListener, ProblemsListener {
    private val problems = object {
        @Volatile
        private var _seenProblems = false // if ever, only goes false -> true

        fun arePresent(): Boolean = _seenProblems

        fun addIfNeeded(hasNewProblem: Boolean) {
            if (hasNewProblem) {
                _seenProblems = true
            }
        }
    }

    private lateinit var rootBuildLayout: ResolvedBuildLayout

    override fun afterStart() {
        // We can't reach out to the task graph when the build is finished.
        // We cannot collect the state of the tasks in the whenReady callback to avoid racing with user-specified ones, which may modify the compatibility state.
        // We cannot listen for the task execution either, because incompatible tasks may not run (e.g. they may have onlyIf {false}).
        // Note that skipping tasks with "-x" excludes them from the graph, so we still nudge. CC behaves similarly.
        val rootBuildGradle = buildRegistry.rootBuild.mutableModel
        rootBuildGradle.taskGraph.addExecutionListener(this::onRootBuildTaskGraphIsAboutToExecute)

        rootBuildGradle.addBuildListener(object : InternalBuildAdapter() {
            override fun buildFinished(result: BuildResult) {
                problems.addIfNeeded(result.failure != null)
            }
        })

        rootBuildLayout = rootBuildGradle.serviceOf<ResolvedBuildLayout>()
    }

    private fun onRootBuildTaskGraphIsAboutToExecute(graph: TaskExecutionGraph) {
        if (!problems.arePresent()) {
            // Collecting degradation reasons may be somewhat expensive, let's skip it if the build is already incompatible.
            // We can only collect the reasons before the start of the execution phase. CC does that too.

            val hasDegradationReasons = DeprecationLogger.whileDisabled(
            // Collecting degradation reasons uses Task.project call internally, which is deprecated at execution time.
            // We disable deprecations for the computation until we'll have a proper build lifecycle callback.
                Factory {
                    degradationController.degradationDecision.shouldDegrade
                }
            ) ?: false
            problems.addIfNeeded(hasDegradationReasons)
        }

        // Checking task graph for compatibility may be even more expensive, so do it only after collecting the degradation reasons.
        if (!problems.arePresent()) {
            problems.addIfNeeded(graph.hasIncompatibleTasks())
        }
    }

    override fun beforeComplete() {
        if (problems.arePresent() || runWithoutBuildDefinition()) {
            return
        }

        val docUrl = documentationRegistry.getDocumentationFor("configuration_cache_enabling")
        // TODO(mlopatkin): finalize the message
        Logging.getLogger(ConfigurationCachePromoHandler::class.java).lifecycle("Consider enabling configuration cache to speed up this build: $docUrl")
    }

    override fun onProblem(problem: PropertyProblem) {
        problems.addIfNeeded(true)
    }

    override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) {
        problems.addIfNeeded(true)
    }

    override fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener = this

    override fun forTask(task: Task): ProblemsListener = this

    override fun onExecutionTimeProblem(problem: PropertyProblem) = onProblem(problem)

    private fun TaskExecutionGraph.hasIncompatibleTasks() = allTasks.any { !(it as TaskInternal).isCompatibleWithConfigurationCache }

    private fun runWithoutBuildDefinition() = rootBuildLayout.isBuildDefinitionMissing
}
