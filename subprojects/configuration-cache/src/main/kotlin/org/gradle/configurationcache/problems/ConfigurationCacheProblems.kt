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
import org.gradle.configurationcache.TooManyConfigurationCacheProblemsException
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.problems.buildtree.ProblemReporter
import java.io.File
import java.util.function.Consumer


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
    val summarizer = ConfigurationCacheProblemsSummary()

    private
    val postBuildHandler = PostBuildProblemsHandler()

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

    override fun onProblem(problem: PropertyProblem) {
        if (summarizer.onProblem(problem)) {
            report.onProblem(problem)
        }
    }

    override fun getId(): String {
        return "configuration-cache"
    }

    override fun report(reportDir: File, validationFailures: Consumer<in Throwable>) {
        val summary = summarizer.get()
        val problemCount = summary.problemCount
        if (problemCount == 0) {
            return
        }
        val hasTooManyProblems = problemCount > startParameter.maxProblems
        if (cacheAction == STORE && (isFailOnProblems || hasTooManyProblems)) {
            // Invalidate stored state if problems fail the build
            requireNotNull(invalidateStoredState).invoke()
        }
        val cacheActionText = cacheAction.summaryText()
        val outputDirectory = outputDirectoryFor(reportDir)
        val htmlReportFile = report.writeReportFileTo(outputDirectory, cacheActionText, problemCount)
        when {
            isFailOnProblems -> {
                // TODO - always include this as a build failure;
                //  currently it is disabled when a serialization problem happens
                validationFailures.accept(
                    ConfigurationCacheProblemsException(summary.causes) {
                        summary.textForConsole(cacheActionText, htmlReportFile)
                    }
                )
            }
            hasTooManyProblems -> {
                validationFailures.accept(
                    TooManyConfigurationCacheProblemsException(summary.causes) {
                        summary.textForConsole(cacheActionText, htmlReportFile)
                    }
                )
            }
            else -> {
                logger.warn(summary.textForConsole(cacheActionText, htmlReportFile))
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
    fun outputDirectoryFor(buildDir: File): File =
        buildDir.resolve("reports/configuration-cache/$cacheKey")

    private
    inner class PostBuildProblemsHandler : RootBuildLifecycleListener {

        override fun afterStart() = Unit

        override fun beforeComplete() {
            val problemCount = summarizer.get().problemCount
            val hasProblems = problemCount > 0
            val hasTooManyProblems = problemCount > startParameter.maxProblems
            val problemCountString = if (problemCount == 1) "1 problem" else "$problemCount problems"
            when {
                isFailingBuildDueToSerializationError && !hasProblems -> log("Configuration cache entry discarded.")
                isFailingBuildDueToSerializationError -> log("Configuration cache entry discarded with {}.", problemCountString)
                cacheAction == STORE && isFailOnProblems && hasProblems -> log("Configuration cache entry discarded with {}.", problemCountString)
                cacheAction == STORE && hasTooManyProblems -> log("Configuration cache entry discarded with too many problems ({}).", problemCountString)
                cacheAction == STORE && !hasProblems -> log("Configuration cache entry stored.")
                cacheAction == STORE -> log("Configuration cache entry stored with {}.", problemCountString)
                cacheAction == LOAD && !hasProblems -> log("Configuration cache entry reused.")
                cacheAction == LOAD -> log("Configuration cache entry reused with {}.", problemCountString)
                hasTooManyProblems -> log("Too many configuration cache problems found ({}).", problemCountString)
                hasProblems -> log("Configuration cache problems found ({}).", problemCountString)
                // else not storing or loading and no problems to report
            }
        }
    }

    private
    fun log(msg: String, vararg args: Any = emptyArray()) {
        logger.warn(msg, *args)
    }

    private
    val logger = Logging.getLogger(ConfigurationCacheProblems::class.java)
}
