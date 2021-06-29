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
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.configurationcache.problems.DocumentationSection
import org.gradle.configurationcache.problems.PropertyKind
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.problems.StructuredMessage
import org.gradle.configurationcache.problems.firstTypeFrom
import org.gradle.configurationcache.problems.taskPathFrom
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.TimeUnit


@ServiceScope(Scopes.BuildTree::class)
class ConfigurationCacheReport(
    executorFactory: ExecutorFactory,
    temporaryFileProvider: TemporaryFileProvider
) : Closeable {

    sealed class State {

        open fun onProblem(problem: PropertyProblem): State = illegalState()

        open fun commitReportTo(htmlReportFile: File, cacheAction: String): State = illegalState()

        open fun close(): State = illegalState()

        private
        fun illegalState(): Nothing =
            throw IllegalStateException("Operation is not valid in ${javaClass.simpleName} state.")

        class Idle(
            val executorFactory: ExecutorFactory,
            val temporaryFileProvider: TemporaryFileProvider
        ) : State() {

            override fun onProblem(problem: PropertyProblem): State =
                Spooling(
                    executorFactory.create("Configuration cache report writer", 1),
                    temporaryFileProvider.createTemporaryFile("configuration-cache-report", "html"),
                    CharBuf::class.java.classLoader
                ).onProblem(problem)

            override fun close(): State =
                this
        }

        class Spooling(
            val executor: ManagedExecutor,
            val spoolFile: File,
            /**
             * [JsonModelWriter] uses Groovy's [CharBuf] for fast json encoding.
             */
            val groovyJsonClassLoader: ClassLoader
        ) : State() {

            private
            val writer = HtmlReportWriter(spoolFile.bufferedWriter())

            init {
                executor.submit {
                    Thread.currentThread().contextClassLoader = groovyJsonClassLoader
                    writer.beginHtmlReport()
                }
            }

            override fun onProblem(problem: PropertyProblem): State {
                executor.submit {
                    writer.writeProblem(problem)
                }
                return this
            }

            override fun commitReportTo(htmlReportFile: File, cacheAction: String): State {
                executor.run {
                    submit {
                        writer.endHtmlReport(cacheAction)
                        writer.close()
                        Files.move(spoolFile.toPath(), htmlReportFile.toPath())
                    }
                    shutdown()
                    awaitTermination(3, TimeUnit.SECONDS)
                }
                return Closed
            }

            override fun close(): State {
                executor.run {
                    submit {
                        writer.close()
                    }
                    shutdown()
                }
                return Closed
            }
        }

        object Closed : State() {
            override fun close(): State = this
        }
    }

    private
    var state: State = State.Idle(
        executorFactory,
        temporaryFileProvider
    )

    private
    val stateLock = Object()

    override fun close() {
        withState {
            close()
        }
    }

    fun onProblem(problem: PropertyProblem) {
        withState {
            onProblem(problem)
        }
    }

    private
    inline fun withState(f: State.() -> State) {
        synchronized(stateLock) {
            state = state.f()
        }
    }

    /**
     * Writes the report file to [outputDirectory].
     *
     * The file is laid out in such a way as to allow extracting the pure JSON model,
     * see [HtmlReportWriter].
     */
    internal
    fun writeReportFileTo(outputDirectory: File, cacheAction: String): File {
        require(outputDirectory.mkdirs()) {
            "Could not create configuration cache report directory '$outputDirectory'"
        }
        return outputDirectory.resolve(HtmlReportTemplate.reportHtmlFileName).also { htmlReportFile ->
            withState {
                commitReportTo(htmlReportFile, cacheAction)
            }
        }
    }
}


/**
 * Writes the configuration cache html.
 *
 * The report is laid out in such a way as to allow extracting the pure JSON model
 * by looking for `// begin-report-data` and `// end-report-data`.
 */
internal
class HtmlReportWriter(val writer: BufferedWriter) {

