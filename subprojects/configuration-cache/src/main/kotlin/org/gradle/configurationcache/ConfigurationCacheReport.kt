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

import org.apache.groovy.json.internal.CharBuf
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.configurationcache.problems.DocumentationSection
import org.gradle.configurationcache.problems.PropertyKind
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.problems.StructuredMessage
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
        // Groovy JSON uses the context classloader to locate various components, so use this class's classloader as the context classloader
        return withContextClassLoader {
            outputDirectory.resolve(reportHtmlFileName).also { htmlReportFile ->
                val html = javaClass.requireResource(reportHtmlFileName)
                htmlReportFile.bufferedWriter().use { writer ->
                    html.openStream().bufferedReader().use { reader ->
                        writer.writeReportFileText(reader, cacheAction, problems)
                    }
                }
            }
        }
    }

    private
    fun <T> withContextClassLoader(action: () -> T): T {
        val currentThread = Thread.currentThread()
        val previous = currentThread.contextClassLoader
        currentThread.contextClassLoader = javaClass.classLoader
        try {
            return action()
        } finally {
            currentThread.contextClassLoader = previous
        }
    }

    private
    fun BufferedWriter.writeReportFileText(htmlReader: BufferedReader, cacheAction: String, problems: List<PropertyProblem>) {
        var dataWritten = false
        htmlReader.forEachLine { line ->
            if (!dataWritten && line.contains("configuration-cache-report-data.js")) {
                appendLine("""<script type="text/javascript">""")
                writeJsReportData(cacheAction, problems)
                appendLine("</script>")
                dataWritten = true
            } else {
                appendLine(line)
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
        appendLine("function configurationCacheProblems() { return (")
        appendLine("// begin-report-data")
        writeJsonModelFor(cacheAction, problems)
        appendLine()
        appendLine("// end-report-data")
        appendLine(");}")
    }

    private
    fun BufferedWriter.writeJsonModelFor(cacheAction: String, problems: List<PropertyProblem>) {
        JsonModelWriter(this, DocumentationRegistry()).write(cacheAction, problems)
    }

    private
    fun Class<*>.requireResource(path: String): URL = getResource(path).also {
        require(it != null) { "Resource `$path` could not be found!" }
    }
}


private
class JsonModelWriter(
    val writer: BufferedWriter,
    val documentationRegistry: DocumentationRegistry
) {
    fun write(cacheAction: String, problems: List<PropertyProblem>) {
        jsonObject {
            property("cacheAction", cacheAction)
            comma()
            property("documentationLink", documentationRegistry.getDocumentationFor("configuration_cache"))
            comma()
            property("problems") {
                jsonObjectList(problems) { problem ->
                    property("trace") {
                        jsonObjectList(problem.trace.sequence.asIterable()) { trace ->
                            writePropertyTrace(trace)
                        }
                    }
                    comma()
                    property("message") {
                        jsonObjectList(problem.message.fragments) { fragment ->
                            writeFragment(fragment)
                        }
                    }
                    problem.documentationSection?.let {
                        comma()
                        property("documentationLink", documentationLinkFor(it))
                    }
                    stackTraceStringOf(problem)?.let {
                        comma()
                        property("error", it)
                    }
                }
            }
        }
    }

    private
    fun writeFragment(fragment: StructuredMessage.Fragment) {
        when (fragment) {
            is StructuredMessage.Fragment.Reference -> property("name", fragment.name)
            is StructuredMessage.Fragment.Text -> property("text", fragment.text)
        }
    }

    private
    fun writePropertyTrace(trace: PropertyTrace) {
        when (trace) {
            is PropertyTrace.Property -> {
                when (trace.kind) {
                    PropertyKind.Field -> {
                        property("kind", trace.kind.name)
                        comma()
                        property("name", trace.name)
                        comma()
                        property("declaringType", firstTypeFrom(trace.trace).name)
                    }
                    else -> {
                        property("kind", trace.kind.name)
                        comma()
                        property("name", trace.name)
                        comma()
                        property("task", taskPathFrom(trace.trace))
                    }
                }
            }
            is PropertyTrace.Task -> {
                property("kind", "Task")
                comma()
                property("path", trace.path)
                comma()
                property("type", trace.type.name)
            }
            is PropertyTrace.Bean -> {
                property("kind", "Bean")
                comma()
                property("type", trace.type.name)
            }
            is PropertyTrace.BuildLogic -> {
                property("kind", "BuildLogic")
                comma()
                property("location", trace.displayName.displayName)
            }
            is PropertyTrace.BuildLogicClass -> {
                property("kind", "BuildLogicClass")
                comma()
                property("type", trace.name)
            }
            PropertyTrace.Gradle -> {
                property("kind", "Gradle")
            }
            PropertyTrace.Unknown -> {
                property("kind", "Unknown")
            }
        }
    }

    private
    inline fun <T> jsonObjectList(list: Iterable<T>, body: (T) -> Unit) {
        jsonList(list) {
            jsonObject {
                body(it)
            }
        }
    }

    private
    inline fun jsonObject(body: () -> Unit) {
        write('{')
        body()
        write('}')
    }

    private
    inline fun <T> jsonList(list: Iterable<T>, body: (T) -> Unit) {
        write('[')
        var first = true
        list.forEach {
            if (first) first = false else comma()
            body(it)
        }
        write(']')
    }

    private
    fun property(name: String, value: String) {
        property(name) { jsonString(value) }
    }

    private
    inline fun property(name: String, value: () -> Unit) {
        simpleString(name)
        write(':')
        value()
    }

    private
    fun simpleString(name: String) {
        write('"')
        write(name)
        write('"')
    }

    private
    val buffer = CharBuf.create(255)

    private
    fun jsonString(value: String) {
        buffer.addJsonEscapedString(value)
        write(buffer.toStringAndRecycle())
    }

    private
    fun comma() {
        write(',')
    }

    private
    fun documentationLinkFor(it: DocumentationSection) =
        documentationRegistry.getDocumentationFor("configuration_cache", it.anchor)

    private
    fun stackTraceStringOf(problem: PropertyProblem): String? =
        problem.exception?.let {
            stackTraceStringFor(it)
        }

    private
    fun stackTraceStringFor(error: Throwable): String =
        StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    private
    fun write(s: String) = writer.append(s)

    private
    fun write(s: Char) = writer.append(s)
}
