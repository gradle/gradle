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

    fun endModel(cacheAction: String, requestedTasks: String, totalProblemCount: Int) {
        endArray()

        comma()
        property("totalProblemCount") {
            write(totalProblemCount.toString())
        }
        comma()
        property("cacheAction", cacheAction)
        comma()
        property("requestedTasks", requestedTasks)
        comma()
        property("documentationLink", documentationRegistry.getDocumentationFor("configuration_cache"))

        endObject()
    }

    fun writeDiagnostic(kind: DiagnosticKind, details: PropertyProblem) {
        if (first) first = false else comma()
        jsonObject {
            property("trace") {
                jsonObjectList(details.trace.sequence.asIterable()) { trace ->
                    writePropertyTrace(trace)
                }
            }
            comma()
            property(keyFor(kind)) {
                jsonObjectList(details.message.fragments) { fragment ->
                    writeFragment(fragment)
                }
            }
            details.documentationSection?.let {
                comma()
                property("documentationLink", documentationLinkFor(it))
            }
            stackTraceStringOf(details)?.let {
                comma()
                property("error", it)
            }
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
    fun documentationLinkFor(section: DocumentationSection) =
        documentationRegistry.getDocumentationFor("configuration_cache", section.anchor)

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
    fun write(csq: CharSequence) = writer.append(csq)

    private
    fun write(c: Char) = writer.append(c)
}
