/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import groovy.json.JsonOutput

import org.gradle.BuildAdapter
import org.gradle.BuildResult

import org.gradle.api.logging.Logging

import org.gradle.instantexecution.extensions.isOrHasCause
import org.gradle.instantexecution.extensions.maybeUnwrapInvocationTargetException
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.instantexecution.problems.firstTypeFrom
import org.gradle.instantexecution.problems.propertyDescriptionFor
import org.gradle.instantexecution.problems.PropertyKind
import org.gradle.instantexecution.problems.PropertyProblem
import org.gradle.instantexecution.problems.PropertyTrace
import org.gradle.instantexecution.problems.StructuredMessage
import org.gradle.instantexecution.problems.taskPathFrom
import org.gradle.instantexecution.serialization.unknownPropertyError

import org.gradle.internal.event.ListenerManager
import org.gradle.internal.logging.ConsoleRenderer

import org.gradle.util.GFileUtils.copyURLToFile

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList


class InstantExecutionReport(

    private
    val startParameter: InstantExecutionStartParameter,

    listenerManager: ListenerManager

) {

    companion object {

        private
        val logger = Logging.getLogger(InstantExecutionReport::class.java)

        private
        const val reportHtmlFileName = "instant-execution-report.html"
    }

    init {
        listenerManager.addListener(BuildFinishedReporter())
    }

    private
    inner class BuildFinishedReporter : BuildAdapter() {

        override fun buildFinished(result: BuildResult) {
            if (problems.isNotEmpty()) {
                if (result.failure?.isOrHasCause(InstantExecutionException::class) == true) {
                    writeReportFiles()
                } else {
                    val problemsFailure =
                        instantExecutionExceptionForErrors()
                            ?: instantExecutionExceptionForProblems()
                    if (problemsFailure != null) {
                        writeReportFiles()
                        throw problemsFailure
                    } else {
                        logConsoleSummary()
                        writeReportFiles()
                    }
                }
            }
        }
    }

    private
    val problems = CopyOnWriteArrayList<PropertyProblem>()

    fun add(problem: PropertyProblem) {
        problems.add(problem)
        if (problems.size >= startParameter.maxProblems) {
            throw TooManyInstantExecutionProblemsException(buildExceptionSummary(), problems)
        }
    }

    fun withExceptionHandling(onError: () -> Unit = {}, block: () -> Unit) {
        withExceptionHandling(block)?.let { error ->
            onError()
            throw error
        }
    }

    private
    fun withExceptionHandling(block: () -> Unit): Throwable? {

        val fatalError = runWithExceptionHandling(block)

        return when {
            problems.isEmpty() -> {
                require(fatalError == null)
                null
            }
            fatalError != null -> {
                require(fatalError is InstantExecutionException)
                fatalError
            }
            else -> {
                instantExecutionExceptionForErrors()
                    ?: instantExecutionExceptionForProblems()
            }
        }
    }

    private
    fun runWithExceptionHandling(block: () -> Unit): Throwable? {
        try {
            block()
        } catch (e: Throwable) {
            when (val cause = e.maybeUnwrapInvocationTargetException()) {
                is InstantExecutionException -> return cause
                is StackOverflowError -> add(cause)
                is Error -> throw cause
                else -> add(cause)
            }
        }
        return null
    }

    private
    fun add(e: Throwable) {
        problems.add(
            unknownPropertyError(e.message ?: e.javaClass.name, e)
        )
    }

    private
    fun instantExecutionExceptionForErrors(): Throwable? =
        if (errors().isNotEmpty()) InstantExecutionErrorsException(buildExceptionSummary(), problems)
        else null

    private
    fun instantExecutionExceptionForProblems(): Throwable? =
        if (startParameter.failOnProblems) InstantExecutionProblemsException(buildExceptionSummary(), problems)
        else null

    private
    fun errors() =
        problems.asIterable().filterIsInstance<PropertyProblem.Error>()

    private
    val outputDirectory: File by lazy {
        startParameter.rootDirectory.resolve(
            "build/reports/instant-execution/${startParameter.instantExecutionCacheKey}"
        ).let { base ->
            if (!base.exists()) base
            else generateSequence(1) { it + 1 }
                .map { base.resolveSibling("${base.name}-$it") }
                .first { !it.exists() }
        }
    }

    private
    fun logConsoleSummary() {
        logger.warn(buildConsoleSummary())
    }

    private
    fun writeReportFiles() {
        require(outputDirectory.mkdirs()) {
            "Could not create instant execution report directory '$outputDirectory'"
        }
        copyReportResources(outputDirectory)
        writeJsReportData(outputDirectory)
    }

    private
    fun buildExceptionSummary(): String =
        StringBuilder().apply {
            appendln(buildSummaryHeader(uniquePropertyProblems()))
            append(buildSummaryReportLink())
        }.toString()

    private
    fun buildConsoleSummary(): String {
        val uniquePropertyProblems = uniquePropertyProblems()
        return StringBuilder().apply {
            appendln()
            appendln(buildSummaryHeader(uniquePropertyProblems))
            uniquePropertyProblems.forEach { (property, message) ->
                append("  > ")
                append(property)
                append(": ")
                appendln(message)
            }
            appendln(buildSummaryReportLink())
        }.toString()
    }

    private
    fun uniquePropertyProblems(): Set<UniquePropertyProblem> =
        problems.sortedBy { it.trace.sequence.toList().reversed().joinToString(".") }
            .groupBy { propertyDescriptionFor(it.trace) to it.message }
            .keys

    private
    fun buildSummaryHeader(uniquePropertyProblems: Set<UniquePropertyProblem>): String {
        val totalProblemCount = problems.size
        val problemOrProblems = if (totalProblemCount == 1) "problem was" else "problems were"
        val uniqueProblemCount = uniquePropertyProblems.size
        val seemsOrSeem = if (uniqueProblemCount == 1) "seems" else "seem"
        return "$totalProblemCount instant execution $problemOrProblems found, $uniqueProblemCount of which $seemsOrSeem unique."
    }

    private
    fun buildSummaryReportLink() =
        "See the complete report at ${clickableUrlFor(outputDirectory.resolve(reportHtmlFileName))}"

    private
    fun copyReportResources(outputDirectory: File) {
        listOf(
            reportHtmlFileName,
            "instant-execution-report.js",
            "instant-execution-report.css",
            "kotlin.js"
        ).forEach { resourceName ->
            copyURLToFile(
                javaClass.requireResource(resourceName),
                outputDirectory.resolve(resourceName)
            )
        }
    }

    private
    fun writeJsReportData(outputDirectory: File) {
        outputDirectory.resolve("instant-execution-report-data.js").bufferedWriter().use { writer ->
            writer.run {
                appendln("function instantExecutionProblems() { return [")
                problems.forEach {
                    append(
                        JsonOutput.toJson(
                            mapOf(
                                "trace" to traceListOf(it),
                                "message" to it.message.fragments,
                                "error" to stackTraceStringOf(it)
                            )
                        )
                    )
                    appendln(",")
                }
                appendln("];}")
            }
        }
    }

    private
    fun clickableUrlFor(file: File) =
        ConsoleRenderer().asClickableFileUrl(file)

    private
    fun Class<*>.requireResource(path: String): URL = getResource(path).also {
        require(it != null) { "Resource `$path` could not be found!" }
    }

    private
    fun stackTraceStringOf(problem: PropertyProblem): String? =
        StringWriter().also { problem.exception.printStackTrace(PrintWriter(it)) }.toString()

    private
    fun traceListOf(problem: PropertyProblem): List<Map<String, Any>> =
        problem.trace.sequence.map(::traceToMap).toList()

    private
    fun traceToMap(trace: PropertyTrace): Map<String, Any> = when (trace) {
        is PropertyTrace.Property -> {
            when (trace.kind) {
                PropertyKind.Field -> mapOf(
                    "kind" to trace.kind.name,
                    "name" to trace.name,
                    "declaringType" to firstTypeFrom(trace.trace).name
                )
                else -> mapOf(
                    "kind" to trace.kind.name,
                    "name" to trace.name,
                    "task" to taskPathFrom(trace.trace)
                )
            }
        }
        is PropertyTrace.Task -> mapOf(
            "kind" to "Task",
            "path" to trace.path,
            "type" to trace.type.name
        )
        is PropertyTrace.Bean -> mapOf(
            "kind" to "Bean",
            "type" to trace.type.name
        )
        PropertyTrace.Gradle -> mapOf(
            "kind" to "Gradle"
        )
        PropertyTrace.Unknown -> mapOf(
            "kind" to "Unknown"
        )
    }
}


private
typealias UniquePropertyProblem = Pair<String, StructuredMessage>