    private
    val jsonModelWriter = JsonModelWriter(writer)

    private
    val htmlTemplate = HtmlReportTemplate.load()

    fun beginHtmlReport() {
        writer.run {
            append(htmlTemplate.first)
            appendLine("""<script type="text/javascript">""")
            appendLine("function configurationCacheProblems() { return (")
            appendLine("// begin-report-data")
        }
        jsonModelWriter.beginModel()
    }

    fun endHtmlReport(cacheAction: String) {
        jsonModelWriter.endModel(cacheAction)
        writer.run {
            appendLine()
            appendLine("// end-report-data")
            appendLine(");}")
            appendLine("</script>")
            append(htmlTemplate.second)
        }
    }

    fun writeProblem(problem: PropertyProblem) {
        jsonModelWriter.writeProblem(problem)
    }

    fun close() {
        writer.close()
    }
}


private
class JsonModelWriter(val writer: BufferedWriter) {

    private
    val documentationRegistry = DocumentationRegistry()

    private
    var first = true

    fun beginModel() {
        beginObject()

        propertyName("problems")
        beginArray()
    }

    fun endModel(cacheAction: String) {
        endArray()

        comma()
        property("cacheAction", cacheAction)
        comma()
        property("documentationLink", documentationRegistry.getDocumentationFor("configuration_cache"))

        endObject()
    }

    fun writeProblem(problem: PropertyProblem) {
        if (first) first = false else comma()
        jsonObject {
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
        beginObject()
        body()
        endObject()
    }

    private
    fun beginObject() {
        write('{')
    }

    private
    fun endObject() {
        write('}')
    }

    private
    inline fun <T> jsonList(list: Iterable<T>, body: (T) -> Unit) {
        beginArray()
        var first = true
        list.forEach {
            if (first) first = false else comma()
            body(it)
        }
        endArray()
    }

    private
    fun beginArray() {
        write('[')
    }

    private
    fun endArray() {
        write(']')
    }

    private
    fun property(name: String, value: String) {
        property(name) { jsonString(value) }
    }

    private
    inline fun property(name: String, value: () -> Unit) {
        propertyName(name)
        value()
    }

    private
    fun propertyName(name: String) {
        simpleString(name)
        write(':')
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
    val stackTraceExtractor = StackTraceExtractor()

    private
    fun stackTraceStringFor(error: Throwable): String =
        stackTraceExtractor.stackTraceStringFor(error)

    private
    fun write(s: String) = writer.append(s)

    private
    fun write(s: Char) = writer.append(s)
}


private
class StackTraceExtractor {

    private
    val stringWriter = StringWriter()

    private
    val printWriter = PrintWriter(stringWriter)

    fun stackTraceStringFor(error: Throwable): String {
        error.printStackTrace(printWriter)
        return stringWriter.toString().also {
            stringWriter.buffer.setLength(0)
        }
    }
}


private
object HtmlReportTemplate {

    const val reportHtmlFileName = "configuration-cache-report.html"

    private
    const val modelLine = """<script type="text/javascript" src="configuration-cache-report-data.js"></script>"""

    /**
     * Returns the header and the footer of the html template as a pair.
     */
    fun load(): Pair<String, String> {
        val template = readHtmlTemplate()
        val headerEnd = template.indexOf(modelLine)
        require(headerEnd > 0) {
            "Invalid configuration cache report template!"
        }
        val header = template.substring(0, headerEnd)
        val footer = template.substring(headerEnd + modelLine.length + 1)
        return header to footer
    }

    private
    fun readHtmlTemplate() =
        ConfigurationCacheReport::class.java
            .requireResource(reportHtmlFileName)
            .openStream()
            .bufferedReader()
            .use(BufferedReader::readText)
}


private
fun Class<*>.requireResource(path: String): URL = getResource(path).let { url ->
    require(url != null) { "Resource `$path` could not be found!" }
    url
}
