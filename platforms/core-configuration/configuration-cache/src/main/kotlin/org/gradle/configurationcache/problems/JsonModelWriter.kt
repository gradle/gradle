/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.problems

import org.apache.groovy.json.internal.CharBuf
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.configuration.problems.documentationLinkFor
import org.gradle.internal.configuration.problems.DecoratedFailure
import org.gradle.internal.configuration.problems.DecoratedPropertyProblem
import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configuration.problems.firstTypeFrom
import org.gradle.internal.configuration.problems.projectPathFrom
import org.gradle.internal.configuration.problems.taskPathFrom
import java.io.Writer


internal
enum class DiagnosticKind {
    PROBLEM,
    INPUT
}


internal
class JsonModelWriter(val writer: Writer) {

    private
    val documentationRegistry = DocumentationRegistry()

    private
    var first = true

    fun beginModel() {
        beginObject()

        propertyName("diagnostics")
        beginArray()
    }

    fun endModel(buildDisplayName: String?, cacheAction: String, requestedTasks: String?, totalProblemCount: Int) {
        endArray()

        comma()
        property("totalProblemCount") {
            write(totalProblemCount.toString())
        }
        if (buildDisplayName != null) {
            comma()
            property("buildName", buildDisplayName)
        }
        if (requestedTasks != null) {
            comma()
            property("requestedTasks", requestedTasks)
        }
        comma()
        property("cacheAction", cacheAction)
        comma()
        property("documentationLink", documentationRegistry.getDocumentationFor("configuration_cache"))

        endObject()
    }

    fun writeDiagnostic(kind: DiagnosticKind, details: DecoratedPropertyProblem) {
        if (first) first = false else comma()
        jsonObject {
            property("trace") {
                jsonObjectList(details.trace.sequence.asIterable()) { trace ->
                    writePropertyTrace(trace)
                }
            }
            comma()
            property(keyFor(kind)) {
                writeStructuredMessage(details.message)
            }
            details.documentationSection?.let {
                comma()
                property("documentationLink", documentationLinkFor(it))
            }
            details.failure?.let { failure ->
                comma()
                writeError(failure)
            }
        }
    }

    private
    fun writeError(failure: DecoratedFailure) {
        val summary = failure.summary
        val parts = failure.parts
        property("error") {
            jsonObject {
                if (summary != null) {
                    property("summary") {
                        writeStructuredMessage(summary)
                    }
                }

                if (parts != null) {
                    if (summary != null) comma()
                    property("parts") {
                        jsonObjectList(parts) { (isInternal, text) ->
                            property(if (isInternal) "internalText" else "text", text)
                        }
                    }
                }
            }
        }
    }

    private
    fun writeStructuredMessage(message: StructuredMessage) {
        jsonObjectList(message.fragments) { fragment ->
            writeFragment(fragment)
        }
    }

    private
    fun keyFor(kind: DiagnosticKind) = when (kind) {
        DiagnosticKind.PROBLEM -> "problem"
        DiagnosticKind.INPUT -> "input"
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

                    PropertyKind.PropertyUsage -> {
                        property("kind", trace.kind.name)
                        comma()
                        property("name", trace.name)
                        comma()
                        property("from", projectPathFrom(trace.trace))
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

            is PropertyTrace.SystemProperty -> {
                property("kind", "SystemProperty")
                comma()
                property("name", trace.name)
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

            is PropertyTrace.Project -> {
                property("kind", "Project")
                comma()
                property("path", trace.path)
            }

            is PropertyTrace.BuildLogic -> {
                property("kind", "BuildLogic")
                comma()
                property("location", trace.source.displayName)
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
        if (value.isEmpty()) {
            write("\"\"")
        } else {
            buffer.addJsonEscapedString(value)
            write(buffer.toStringAndRecycle())
        }
    }

    private
    fun comma() {
        write(',')
    }

    private
    fun documentationLinkFor(section: DocumentationSection) =
        documentationRegistry.documentationLinkFor(section)

    private
    fun write(csq: CharSequence) = writer.append(csq)

    private
    fun write(c: Char) = writer.append(c)
}
