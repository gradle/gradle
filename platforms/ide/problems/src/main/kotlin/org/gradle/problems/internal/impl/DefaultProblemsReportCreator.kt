/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.problems.internal.impl

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemLocation
import org.gradle.api.problems.internal.PluginIdLocation
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemReportCreator
import org.gradle.api.problems.internal.ProblemSummaryData
import org.gradle.api.problems.internal.StackTraceLocation
import org.gradle.api.problems.internal.TaskLocation
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.configuration.problems.CommonReport
import org.gradle.internal.configuration.problems.FailureDecorator
import org.gradle.internal.configuration.problems.toJsError
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.problems.internal.report.model.JsLocation
import org.gradle.problems.internal.report.model.JsProblem
import org.gradle.problems.internal.report.model.JsProblemIdElement
import org.gradle.problems.internal.report.model.JsProblemSummary
import org.gradle.problems.internal.report.model.JsProblemsModel
import org.gradle.problems.internal.report.model.ProblemReportJsModel
import java.io.File

private val logger: Logger = Logging.getLogger(DefaultProblemsReportCreator::class.java)

class DefaultProblemsReportCreator(
    executorFactory: ExecutorFactory,
    temporaryFileProvider: TemporaryFileProvider,
    internalOptions: InternalOptions,
    startParameter: StartParameterInternal,
    private val failureFactory: FailureFactory,
    private val buildStateRegistry: BuildStateRegistry,
) : ProblemReportCreator {

    private val report = CommonReport(
        executorFactory,
        temporaryFileProvider,
        internalOptions,
        reportContext = "problems report",
        reportFileName = "problems-report",
        distinctReports = false
    )
    private val taskNames = startParameter.taskNames
    private val failureDecorator = FailureDecorator()
    private val warningMode = startParameter.warningMode

    override fun addProblem(problem: ProblemInternal) {
        report.onProblem(problem.toJsProblem())
    }

    override fun createReportFile(reportDir: File, problemSummaries: List<ProblemSummaryData>) {
        val envelope = JsProblemsModel(
            problemsReport = ProblemReportJsModel(
                buildName = buildStateRegistry.rootBuild.displayName.displayName,
                requestedTasks = taskNames.joinToString(" "),
                documentationLink = DocumentationRegistry().getDocumentationFor("reporting_problems"),
                summaries = problemSummaries.map { it.toJsProblemSummary() },
            )
        )
        val reportFile = report.writeReportFileTo(reportDir.resolve("reports/problems"), envelope)
        if (reportFile != null && warningMode != WarningMode.None) {
            logger.warn(
                "{}[Incubating] Problems report is available at: {}",
                System.lineSeparator(),
                ConsoleRenderer().asClickableFileUrl(reportFile)
            )
        }
    }

    private fun ProblemInternal.toJsProblem(): JsProblem = JsProblem(
        problemId = definition.id.toJsProblemIdElements(),
        documentationLink = definition.documentationLink?.url,
        severity = definition.severity.toString().uppercase(),
        error = exception?.let { failureDecorator.decorate(failureFactory.create(it)).toJsError() },
        problemDetails = details,
        contextualLabel = contextualLabel,
        solutions = solutions.takeIf { it.isNotEmpty() },
        locations = jsLocationsFor(originLocations, contextualLocations),
    )
}

private fun ProblemSummaryData.toJsProblemSummary(): JsProblemSummary =
    JsProblemSummary(problemId = problemId.toJsProblemIdElements(), count = count)

@Suppress("USELESS_ELVIS")
private fun ProblemId.toJsProblemIdElements(): List<JsProblemIdElement> {
    val groups = generateSequence(group) { it.parent }.toList().reversed() + ProblemGroup.create(name, displayName)
    return groups.map { group ->
        JsProblemIdElement(
            name = group.name ?: "<no name provided>",
            displayName = group.displayName ?: "<no display name provided>"
        )
    }
}

private fun jsLocationsFor(
    originLocations: List<ProblemLocation>,
    contextualLocations: List<ProblemLocation>
): List<JsLocation>? =
    (originLocations + contextualLocations)
        .mapNotNull { location -> if (location is StackTraceLocation) location.fileLocation else location }
        .filter { it is FileLocation || it is PluginIdLocation || it is TaskLocation }
        .map { it.toJsLocation() }
        .ifEmpty { null }

private fun ProblemLocation.toJsLocation(): JsLocation = when (this) {
    is LineInFileLocation -> JsLocation(
        path = path,
        line = line.takeIf { it >= 0 },
        column = column.takeIf { it >= 0 },
        length = length.takeIf { it >= 0 },
    )

    is FileLocation -> JsLocation(path = path)

    is PluginIdLocation -> JsLocation(pluginId = pluginId)

    is TaskLocation -> JsLocation(taskPath = buildTreePath)

    else -> error("Unexpected problem location: $this")
}
