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

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.taskfactory.TaskIdentity
import org.gradle.api.logging.Logging
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.api.problems.internal.PropertyTraceDataSpec
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.cc.base.exceptions.ConfigurationCacheError
import org.gradle.internal.cc.base.exceptions.ConfigurationCacheThrowable
import org.gradle.internal.cc.base.problems.AbstractProblemsListener
import org.gradle.internal.cc.impl.ConfigurationCacheAction
import org.gradle.internal.cc.impl.ConfigurationCacheAction.Load
import org.gradle.internal.cc.impl.ConfigurationCacheAction.SkipStore
import org.gradle.internal.cc.impl.ConfigurationCacheAction.Store
import org.gradle.internal.cc.impl.ConfigurationCacheAction.Update
import org.gradle.internal.cc.impl.ConfigurationCacheKey
import org.gradle.internal.cc.impl.ConfigurationCacheProblemsException
import org.gradle.internal.cc.impl.DefaultConfigurationCacheDegradationController
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
import org.gradle.internal.extensions.stdlib.maybeUnwrapInvocationTargetException
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.problems.buildtree.ProblemReporter
import org.gradle.problems.buildtree.ProblemReporter.ProblemConsumer
import java.io.File
import java.io.IOException


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
    val buildNameProvider: BuildNameProvider,

    private
    val degradationController: DefaultConfigurationCacheDegradationController
) : AbstractProblemsListener(), ProblemReporter, AutoCloseable {

    private
    val summarizer = ConfigurationCacheProblemsSummary()

    private
    val postBuildHandler = PostBuildProblemsHandler()

    private
    val isWarningMode: Boolean = startParameter.isWarningMode

    private
    var seenSerializationErrorOnStore = false

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

    private
    val degradationDecision: DefaultConfigurationCacheDegradationController.DegradationDecision
        get() = degradationController.degradationDecision

    val shouldDiscardEntry: Boolean
        get() {
            // skipping store means there is no entry to be discarded
            require(cacheAction != SkipStore)
            if (cacheAction is Load) {
                return false
            }
            if (seenSerializationErrorOnStore) {
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

    fun onStoreSerializationError() {
        seenSerializationErrorOnStore = true
    }

    fun projectStateStats(reusedProjects: Int, updatedProjects: Int) {
        this.reusedProjects = reusedProjects
        this.updatedProjects = updatedProjects
    }

    override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) {
        // Let IO and configuration cache exceptions surface to the top.
        if (error is IOException || error is ConfigurationCacheThrowable) {
            throw error
        }

        val wrappedMessage = StructuredMessage.build(message)
        val originalError = error.maybeUnwrapInvocationTargetException()
        val wrappedError = ConfigurationCacheError(
            // TODO: the message is not precise, since some errors can happen during load
            "Configuration cache state could not be cached: $trace: ${wrappedMessage.render()}",
            originalError
        )
        val problem = PropertyProblem(trace, wrappedMessage, wrappedError, failureFactory.create(originalError))
        onProblem(problem, ProblemSeverity.Interrupting)
    }

    override fun onExecutionTimeProblem(problem: PropertyProblem) {
        val severity = when {
            isWarningMode -> ProblemSeverity.Deferred
            cacheAction == SkipStore || degradationDecision.shouldDegrade -> ProblemSeverity.SuppressedSilently
            else -> ProblemSeverity.Interrupting
        }
        onProblem(problem, severity)
    }

    override fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener {
        onIncompatibleTask(trace, reason)
        return ErrorsAreProblemsProblemsListener(ProblemSeverity.Suppressed)
    }

    override fun forTask(task: Task): ProblemsListener {
        val degradationReasons = degradationDecision.degradationReasonForTask(task.identity)
        return if (!degradationReasons.isNullOrEmpty()) {
            onIncompatibleTask(
                locationForTask(task.identity),
                degradationReasons.joinToString()
            )
            ErrorsAreProblemsProblemsListener(ProblemSeverity.SuppressedSilently)
        } else this
    }

    fun shouldDegradeGracefully(): Boolean = degradationDecision.shouldDegrade

    private
    val Task.identity: TaskIdentity<*>
        get() = (this as TaskInternal).taskIdentity

    private
    fun onIncompatibleTask(trace: PropertyTrace, reason: String) {
        val notSeenBefore = incompatibleTasks.add(trace)
        if (notSeenBefore) {
            // this method is invoked whenever a problem listener is needed in the context of an incompatible task,
            // report the incompatible task itself the first time only
            reportIncompatibleTask(trace, reason)
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
        report.onIncompatibleTask(problem)
        summarizer.onIncompatibleTask()
    }

    private
    fun reportDegradingFeature(feature: String) {
        val problem = problemFactory
            .problem {
                // for now, we don't expect interesting information from degrading features, so only the feature name is displayed
                text("Feature '$feature' is incompatible with the configuration cache.")
            }
            .build()
        report.onProblem(problem)
        summarizer.onIncompatibleFeature()
    }

    override fun onProblem(problem: PropertyProblem) {
        onProblem(problem, ProblemSeverity.Deferred)
    }

    private
    fun onProblem(problem: PropertyProblem, severity: ProblemSeverity) {
        if (summarizer.onProblem(problem, severity)) {
            problemsService.onProblem(problem, severity)
            report.onProblem(problem)
        }

        if (severity == ProblemSeverity.Interrupting) {
            val exception = problem.exception ?: error("Interrupting problems must have an associated exception. Got: $problem")
            throw exception
        }
    }

    private
    val configCacheValidation: ProblemGroup = ProblemGroup.create("configuration-cache", "configuration cache validation", GradleCoreProblemGroup.validation().thisGroup())

    private
    fun InternalProblems.onProblem(problem: PropertyProblem, severity: ProblemSeverity) {
        val message = problem.message.render()
        internalReporter.internalCreate {
            id(
                DeprecationMessageBuilder.createDefaultDeprecationId(message),
                message,
                configCacheValidation
            )
            contextualLabel(message)
            documentOfProblem(problem)
            locationOfProblem(problem)
            severity(severity.toProblemSeverity())
            additionalDataInternal(PropertyTraceDataSpec::class.java) {
                trace(problem.trace.containingUserCode)
            }
        }.also { internalReporter.report(it) }
    }

    private
    fun ProblemSpec.documentOfProblem(problem: PropertyProblem) {
        problem.documentationSection?.let {
            documentedAt(Documentation.userManual(it.page, it.anchor).url)
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
        this == ProblemSeverity.Suppressed ||
            this == ProblemSeverity.SuppressedSilently -> Severity.ADVICE

        isWarningMode -> Severity.WARNING
        else -> Severity.ERROR
    }

    override fun getId(): String {
        return "configuration-cache"
    }

    fun queryFailure(summary: Summary = summarizer.get(), htmlReportFile: File? = null): Throwable? {
        val failDueToProblems = summary.deferredProblemCount > 0 && !isWarningMode
        val hasTooManyProblems = hasTooManyProblems(summary)
        val summaryText = { summary.textForConsole(cacheAction.summaryText(), htmlReportFile) }
        return when {
            failDueToProblems -> {
                ConfigurationCacheProblemsException(summary.originalProblemExceptions, summaryText)
            }

            hasTooManyProblems -> {
                TooManyConfigurationCacheProblemsException(summary.originalProblemExceptions, summaryText)
            }

            else -> null
        }
    }

    /**
     * Writes the report to the given [reportDir] if any [diagnostics][org.gradle.internal.configuration.problems.DiagnosticKind] have
     * been reported in which case a warning is also logged with the location of the report.
     */
    override fun report(reportDir: File, validationFailures: ProblemConsumer) {
        addNotReportedDegradingTasks()
        addDegradingFeatures()
        val summary = summarizer.get()
        val hasNoProblemsForConsole = summary.reportableProblemCount == 0
        val outputDirectory = outputDirectoryFor(reportDir)
        val details = detailsFor(summary)
        val htmlReportFile = report.writeReportFileTo(outputDirectory, ProblemReportDetailsJsonSource(details))
        val areTaskDegradationReasonsPresent = degradationDecision.degradedTaskCount > 0
        if (htmlReportFile == null) {
            // there was nothing to report (no problems, no build configuration inputs)
            require(summary.totalProblemCount == 0)
            require(!areTaskDegradationReasonsPresent)
            return
        }

        when (val failure = queryFailure(summary, htmlReportFile)) {
            null -> {
                val log: (String) -> Unit = when {
                    shouldDegradeGracefully() -> logger::warn
                    hasNoProblemsForConsole && !startParameter.alwaysLogReportLinkAsWarning -> logger::info
                    else -> logger::warn
                }
                log(summary.textForConsole(details.cacheAction, htmlReportFile))
            }

            else -> validationFailures.accept(failure)
        }
    }

    private
    fun addNotReportedDegradingTasks() {
        degradationDecision.onDegradedTask { taskIdentity, reasons ->
            val trace = locationForTask(taskIdentity)
            if (!incompatibleTasks.contains(trace)) {
                reportIncompatibleTask(trace, reasons.joinToString())
            }
        }
    }

    private
    fun addDegradingFeatures() {
        degradationDecision.onDegradedFeature { feature, _ ->
            // TODO:configuration-cache consider collecting location information (trace)
            reportDegradingFeature(feature)
        }
    }

    private
    fun detailsFor(summary: Summary): ProblemReportDetails {
        val cacheActionText = cacheAction.summaryText()
        val requestedTasks = startParameter.requestedTasksOrDefault()
        return ProblemReportDetails(buildNameProvider.buildName(), cacheActionText, cacheActionDescription, requestedTasks, summary.totalProblemCount)
    }

    private
    fun ConfigurationCacheAction.summaryText() =
        when (this) {
            is Load -> "reusing"
            Store -> "storing"
            SkipStore -> "skipping"
            is Update -> "updating"
        }

    private
    fun ConfigurationCacheStartParameter.requestedTasksOrDefault() =
        requestedTaskNames.takeIf { it.isNotEmpty() }?.joinToString(" ")

    private
    fun outputDirectoryFor(buildDir: File): File =
        startParameter.customReportOutputDirectory?.resolve(cacheKey.toString())
            ?: buildDir.resolve("reports/configuration-cache/$cacheKey")

    private
    inner class PostBuildProblemsHandler : RootBuildLifecycleListener {

        override fun afterStart() = Unit

        @Suppress("CyclomaticComplexMethod")
        override fun beforeComplete(failure: Throwable?) {
            val summary = summarizer.get()
            val reportableProblemCount = summary.reportableProblemCount
            val deferredProblemCount = summary.deferredProblemCount
            val hasProblems = reportableProblemCount > 0
            val discardStateDueToProblems = discardStateDueToProblems(summary)
            val hasTooManyProblems = hasTooManyProblems(summary)
            val problemCountString = reportableProblemCount.counter("problem")
            val reusedProjectsString = reusedProjects.counter("project")
            val updatedProjectsString = updatedProjects.counter("project")
            when {
                seenSerializationErrorOnStore && deferredProblemCount == 0 -> log("Configuration cache entry discarded due to serialization error.")
                seenSerializationErrorOnStore -> log("Configuration cache entry discarded with {}.", problemCountString)
                cacheAction == Store && shouldDegradeGracefully() -> log("Configuration cache disabled${degradationSummary()}")
                cacheAction == Store && discardStateDueToProblems && !hasProblems -> log("Configuration cache entry discarded${incompatibleTasksSummary()}")
                cacheAction == Store && discardStateDueToProblems -> log("Configuration cache entry discarded with {}.", problemCountString)
                cacheAction == Store && hasTooManyProblems -> log("Configuration cache entry discarded with too many problems ({}).", problemCountString)
                cacheAction == Store && !hasProblems -> log("Configuration cache entry stored.")
                cacheAction == Store -> log("Configuration cache entry stored with {}.", problemCountString)
                cacheAction is Update && !hasProblems -> log("Configuration cache entry updated for {}, {} up-to-date.", updatedProjectsString, reusedProjectsString)
                cacheAction is Update -> log("Configuration cache entry updated for {} with {}, {} up-to-date.", updatedProjectsString, problemCountString, reusedProjectsString)
                cacheAction is Load && !hasProblems -> log("Configuration cache entry reused.")
                cacheAction is Load -> log("Configuration cache entry reused with {}.", problemCountString)
                cacheAction == SkipStore -> log("Configuration cache disabled as cache is in read-only mode.")
                hasTooManyProblems -> log("Too many configuration cache problems found ({}).", problemCountString)
                hasProblems -> log("Configuration cache problems found ({}).", problemCountString)
                // else not storing or loading and no problems to report
            }
        }
    }

    private
    fun degradationSummary(): String {
        val degradingFeatures = buildList {
            degradationDecision.onDegradedFeature { feature, _ -> add(feature) }
        }
        return DegradationSummary(degradingFeatures, degradationDecision.degradedTaskCount).render()
    }

    @VisibleForTesting
    internal
    class DegradationSummary(private val degradingFeatures: List<String>, private val degradingTaskCount: Int) {
        init {
            require(degradingFeatures.isNotEmpty() || degradingTaskCount > 0)
        }

        fun render(): String {
            val featuresAsString = degradingFeatures.joinToString().let { "($it)" }
            return " because incompatible " +
                when {
                    degradingTaskCount == 1 && degradingFeatures.isEmpty() -> "task was"
                    degradingTaskCount > 1 && degradingFeatures.isEmpty() -> "tasks were"
                    degradingTaskCount == 0 && degradingFeatures.isNotEmpty() -> "feature usage $featuresAsString was"
                    degradingTaskCount == 1 -> "task and feature usage $featuresAsString were"
                    else -> "tasks and feature usage $featuresAsString were"
                } + " found."
        }
    }

    private
    fun incompatibleTasksSummary() = when {
        incompatibleTasks.isNotEmpty() -> " because incompatible ${if (incompatibleTasks.size > 1) "tasks were" else "task was"} found: ${incompatibleTasks.joinToString(", ") { "'${it.render()}'" }}."
        else -> "."
    }

    private
    fun discardStateDueToProblems(summary: Summary) =
        incompatibleTasks.isNotEmpty() || shouldDegradeGracefully() ||
            summary.reportableProblemCount > 0 && !isWarningMode

    private
    fun hasTooManyProblems(summary: Summary) =
        summary.deferredProblemCount > startParameter.maxProblems

    private
    fun log(msg: String, vararg args: Any = emptyArray()) {
        logger.warn(msg, *args)
    }

    private
    val logger = Logging.getLogger(ConfigurationCacheProblems::class.java)

    private
    fun locationForTask(taskIdentity: TaskIdentity<*>) = PropertyTrace.Task(taskIdentity.taskType, taskIdentity.buildTreePath.asString())

    private
    fun Int.counter(singular: String, plural: String = "${singular}s"): String {
        return when (this) {
            0 -> "no $plural"
            1 -> "1 $singular"
            else -> "$this $plural"
        }
    }

    private
    inner class ErrorsAreProblemsProblemsListener(
        private
        val problemSeverity: ProblemSeverity
    ) : AbstractProblemsListener() {

        override fun onProblem(problem: PropertyProblem) {
            onProblem(problem, problemSeverity)
        }

        override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) {
            val failure = failureFactory.create(error)
            onProblem(PropertyProblem(trace, StructuredMessage.build(message), error, failure))
        }

        override fun onExecutionTimeProblem(problem: PropertyProblem) {
            onProblem(problem)
        }
    }
}
