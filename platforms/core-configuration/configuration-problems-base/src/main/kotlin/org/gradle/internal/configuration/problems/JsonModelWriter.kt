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

package org.gradle.internal.configuration.problems

import java.io.Writer


enum class DiagnosticKind {
    PROBLEM,
    INPUT,
    INCOMPATIBLE_TASK
}


class JsonModelWriter(writer: Writer) : JsonModelWriterCommon(writer) {

    private
    var first = true

    fun beginModel() {
        beginObject()

        propertyName("diagnostics")
        beginArray()
    }

    fun endModel(details: ProblemReportDetails) = with(details) {
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
        property("cacheActionDescription") {
            writeStructuredMessage(cacheActionDescription)
        }
        comma()
        property("documentationLink", documentationRegistry.getDocumentationFor("configuration_cache"))

        endObject()
    }

    fun writeDiagnostic(kind: DiagnosticKind, details: DecoratedReportProblem) {
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
        DiagnosticKind.INCOMPATIBLE_TASK -> "incompatibleTask"
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
}
