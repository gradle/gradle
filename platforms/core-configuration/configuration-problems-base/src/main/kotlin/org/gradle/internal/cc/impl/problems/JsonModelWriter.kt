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

package org.gradle.internal.cc.impl.problems

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.configuration.problems.DecoratedFailure
import org.gradle.internal.configuration.problems.DecoratedReportProblem
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configuration.problems.documentationLinkFor
import org.gradle.internal.configuration.problems.firstTypeFrom
import org.gradle.internal.configuration.problems.projectPathFrom
import org.gradle.internal.configuration.problems.taskPathFrom


enum class DiagnosticKind {
    PROBLEM, INPUT, INCOMPATIBLE_TASK
}

class JsonModelWriter(private val modelWriter: JsonWriter) {

    private val documentationRegistry = DocumentationRegistry()

    fun beginModel() {
        with(modelWriter) {
            beginObject()

            propertyName("diagnostics")
            beginArray()
        }
    }

    fun endModel(details: ProblemReportDetails) = with(details) {
        with(modelWriter) {
            endArray()
            property("totalProblemCount") {
                write(totalProblemCount.toString())
            }
            buildDisplayName?.let {
                property("buildName", it)
            }
            requestedTasks?.let {
                property("requestedTasks", it)
            }
            property("cacheAction", cacheAction)
            property("cacheActionDescription") {
                writeStructuredMessage(cacheActionDescription)
            }
            property("documentationLink", documentationRegistry.getDocumentationFor("configuration_cache"))

            endObject()
        }
    }

    fun writeDiagnostic(kind: DiagnosticKind, details: DecoratedReportProblem) {
        with(modelWriter) {
            jsonObject {
                property("trace") {
                    jsonObjectList(details.trace.sequence.asIterable()) { trace ->
                        writePropertyTrace(trace)
                    }
                }
                property(keyFor(kind)) {
                    writeStructuredMessage(details.message)
                }
                details.documentationSection?.let {
                    property("documentationLink", documentationLinkFor(it))
                }
                details.failure?.let { failure ->
                    writeError(failure)
                }
            }
        }
    }

    private fun writeError(failure: DecoratedFailure) {
        with(modelWriter) {
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
                        property("parts") {
                            jsonObjectList(parts) { (isInternal, text) ->
                                property(if (isInternal) "internalText" else "text", text)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun writeStructuredMessage(message: StructuredMessage) {
        with(modelWriter) {
            jsonObjectList(message.fragments) { fragment ->
                writeFragment(fragment)
            }
        }
    }

    private fun keyFor(kind: DiagnosticKind) = when (kind) {
        DiagnosticKind.PROBLEM -> "problem"
        DiagnosticKind.INPUT -> "input"
        DiagnosticKind.INCOMPATIBLE_TASK -> "incompatibleTask"
    }

    private fun writeFragment(fragment: StructuredMessage.Fragment) {
        with(modelWriter) {
            when (fragment) {
                is StructuredMessage.Fragment.Reference -> property("name", fragment.name)
                is StructuredMessage.Fragment.Text -> property("text", fragment.text)
            }
        }
    }

    private fun writePropertyTrace(trace: PropertyTrace) {
        with(modelWriter) {
            when (trace) {
                is PropertyTrace.Property -> {
                    when (trace.kind) {
                        PropertyKind.Field -> {
                            property("kind", trace.kind.name)
                            property("name", trace.name)
                            property("declaringType", firstTypeFrom(trace.trace).name)
                        }

                        PropertyKind.PropertyUsage -> {
                            property("kind", trace.kind.name)
                            property("name", trace.name)
                            property("from", projectPathFrom(trace.trace))
                        }

                        else -> {
                            property("kind", trace.kind.name)
                            property("name", trace.name)
                            property("task", taskPathFrom(trace.trace))
                        }
                    }
                }

                is PropertyTrace.SystemProperty -> {
                    property("kind", "SystemProperty")
                    property("name", trace.name)
                }

                is PropertyTrace.Task -> {
                    property("kind", "Task")
                    property("path", trace.path)
                    property("type", trace.type.name)
                }

                is PropertyTrace.Bean -> {
                    property("kind", "Bean")
                    property("type", trace.type.name)
                }

                is PropertyTrace.Project -> {
                    property("kind", "Project")
                    property("path", trace.path)
                }

                is PropertyTrace.BuildLogic -> {
                    property("kind", "BuildLogic")
                    property("location", trace.source.displayName)
                }

                is PropertyTrace.BuildLogicClass -> {
                    property("kind", "BuildLogicClass")
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
}
