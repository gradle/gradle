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
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemLocation
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.InternalProblem
import org.gradle.api.problems.internal.PluginIdLocation
import org.gradle.api.problems.internal.ProblemReportCreator
import org.gradle.api.problems.internal.ProblemSummaryData
import org.gradle.api.problems.internal.StackTraceLocation
import org.gradle.api.problems.internal.TaskLocation
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.cc.impl.problems.BuildNameProvider
import org.gradle.internal.cc.impl.problems.JsonSource
import org.gradle.internal.cc.impl.problems.JsonWriter
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.configuration.problems.CommonReport
import org.gradle.internal.configuration.problems.FailureDecorator
import org.gradle.internal.configuration.problems.writeError
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.internal.problems.failure.FailureFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private val logger: Logger = Logging.getLogger(DefaultProblemsReportCreator::class.java)

class DefaultProblemsReportCreator(
    executorFactory: ExecutorFactory,
    temporaryFileProvider: TemporaryFileProvider,
    internalOptions: InternalOptions,
    startParameter: StartParameterInternal,
    private val failureFactory: FailureFactory,
    private val buildNameProvider: BuildNameProvider
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
    private val problemCount = AtomicInteger(0)
    private val failureDecorator = FailureDecorator()

    override fun addProblem(problem: InternalProblem) {
        problemCount.incrementAndGet()
        report.onProblem(JsonProblemWriter(problem, failureDecorator, failureFactory))
    }

    override fun createReportFile(reportDir: File, problemSummaries: List<ProblemSummaryData>) {
        val reportFile = report.writeReportFileTo(reportDir.resolve("reports/problems"), object : JsonSource {
            override fun writeToJson(jsonWriter: JsonWriter) = with(jsonWriter) {
                property("problemsReport") {
                    jsonObject {
                        writeTotalProblemCount()
                        writeBuildName()
                        writeRequestedTasks()
                        writeDocumentationLink()
                        writeSummaries(problemSummaries)
                    }
                }
            }
        })
        if (reportFile != null) {
            logger.warn(
                "{}[Incubating] Problems report is available at: {}",
                System.lineSeparator(),
                ConsoleRenderer().asClickableFileUrl(reportFile)
            )
        }
    }

    private fun JsonWriter.writeTotalProblemCount() {
        property("totalProblemCount", problemCount.get())
    }

    private fun JsonWriter.writeBuildName() {
        buildNameProvider.buildName()?.let { name ->
            property("buildName", name)
        }
    }

    private fun JsonWriter.writeRequestedTasks() {
        property("requestedTasks", taskNames.joinToString(" "))
    }

    private fun JsonWriter.writeDocumentationLink() {
        property("documentationLink", DocumentationRegistry().getDocumentationFor("reporting_problems"))
    }

    private fun JsonWriter.writeSummaries(problemSummaries: List<ProblemSummaryData>) {
        property("summaries") {
            jsonList(problemSummaries) {
                jsonObject {
                    writeProblemId(it.problemId)
                    property("count", it.count)
                }
            }
        }
    }
}

internal class JsonProblemWriter(
    private val problem: InternalProblem,
    private val failureDecorator: FailureDecorator,
    private val failureFactory: FailureFactory,
) : JsonSource {

    override fun writeToJson(jsonWriter: JsonWriter) = with(jsonWriter) {
        jsonObject {
            writeProblemId(problem.definition.id)
            writeSeverity(problem.definition.severity)
            writeContextualLabel(problem.contextualLabel)
            writeDetails(problem.details)
            writeDocumentationLink(problem.definition.documentationLink)
            writeException(problem.exception)
            writeLocations(problem.originLocations, problem.contextualLocations)
            writeSolutions(problem.solutions)
        }
    }

    private fun JsonWriter.writeSeverity(severity: Severity) {
        property("severity", severity.toString().uppercase())
    }

    private fun JsonWriter.writeContextualLabel(contextualLabel: String?) {
        if (contextualLabel != null) {
            property("contextualLabel", contextualLabel)
        }
    }

    private fun JsonWriter.writeDetails(details: String?) {
        if (details != null) {
            property("problemDetails", details)
        }
    }

    private fun JsonWriter.writeDocumentationLink(docLink: DocLink?) {
        if (docLink != null) {
            property("documentationLink", docLink.url)
        }
    }

    private fun JsonWriter.writeException(exception: Throwable?) {
        if (exception != null) {
            writeError(failureDecorator.decorate(failureFactory.create(exception)))
        }
    }

    private fun JsonWriter.writeLocations(originLocations: List<ProblemLocation>, contextualLocations: List<ProblemLocation>) {
        val locations = (originLocations + contextualLocations)
            .mapNotNull { location -> if (location is StackTraceLocation) location.fileLocation else location }
            .filter { it is FileLocation || it is PluginIdLocation || it is TaskLocation }
        if (locations.isNotEmpty()) {
            property("locations") {
                jsonObjectList(locations) { location ->
                    when (location) {
                        is FileLocation -> writeFileLocation(location)
                        is PluginIdLocation -> property("pluginId", location.pluginId)
                        is TaskLocation -> property("taskPath", location.buildTreePath)
                    }
                }
            }
        }
    }

    private fun JsonWriter.writeFileLocation(location: FileLocation) {
        property("path", location.path)
        if (location is LineInFileLocation) {
            if (location.line >= 0) property("line", location.line)
            if (location.column >= 0) property("column", location.column)
            if (location.length >= 0) property("length", location.length)
        }
    }

    private fun JsonWriter.writeSolutions(solutions: List<String>) {
        if (solutions.isNotEmpty()) {
            property("solutions") {
                jsonList(solutions)
            }
        }
    }
}

@Suppress("USELESS_ELVIS")
private fun JsonWriter.writeProblemId(id: ProblemId) {
    property("problemId") {
        val list = generateSequence(id.group) { it.parent }.toList().reversed() + ProblemGroup.create(id.name, id.displayName)
        jsonObjectList(list) { group ->
            property("name", group.name ?: "<no name provided>")
            property("displayName", group.displayName ?: "<no display name provided>")
        }
    }
}
