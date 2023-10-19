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

import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.api.logging.Logging
import org.gradle.api.problems.ProblemBuilderDefiningCategory
import org.gradle.api.problems.ProblemBuilderDefiningLocation
import org.gradle.api.problems.Problems
import org.gradle.api.problems.Severity
import org.gradle.configurationcache.ConfigurationCacheAction
import org.gradle.configurationcache.ConfigurationCacheAction.LOAD
import org.gradle.configurationcache.ConfigurationCacheAction.STORE
import org.gradle.configurationcache.ConfigurationCacheAction.UPDATE
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
internal
class ConfigurationCacheProblems(

    private
    val startParameter: ConfigurationCacheStartParameter,

    private
    val report: ConfigurationCacheReport,

    private
    val cacheKey: ConfigurationCacheKey,

    private
    val listenerManager: ListenerManager,

    private
    val problemsService: Problems,

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
    var reusedProjects = 0

    private
    var updatedProjects = 0

    private
    var incompatibleTasks = newConcurrentHashSet<String>()

    private
    lateinit var cacheAction: ConfigurationCacheAction

    val shouldDiscardEntry: Boolean
        get() {
            if (cacheAction == LOAD) {
                return false
            }
            if (isFailingBuildDueToSerializationError) {
                return true
            }
            val summary = summarizer.get()
            return discardStateDueToProblems(summary) || hasTooManyProblems(summary)
        }

    init {
        listenerManager.addListener(postBuildHandler)
    }

    override fun close() {
        listenerManager.removeListener(postBuildHandler)
    }

    fun action(action: ConfigurationCacheAction) {
        cacheAction = action
    }

    fun failingBuildDueToSerializationError() {
        isFailingBuildDueToSerializationError = true
        isFailOnProblems = false
    }

    fun projectStateStats(reusedProjects: Int, updatedProjects: Int) {
        this.reusedProjects = reusedProjects
        this.updatedProjects = updatedProjects
    }

    override fun forIncompatibleTask(path: String): ProblemsListener {
        incompatibleTasks.add(path)
        return object : ProblemsListener {
            override fun onProblem(problem: PropertyProblem) {
                onProblem(problem, ProblemSeverity.Suppressed)
            }

            override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) {
                onProblem(PropertyProblem(trace, StructuredMessage.build(message), error))
            }
        }
    }

    override fun onProblem(problem: PropertyProblem) {
        onProblem(problem, ProblemSeverity.Failure)
    }

    private
    fun onProblem(problem: PropertyProblem, severity: ProblemSeverity) {
        if (summarizer.onProblem(problem, severity)) {
            problemsService.onProblem(problem, severity)
            report.onProblem(problem)
        }
    }

    private
    fun Problems.onProblem(problem: PropertyProblem, severity: ProblemSeverity) {
        createProblem { builder ->
            builder.label(problem.message.toString())
                .undocumented()
                .locationOfProblem(problem)
                .category("CC")
                .severity(severity.toProblemSeverity())
        }.report()
    }

    private
    fun ProblemBuilderDefiningLocation.locationOfProblem(problem: PropertyProblem): ProblemBuilderDefiningCategory {
        val trace = problem.trace.buildLogic()
        return when {
            trace?.lineNumber != null ->
                location(trace.source.displayName, trace.lineNumber)
            else ->
                noLocation()
        }
    }

    private
    fun PropertyTrace.buildLogic() = sequence.filterIsInstance<PropertyTrace.BuildLogic>().firstOrNull()

    private
    fun ProblemSeverity.toProblemSeverity() = when (isFailOnProblems) {
        true -> Severity.ERROR
        false -> Severity.WARNING
    }

    override fun getId(): String {
        return "configuration-cache"
    }

    /**
     * Writes the report to the given [reportDir] if any [diagnostics][DiagnosticKind] have
     * been reported in which case a warning is also logged with the location of the report.
     */
    override fun report(reportDir: File, validationFailures: Consumer<in Throwable>) {
        val summary = summarizer.get()
        val failDueToProblems = summary.failureCount > 0 && isFailOnProblems
        val hasTooManyProblems = hasTooManyProblems(summary)
        val hasNoProblems = summary.problemCount == 0
        val outputDirectory = outputDirectoryFor(reportDir)
        val cacheActionText = cacheAction.summaryText()
        val requestedTasks = startParameter.requestedTasksOrDefault()
        val htmlReportFile = report.writeReportFileTo(outputDirectory, cacheActionText, requestedTasks, summary.problemCount)
        if (htmlReportFile == null) {
            // there was nothing to report (no problems, no build configuration inputs)
            require(hasNoProblems)
            return
        }

        when {
            failDueToProblems -> {
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
                val logReportAsInfo = hasNoProblems && !startParameter.alwaysLogReportLinkAsWarning
                val log: (String) -> Unit = if (logReportAsInfo) logger::info else logger::warn
                log(summary.textForConsole(cacheActionText, htmlReportFile))
            }
        }
    }

    private
    fun ConfigurationCacheAction.summaryText() =
        when (this) {
            LOAD -> "reusing"
            STORE -> "storing"
            UPDATE -> "updating"
        }

    private
    fun ConfigurationCacheStartParameter.requestedTasksOrDefault() =
        requestedTaskNames.takeIf { it.isNotEmpty() }?.joinToString(" ") ?: "default tasks"

    private
    fun outputDirectoryFor(buildDir: File): File =
        buildDir.resolve("reports/configuration-cache/$cacheKey")

    private
    inner class PostBuildProblemsHandler : RootBuildLifecycleListener {

        override fun afterStart() = Unit

        override fun beforeComplete() {
            val summary = summarizer.get()
            val problemCount = summary.problemCount
            val hasProblems = problemCount > 0
            val discardStateDueToProblems = discardStateDueToProblems(summary)
            val hasTooManyProblems = hasTooManyProblems(summary)
            val problemCountString = problemCount.counter("problem")
            val reusedProjectsString = reusedProjects.counter("project")
            val updatedProjectsString = updatedProjects.counter("project")
            when {
                isFailingBuildDueToSerializationError && !hasProblems -> log("Configuration cache entry discarded due to serialization error.")
                isFailingBuildDueToSerializationError -> log("Configuration cache entry discarded with {}.", problemCountString)
                cacheAction == STORE && discardStateDueToProblems && !hasProblems -> log("Configuration cache entry discarded${incompatibleTasksSummary()}")
                cacheAction == STORE && discardStateDueToProblems -> log("Configuration cache entry discarded with {}.", problemCountString)
                cacheAction == STORE && hasTooManyProblems -> log("Configuration cache entry discarded with too many problems ({}).", problemCountString)
                cacheAction == STORE && !hasProblems -> log("Configuration cache entry stored.")
                cacheAction == STORE -> log("Configuration cache entry stored with {}.", problemCountString)
                cacheAction == UPDATE && !hasProblems -> log("Configuration cache entry updated for {}, {} up-to-date.", updatedProjectsString, reusedProjectsString)
                cacheAction == UPDATE -> log("Configuration cache entry updated for {} with {}, {} up-to-date.", updatedProjectsString, problemCountString, reusedProjectsString)
                cacheAction == LOAD && !hasProblems -> log("Configuration cache entry reused.")
                cacheAction == LOAD -> log("Configuration cache entry reused with {}.", problemCountString)
                hasTooManyProblems -> log("Too many configuration cache problems found ({}).", problemCountString)
                hasProblems -> log("Configuration cache problems found ({}).", problemCountString)
                // else not storing or loading and no problems to report
            }
        }
    }

    private
    fun incompatibleTasksSummary() = when {
        incompatibleTasks.isNotEmpty() -> " because incompatible ${if (incompatibleTasks.size > 1) "tasks were" else "task was"} found: ${incompatibleTasks.joinToString(", ") { "'$it'" }}."
        else -> "."
    }

    private
    fun discardStateDueToProblems(summary: Summary) =
        (summary.problemCount > 0 || incompatibleTasks.isNotEmpty()) && isFailOnProblems

    private
    fun hasTooManyProblems(summary: Summary) =
        summary.nonSuppressedProblemCount > startParameter.maxProblems

    private
    fun log(msg: String, vararg args: Any = emptyArray()) {
        logger.warn(msg, *args)
    }

    private
    val logger = Logging.getLogger(ConfigurationCacheProblems::class.java)

    private
    fun Int.counter(singular: String, plural: String = "${singular}s"): String {
        return when (this) {
            0 -> "no $plural"
            1 -> "1 $singular"
            else -> "$this $plural"
        }
    }
}
