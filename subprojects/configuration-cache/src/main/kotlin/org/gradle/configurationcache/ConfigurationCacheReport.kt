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

package org.gradle.configurationcache

import groovy.json.JsonOutput
import org.gradle.api.internal.DocumentationRegistry

import org.gradle.configurationcache.problems.PropertyKind
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.problems.buildConsoleSummary
import org.gradle.configurationcache.problems.firstTypeFrom
import org.gradle.configurationcache.problems.taskPathFrom

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL


class ConfigurationCacheReport {

    companion object {

        private
        const val reportHtmlFileName = "configuration-cache-report.html"
    }

    internal
    fun consoleSummaryFor(cacheAction: String, problems: List<PropertyProblem>, htmlReportFile: File) =
        buildConsoleSummary(cacheAction, problems, htmlReportFile)

    /**
     * Writes the report file to [outputDirectory].
     *
     * The file is laid out in such a way as to allow extracting the pure JSON model,
     * see [writeJsReportData].
     */
    internal
    fun writeReportFileTo(outputDirectory: File, cacheAction: String, problems: List<PropertyProblem>): File {
        require(outputDirectory.mkdirs()) {
            "Could not create configuration cache report directory '$outputDirectory'"
        }
        return outputDirectory.resolve(reportHtmlFileName).also { htmlReportFile ->
            val html = javaClass.requireResource(reportHtmlFileName)
            htmlReportFile.bufferedWriter().use { writer ->
                html.openStream().bufferedReader().use { reader ->
                    writer.writeReportFileText(reader, cacheAction, problems)
                }
            }
        }
    }

    private
    fun BufferedWriter.writeReportFileText(htmlReader: BufferedReader, cacheAction: String, problems: List<PropertyProblem>) {
        var dataWritten = false
        htmlReader.forEachLine { line ->
            if (!dataWritten && line.contains("configuration-cache-report-data.js")) {
                appendln("""<script type="text/javascript">""")
                writeJsReportData(cacheAction, problems)
                appendln("</script>")
                dataWritten = true
            } else {
                appendln(line)
            }
        }
        require(dataWritten) { "Didn't write report data, placeholder not found!" }
    }

    /**
     * Writes the report data function.
     *
     * The text is laid out in such a way as to allow extracting the pure JSON model
     * by looking for `// begin-report-data` and `// end-report-data`.
     */
    private
    fun BufferedWriter.writeJsReportData(cacheAction: String, problems: List<PropertyProblem>) {
        appendln("function configurationCacheProblems() { return (")
        appendln("// begin-report-data")
        writeJsonModelFor(cacheAction, problems)
        appendln("// end-report-data")
        appendln(");}")
    }

    private
    fun BufferedWriter.writeJsonModelFor(cacheAction: String, problems: List<PropertyProblem>) {
        val documentationRegistry = DocumentationRegistry()
        appendln("{") // begin JSON
        appendln("\"cacheAction\": \"$cacheAction\",")
        appendln("\"documentationLink\": \"${documentationRegistry.getDocumentationFor("configuration_cache")}\",")
        appendln("\"problems\": [") // begin problems
        problems.forEachIndexed { index, problem ->
            if (index > 0) append(',')
            append(
                JsonOutput.toJson(
                    mapOf(
                        "trace" to traceListOf(problem),
                        "message" to problem.message.fragments,
                        "documentationLink" to problem.documentationSection?.let { documentationRegistry.getDocumentationFor("configuration_cache", it.anchor) },
                        "error" to stackTraceStringOf(problem)
                    )
                )
            )
        }
        appendln("]") // end problems
        appendln("}") // end JSON
    }

    private
    fun Class<*>.requireResource(path: String): URL = getResource(path).also {
        require(it != null) { "Resource `$path` could not be found!" }
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
        is PropertyTrace.BuildLogic -> mapOf(
            "kind" to "BuildLogic",
            "location" to trace.displayName.displayName
        )
        is PropertyTrace.BuildLogicClass -> mapOf(
            "kind" to "BuildLogicClass",
            "type" to trace.name
        )
        PropertyTrace.Gradle -> mapOf(
            "kind" to "Gradle"
        )
        PropertyTrace.Unknown -> mapOf(
            "kind" to "Unknown"
        )
    }
}
