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

import org.gradle.api.logging.Logger

import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.PropertyProblem
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.unknownPropertyError

import org.gradle.internal.logging.ConsoleRenderer

import org.gradle.util.GFileUtils.copyURLToFile

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL


class InstantExecutionReport(

    private
    val outputDirectory: File,

    private
    val logger: Logger,

    private
    val maxProblems: Int,

    private
    val failOnProblems: Boolean
) {

    private
    val problems = mutableListOf<PropertyProblem>()

    fun add(problem: PropertyProblem) {
        problems.add(problem)
        if (problems.size >= maxProblems) {
            throw TooManyInstantExecutionProblemsException()
        }
    }

    fun withExceptionHandling(block: () -> Unit): Throwable? {

        val fatalError = runWithExceptionHandling(block)

        if (problems.isEmpty()) {
            require(fatalError == null)
            return null
        }

        logSummary()
        writeReportFiles()

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
        if (failOnProblems) InstantExecutionProblemsException()
        else null

    private
    fun Throwable.withSuppressed(errors: List<PropertyProblem.Error>) = apply {
        errors.forEach {
            addSuppressed(it.exception)
        }
    }

    private
    fun errors() =
        problems.filterIsInstance<PropertyProblem.Error>()

    private
    fun logSummary() {
        logger.warn(summary())
    }

    private
    fun writeReportFiles() {
        outputDirectory.mkdirs()
        copyReportResources()
        writeJsReportData()
    }

    private
    fun summary(): String {
        val uniquePropertyProblems = problems.groupBy {
            propertyDescriptionFor(it) to it.message
        }
        return StringBuilder().apply {
            val totalProblemCount = problems.size
            val problemOrProblems = if (totalProblemCount == 1) "problem was" else "problems were"
            val uniqueProblemCount = uniquePropertyProblems.size
            val seemsOrSeem = if (uniqueProblemCount == 1) "seems" else "seem"
            appendln("$totalProblemCount instant execution $problemOrProblems found, $uniqueProblemCount of which $seemsOrSeem unique:")
            uniquePropertyProblems.keys.forEach { (property, message) ->
                append("  - ")
                append(property)
                append(": ")
                appendln(message)
            }
            appendln("See the complete report at ${clickableUrlFor(reportFile)}")
        }.toString()
    }

    private
    fun copyReportResources() {
        listOf(
            reportFile.name,
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
    fun writeJsReportData() {
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
        (problem as? PropertyProblem.Error)?.exception?.let {
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

    private
    val reportFile
        get() = outputDirectory.resolve("instant-execution-report.html")
}


private
fun clickableUrlFor(file: File) = ConsoleRenderer().asClickableFileUrl(file)
