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

package org.gradle.internal.cc.impl.problems

import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.api.logging.Logging
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.DefaultProblemGroup
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.api.problems.internal.PropertyTraceDataSpec
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.cc.impl.ConfigurationCacheAction
import org.gradle.internal.cc.impl.ConfigurationCacheAction.STORE
import org.gradle.internal.cc.impl.ConfigurationCacheAction.UPDATE
import org.gradle.internal.cc.impl.ConfigurationCacheKey
import org.gradle.internal.cc.impl.ConfigurationCacheProblemsException
import org.gradle.internal.cc.impl.TooManyConfigurationCacheProblemsException
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.configuration.problems.CommonReport
import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemReportDetails
import org.gradle.internal.configuration.problems.ProblemReportDetailsJsonSource
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.deprecation.DeprecationMessageBuilder
import org.gradle.internal.deprecation.Documentation
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.problems.buildtree.ProblemReporter
import org.gradle.problems.buildtree.ProblemReporter.ProblemConsumer
import java.io.File


@ServiceScope(Scope.BuildTree::class)
internal
class ConfigurationCacheProblems(

    private
    val startParameter: ConfigurationCacheStartParameter,

    private
    val report: CommonReport,

    private
    val cacheKey: ConfigurationCacheKey,

    private
    val listenerManager: ListenerManager,

    private
    val problemsService: InternalProblems,

    private
    val problemFactory: ProblemFactory,

    private
    val failureFactory: FailureFactory,

    private
    val buildNameProvider: BuildNameProvider
) : AbstractProblemsListener(), ProblemReporter, AutoCloseable {

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
    val incompatibleTasks = newConcurrentHashSet<PropertyTrace>()

    private
    lateinit var cacheAction: ConfigurationCacheAction

    private
    lateinit var cacheActionDescription: StructuredMessage

    val shouldDiscardEntry: Boolean
        get() {
            if (cacheAction is ConfigurationCacheAction.LOAD) {
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

    fun action(action: ConfigurationCacheAction, actionDescription: StructuredMessage) {
        cacheAction = action
        cacheActionDescription = actionDescription
    }

    fun failingBuildDueToSerializationError() {
        isFailingBuildDueToSerializationError = true
        isFailOnProblems = false
    }

    fun projectStateStats(reusedProjects: Int, updatedProjects: Int) {
        this.reusedProjects = reusedProjects
        this.updatedProjects = updatedProjects
    }

    override fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener {
        val notSeenBefore = incompatibleTasks.add(trace)
        if (notSeenBefore) {
            // this method is invoked whenever a problem listener is needed in the context of an incompatible task,
            // report the incompatible task itself the first time only
            reportIncompatibleTask(trace, reason)
        }
        return object : AbstractProblemsListener() {
            override fun onProblem(problem: PropertyProblem) {
                onProblem(problem, ProblemSeverity.Suppressed)
            }

            override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) {
                val failure = failureFactory.create(error)
                onProblem(PropertyProblem(trace, StructuredMessage.build(message), error, failure))
            }
        }
    }

    private
    fun reportIncompatibleTask(trace: PropertyTrace, reason: String) {

        val problem = problemFactory
            .problem {
                message(trace.containingUserCodeMessage)
                text(" is incompatible with the configuration cache. Reason: $reason.")
            }
            .mapLocation {
                trace
            }
            .documentationSection(DocumentationSection.TaskOptOut).build()
        onIncompatibleTask(problem)
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
    val configCacheValidation: ProblemGroup = DefaultProblemGroup("configuration-cache", "configuration cache validation", GradleCoreProblemGroup.validation())

    private
    fun InternalProblems.onProblem(problem: PropertyProblem, severity: ProblemSeverity) {
        val message = problem.message.render()
        internalReporter.create {
            id(
                DeprecationMessageBuilder.createDefaultDeprecationId(message),
                message,
                configCacheValidation
            )
            contextualLabel(message)
            documentOfProblem(problem)
            locationOfProblem(problem)
            severity(severity.toProblemSeverity())
            additionalData(PropertyTraceDataSpec::class.java) {
                trace(problem.trace.containingUserCode)
            }
        }.also { internalReporter.report(it) }
    }

    private
    fun ProblemSpec.documentOfProblem(problem: PropertyProblem) {
        problem.documentationSection?.let {
            documentedAt(Documentation.userManual("configuration_cache", it.anchor).url)
        }
    }

    private
    fun ProblemSpec.locationOfProblem(problem: PropertyProblem) {
        val trace = problem.trace.buildLogic()
        if (trace?.lineNumber != null) {
            lineInFileLocation(trace.source.displayName, trace.lineNumber!!)
        }
    }

    private
    fun PropertyTrace.buildLogic() = sequence.filterIsInstance<PropertyTrace.BuildLogic>().firstOrNull()

    private
    fun ProblemSeverity.toProblemSeverity() = when {
        this == ProblemSeverity.Suppressed -> Severity.ADVICE
        isFailOnProblems -> Severity.ERROR
        else -> Severity.WARNING
    }

    private fun onIncompatibleTask(problem: PropertyProblem) {
        report.onIncompatibleTask(problem)
    }

    override fun getId(): String {
        return "configuration-cache"
    }

    fun queryFailure(summary: Summary = summarizer.get(), htmlReportFile: File? = null): Throwable? {
        val failDueToProblems = summary.failureCount > 0 && isFailOnProblems
        val hasTooManyProblems = hasTooManyProblems(summary)
        val summaryText = { summary.textForConsole(cacheAction.summaryText(), htmlReportFile) }
        return when {
            // TODO - always include this as a build failure;
            //  currently it is disabled when a serialization problem happens
            failDueToProblems -> {
                ConfigurationCacheProblemsException(summary.causes, summaryText)
            }

            hasTooManyProblems -> {
                TooManyConfigurationCacheProblemsException(summary.causes, summaryText)
            }

            else -> null
        }
    }

    /**
     * Writes the report to the given [reportDir] if any [diagnostics][DiagnosticKind] have
     * been reported in which case a warning is also logged with the location of the report.
     */
    override fun report(reportDir: File, validationFailures: ProblemConsumer) {
        val summary = summarizer.get()
        val hasNoProblems = summary.problemCount == 0
        val outputDirectory = outputDirectoryFor(reportDir)
        val details = detailsFor(summary)
        val htmlReportFile = report.writeReportFileTo(outputDirectory, ProblemReportDetailsJsonSource(details))
        if (htmlReportFile == null) {
            // there was nothing to report (no problems, no build configuration inputs)
            require(hasNoProblems)
            return
        }

        when (val failure = queryFailure(summary, htmlReportFile)) {
            null -> {
                val logReportAsInfo = hasNoProblems && !startParameter.alwaysLogReportLinkAsWarning
                val log: (String) -> Unit = if (logReportAsInfo) logger::info else logger::warn
                log(summary.textForConsole(details.cacheAction, htmlReportFile))
            }

            else -> validationFailures.accept(failure)
        }
    }

    private
    fun detailsFor(summary: Summary): ProblemReportDetails {
        val cacheActionText = cacheAction.summaryText()
        val requestedTasks = startParameter.requestedTasksOrDefault()
        return ProblemReportDetails(buildNameProvider.buildName(), cacheActionText, cacheActionDescription, requestedTasks, summary.problemCount)
    }

    private
    fun ConfigurationCacheAction.summaryText() =
        when (this) {
            is ConfigurationCacheAction.LOAD -> "reusing"
            STORE -> "storing"
            UPDATE -> "updating"
        }

    private
    fun ConfigurationCacheStartParameter.requestedTasksOrDefault() =
        requestedTaskNames.takeIf { it.isNotEmpty() }?.joinToString(" ")

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
                cacheAction is ConfigurationCacheAction.LOAD && !hasProblems -> log("Configuration cache entry reused.")
                cacheAction is ConfigurationCacheAction.LOAD -> log("Configuration cache entry reused with {}.", problemCountString)
                hasTooManyProblems -> log("Too many configuration cache problems found ({}).", problemCountString)
                hasProblems -> log("Configuration cache problems found ({}).", problemCountString)
                // else not storing or loading and no problems to report
            }
        }
    }

    private
    fun incompatibleTasksSummary() = when {
        incompatibleTasks.isNotEmpty() -> " because incompatible ${if (incompatibleTasks.size > 1) "tasks were" else "task was"} found: ${incompatibleTasks.joinToString(", ") { "'${it.render()}'" }}."
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
