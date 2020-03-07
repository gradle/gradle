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

import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.PropertyProblem
import org.gradle.instantexecution.serialization.PropertyTrace
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
                val outputDirectory = calculateOutputDirectory()
                logSummary(outputDirectory)
                writeReportFiles(outputDirectory)
            }
        }

        private
        fun calculateOutputDirectory(): File =
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
    val problems = CopyOnWriteArrayList<PropertyProblem>()

    fun add(problem: PropertyProblem) {
        problems.add(problem)
        if (problems.size >= startParameter.maxProblems) {
            throw TooManyInstantExecutionProblemsException().also { ex ->
                problems.mapNotNull { it.exception }.forEach { ex.addSuppressed(it) }
            }
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

        if (problems.isEmpty()) {
            require(fatalError == null)
            return null
        }

        return fatalError?.withSuppressed(errors())
            ?: instantExecutionExceptionForErrors()
            ?: instantExecutionExceptionForProblems()
    }

    private
    fun runWithExceptionHandling(block: () -> Unit): Throwable? {
        try {
            block()
        } catch (e: Throwable) {
            when (e.cause ?: e) {
                is TooManyInstantExecutionProblemsException -> return e
                is StackOverflowError -> add(e)
                is Error -> throw e
                else -> add(e)
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
        errors()
            .takeIf { it.isNotEmpty() }
            ?.let { errors -> InstantExecutionErrorsException().withSuppressed(errors) }

    private
    fun instantExecutionExceptionForProblems(): Throwable? =
        if (startParameter.failOnProblems) InstantExecutionProblemsException()
        else null

    private
    fun Throwable.withSuppressed(errors: List<PropertyProblem.Error>) = apply {
        errors.forEach {
            addSuppressed(it.exception)
        }
    }

    private
    fun errors() =
        problems.asIterable().filterIsInstance<PropertyProblem.Error>()

    private
    fun logSummary(outputDirectory: File) {
        logger.warn(summary(outputDirectory))
    }

    private
    fun writeReportFiles(outputDirectory: File) {
        require(outputDirectory.mkdirs()) {
            "Could not create instant execution report directory '$outputDirectory'"
        }
        copyReportResources(outputDirectory)
        writeJsReportData(outputDirectory)
    }

    private
    fun summary(outputDirectory: File): String {
        val uniquePropertyProblems = problems
            .sortedBy { it.trace.sequence.toList().reversed().joinToString(".") }
            .groupBy { propertyDescriptionFor(it) to it.message }
            .keys
        return StringBuilder().apply {
            appendln()
            val totalProblemCount = problems.size
            val problemOrProblems = if (totalProblemCount == 1) "problem was" else "problems were"
            val uniqueProblemCount = uniquePropertyProblems.size
            val seemsOrSeem = if (uniqueProblemCount == 1) "seems" else "seem"
            appendln("$totalProblemCount instant execution $problemOrProblems found, $uniqueProblemCount of which $seemsOrSeem unique:")
            uniquePropertyProblems.forEach { (property, message) ->
                append("  - ")
                append(property)
                append(": ")
                appendln(message)
            }
            appendln("See the complete report at ${clickableUrlFor(outputDirectory.resolve(reportHtmlFileName))}")
        }.toString()
    }

    private
    fun copyReportResources(outputDirectory: File) {
        listOf(
            reportHtmlFileName,
            "instant-execution-report.js",
            "instant-execution-report.css",
            "kotlin.js"
        ).forEach { resourceName ->
            copyURLToFile(
                getResource(resourceName),
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
    fun stackTraceStringOf(problem: PropertyProblem): String? =
        problem.exception?.let {
            stackTraceStringFor(it)
        }

    private
    fun stackTraceStringFor(error: Throwable): String =
        StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

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

    private
    fun propertyDescriptionFor(problem: PropertyProblem): String = problem.trace.run {
        when (this) {
            is PropertyTrace.Property -> simplePropertyDescription()
            else -> toString()
        }
    }

    private
    fun getResource(path: String): URL = javaClass.getResource(path).also {
        require(it != null) { "Resource `$path` could not be found!" }
    }

    private
    fun PropertyTrace.Property.simplePropertyDescription(): String = when (kind) {
        PropertyKind.Field -> "field '$name' from type '${firstTypeFrom(trace).name}'"
        else -> "$kind '$name' of '${taskPathFrom(trace)}'"
    }

    private
    fun taskPathFrom(trace: PropertyTrace): String =
        trace.sequence.filterIsInstance<PropertyTrace.Task>().first().path

    private
    fun firstTypeFrom(trace: PropertyTrace): Class<*> =
        trace.sequence.mapNotNull { typeFrom(it) }.first()

    private
    fun typeFrom(trace: PropertyTrace): Class<out Any>? = when (trace) {
        is PropertyTrace.Bean -> trace.type
        is PropertyTrace.Task -> trace.type
        else -> null
    }
}


private
fun clickableUrlFor(file: File) = ConsoleRenderer().asClickableFileUrl(file)
