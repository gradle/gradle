/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.problems

import org.gradle.api.logging.Logging
import org.gradle.configurationcache.ConfigurationCacheAction
import org.gradle.configurationcache.ConfigurationCacheAction.LOAD
import org.gradle.configurationcache.ConfigurationCacheAction.STORE
import org.gradle.configurationcache.ConfigurationCacheKey
import org.gradle.configurationcache.ConfigurationCacheProblemsException
import org.gradle.configurationcache.ConfigurationCacheReport
import org.gradle.configurationcache.TooManyConfigurationCacheProblemsException
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.problems.buildtree.ProblemReporter
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer


private
const val maxCauses = 5


@ServiceScope(Scopes.BuildTree::class)
class ConfigurationCacheProblems(

    private
    val startParameter: ConfigurationCacheStartParameter,

    private
    val report: ConfigurationCacheReport,

    private
    val cacheKey: ConfigurationCacheKey,

    private
    val listenerManager: ListenerManager

) : ProblemsListener, ProblemReporter, AutoCloseable {
    private
    val postBuildHandler = PostBuildProblemsHandler()

    private
    val problems = CopyOnWriteArrayList<PropertyProblem>()

    private
    var isFailOnProblems = startParameter.failOnProblems

    private
    var isFailingBuildDueToSerializationError = false

    private
    var cacheAction: ConfigurationCacheAction? = null

    private
    var invalidateStoredState: (() -> Unit)? = null

    init {
        listenerManager.addListener(postBuildHandler)
    }

    override fun close() {
        listenerManager.removeListener(postBuildHandler)
    }

    fun storing(invalidateState: () -> Unit) {
        cacheAction = STORE
        invalidateStoredState = invalidateState
    }

    fun loading() {
        cacheAction = LOAD
    }

    fun failingBuildDueToSerializationError() {
        isFailingBuildDueToSerializationError = true
        isFailOnProblems = false
    }

    private
    fun List<PropertyProblem>.causes() = mapNotNull { it.exception }.take(maxCauses)

    override fun onProblem(problem: PropertyProblem) {
        problems.add(problem)
    }

    override fun getId(): String {
        return "configuration-cache"
    }

    override fun report(reportDir: File, validationFailures: Consumer<in Throwable>) {
        if (problems.isEmpty()) {
            return
        }
        val tooManyProblems = problems.size > startParameter.maxProblems
        if (cacheAction == STORE && (isFailOnProblems || tooManyProblems)) {
            // Invalidate stored state if problems fail the build
            requireNotNull(invalidateStoredState).invoke()
        }
        val cacheActionText = cacheAction.summaryText()
        val outputDirectory = outputDirectoryFor(reportDir)
        val htmlReportFile = report.writeReportFileTo(outputDirectory, cacheActionText, problems)
        when {
            isFailOnProblems -> {
                // TODO - always include this as a build failure; currently it is disabled when a serialization problem happens
                validationFailures.accept(newProblemsException(cacheActionText, htmlReportFile))
            }
            tooManyProblems -> {
                validationFailures.accept(newTooManyProblemsException(cacheActionText, htmlReportFile))
            }
            else -> {
                logger.warn(report.consoleSummaryFor(cacheActionText, problems, htmlReportFile))
            }
        }
    }

    private
    fun ConfigurationCacheAction?.summaryText() =
        when (this) {
            null -> "storing"
            LOAD -> "reusing"
            STORE -> "storing"
        }

    private
    fun newProblemsException(cacheActionText: String, htmlReportFile: File) =
        ConfigurationCacheProblemsException(
            problems.causes(),
            cacheActionText,
            problems,
            htmlReportFile
        )

    private
    fun newTooManyProblemsException(cacheActionText: String, htmlReportFile: File) =
        TooManyConfigurationCacheProblemsException(
            problems.causes(),
            cacheActionText,
            problems,
            htmlReportFile
        )

    private
    fun outputDirectoryFor(buildDir: File): File =
        buildDir.resolve("reports/configuration-cache/$cacheKey").let { base ->
            if (!base.exists()) base
            else generateSequence(1) { it + 1 }
                .map { base.resolveSibling("${base.name}-$it") }
                .first { !it.exists() }
        }

    private
    inner class PostBuildProblemsHandler : RootBuildLifecycleListener {

        override fun afterStart() = Unit

        override fun beforeComplete() {
            val hasProblems = problems.isNotEmpty()
            val hasTooManyProblems = problems.size > startParameter.maxProblems
            when {
                isFailingBuildDueToSerializationError && !hasProblems -> log("Configuration cache entry discarded.")
                isFailingBuildDueToSerializationError -> log("Configuration cache entry discarded with {}.", problemCount)
                cacheAction == STORE && isFailOnProblems && hasProblems -> log("Configuration cache entry discarded with {}.", problemCount)
                cacheAction == STORE && hasTooManyProblems -> log("Configuration cache entry discarded with too many problems ({}).", problemCount)
                cacheAction == STORE && !hasProblems -> log("Configuration cache entry stored.")
                cacheAction == STORE -> log("Configuration cache entry stored with {}.", problemCount)
                cacheAction == LOAD && !hasProblems -> log("Configuration cache entry reused.")
                cacheAction == LOAD -> log("Configuration cache entry reused with {}.", problemCount)
                hasTooManyProblems -> log("Too many configuration cache problems found ({}).", problemCount)
                hasProblems -> log("Configuration cache problems found ({}).", problemCount)
                // else not storing or loading and no problems to report
            }
        }

        private
        val problemCount: String
            get() = if (problems.size == 1) "1 problem"
            else "${problems.size} problems"
    }

    private
    fun log(msg: String, vararg args: Any = emptyArray()) {
        logger.warn(msg, *args)
    }

    private
    val logger = Logging.getLogger(ConfigurationCacheProblems::class.java)
}
