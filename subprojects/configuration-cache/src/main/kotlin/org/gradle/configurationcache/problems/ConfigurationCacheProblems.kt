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
import org.gradle.configurationcache.ConfigurationCacheReport.Companion.reportHtmlFileName
import org.gradle.configurationcache.TooManyConfigurationCacheProblemsException
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.util.SynchronizedListBuilder
import org.gradle.configurationcache.util.compactHashString
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.hash.Hasher
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.problems.buildtree.ProblemReporter
import java.io.File
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
    val problemListBuilder = SynchronizedListBuilder<PropertyProblem>()

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

    override fun onProblem(problem: PropertyProblem) =
        problemListBuilder.add(problem)

    private
    fun buildProblemList() =
        problemListBuilder.build()

    override fun getId(): String {
        return "configuration-cache"
    }

    override fun report(reportDir: File, validationFailures: Consumer<in Throwable>) {
        val problems = buildProblemList()
        if (problems.isEmpty()) {
            return
        }
        val tooManyProblems = problems.size > startParameter.maxProblems
        if (cacheAction == STORE && (isFailOnProblems || tooManyProblems)) {
            // Invalidate stored state if problems fail the build
            requireNotNull(invalidateStoredState).invoke()
        }
        val cacheActionText = cacheAction.summaryText()
        val htmlReportFile = writeReportTo(reportDir, cacheActionText, problems)
        when {
            isFailOnProblems -> {
                // TODO - always include this as a build failure;
                //  currently it is disabled when a serialization problem happens
                validationFailures.accept(problems.newProblemsException(cacheActionText, htmlReportFile))
            }
            tooManyProblems -> {
                validationFailures.accept(problems.newTooManyProblemsException(cacheActionText, htmlReportFile))
            }
            else -> {
                logger.warn(report.consoleSummaryFor(cacheActionText, problems, htmlReportFile))
            }
        }
    }

    private
    fun writeReportTo(reportDir: File, cacheActionText: String, problems: List<PropertyProblem>): File {
        val contentHash = hashOf(cacheActionText, problems)
        val outputDirectory = reportDir.resolve("reports/configuration-cache/$cacheKey/$contentHash")
        val htmlReportFile = outputDirectory.resolve(reportHtmlFileName)
        if (!htmlReportFile.exists()) {
            require(outputDirectory.mkdirs()) {
                "Could not create configuration cache report directory '$outputDirectory'"
            }
            report.writeReportTo(htmlReportFile, cacheActionText, problems)
        }
        return htmlReportFile
    }

    private
    fun hashOf(cacheActionText: String, problems: List<PropertyProblem>): String =
        compactHashString {
            val appendable = appendableHasher()
            putString(cacheActionText)
            for (problem in problems) {
                for (fragment in problem.message.fragments) {
                    when (fragment) {
                        is StructuredMessage.Fragment.Reference -> putString(fragment.name)
                        is StructuredMessage.Fragment.Text -> putString(fragment.text)
                    }
                }
                for (trace in problem.trace.sequence) {
                    trace.appendStringTo(appendable)
                }
            }
        }

    private
    fun Hasher.appendableHasher() = object : Appendable {
        override fun append(csq: CharSequence): Appendable {
            putString(csq)
            return this
        }

        override fun append(c: Char): Appendable {
            putInt(c.toInt())
            return this
        }

        override fun append(csq: CharSequence, start: Int, end: Int): Appendable =
            throw NotImplementedError()
    }

    private
    fun ConfigurationCacheAction?.summaryText() =
        when (this) {
            null -> "storing"
            LOAD -> "reusing"
            STORE -> "storing"
        }

    private
    fun List<PropertyProblem>.newProblemsException(cacheActionText: String, htmlReportFile: File) =
        ConfigurationCacheProblemsException(
            causes(),
            cacheActionText,
            this,
            htmlReportFile
        )

    private
    fun List<PropertyProblem>.newTooManyProblemsException(cacheActionText: String, htmlReportFile: File) =
        TooManyConfigurationCacheProblemsException(
            causes(),
            cacheActionText,
            this,
            htmlReportFile
        )

    private
    fun List<PropertyProblem>.causes() =
        mapNotNull { it.exception }.take(maxCauses)

    private
    inner class PostBuildProblemsHandler : RootBuildLifecycleListener {

        override fun afterStart() = Unit

        override fun beforeComplete() {
            val problems = buildProblemList()
            val hasProblems = problems.isNotEmpty()
            val hasTooManyProblems = problems.size > startParameter.maxProblems
            val problemCount = if (problems.size == 1) "1 problem" else "${problems.size} problems"
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
    }

    private
    fun log(msg: String, vararg args: Any = emptyArray()) {
        logger.warn(msg, *args)
    }

    private
    val logger = Logging.getLogger(ConfigurationCacheProblems::class.java)
}
