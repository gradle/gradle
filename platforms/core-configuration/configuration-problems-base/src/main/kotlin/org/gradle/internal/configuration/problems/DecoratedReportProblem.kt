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

import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailurePrinter
import org.gradle.internal.problems.failure.FailurePrinterListener
import org.gradle.internal.problems.failure.StackTraceRelevance


data class DecoratedReportProblem(
    val trace: PropertyTrace,
    val message: StructuredMessage,
    val failure: DecoratedFailure? = null,
    val documentationSection: DocumentationSection? = null,
    val kind: String
) : JsonSource {

    private
    fun JsonModelWriterCommon.writeError(failure: DecoratedFailure) {
        val summary = failure.summary
        val parts = failure.parts
        property("error") {
            jsonObject {
                summary?.let {
                    property("summary") {
                        writeStructuredMessage(summary)
                    }
                }

                parts?.let {

                    summary?.let { comma() }
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
    fun JsonModelWriterCommon.writePropertyTrace(trace: PropertyTrace) {
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
    fun keyFor(kind: DiagnosticKind) = when (kind) {
        DiagnosticKind.PROBLEM -> "problem"
        DiagnosticKind.INPUT -> "input"
        DiagnosticKind.INCOMPATIBLE_TASK -> "incompatibleTask"
    }

    override fun writeToJson(jsonWriter: JsonModelWriterCommon) {
        with(jsonWriter) {
            jsonObject {
                property("trace") {
                    jsonObjectList(trace.sequence.asIterable()) { trace ->
                        writePropertyTrace(trace)
                    }
                }
                comma()
                property(kind) {
                    writeStructuredMessage(message)
                }
                documentationSection?.let {
                    comma()
                    property("documentationLink", documentationLinkFor(it))
                }
                failure?.let { failure ->
                    comma()
                    writeError(failure)
                }
            }
        }

    }

}


data class DecoratedFailure(
    val summary: StructuredMessage?,
    val parts: List<StackTracePart>?
) {
    companion object {
        val MARKER = DecoratedFailure(null, null)
    }
}


data class StackTracePart(
    val isInternal: Boolean,
    val text: String
)


class FailureDecorator {

    private
    val stringBuilder = StringBuilder()

    fun decorate(failure: Failure): DecoratedFailure {
        return DecoratedFailure(
            exceptionSummaryFor(failure),
            partitionedTraceFor(failure)
        )
    }

    private
    fun partitionedTraceFor(failure: Failure): List<StackTracePart> {
        val listener = PartitioningFailurePrinterListener(stringBuilder)
        try {
            FailurePrinter.print(stringBuilder, failure, listener)
            return listener.parts
        } finally {
            stringBuilder.setLength(0)
        }
    }

    private
    fun exceptionSummaryFor(failure: Failure): StructuredMessage? {
        return failure.findFirstUserCode()?.let(::exceptionSummaryFrom)
    }

    private
    fun Failure.findFirstUserCode(): StackTraceElement? {
        stackTrace.forEachIndexed { index, element ->
            if (getStackTraceRelevance(index).isUserCode()) {
                return element
            }
        }

        return null
    }

    private
    fun exceptionSummaryFrom(frame: StackTraceElement) = StructuredMessage.build {
        text("at ")
        reference(frame.toString())
    }

    private
    class PartitioningFailurePrinterListener(
        private val buffer: StringBuilder
    ) : FailurePrinterListener {

        private
        var lastIsInternal: Boolean? = null

        val parts = mutableListOf<StackTracePart>()

        override fun beforeFrames() {
            cutPart(false)
        }

        override fun beforeFrame(element: StackTraceElement, relevance: StackTraceRelevance) {
            val lastIsInternal = lastIsInternal
            val curIsInternal = !relevance.isUserCode()
            if (lastIsInternal != null && lastIsInternal != curIsInternal) {
                cutPart(lastIsInternal)
            }
            this.lastIsInternal = curIsInternal
        }

        override fun afterFrames() {
            val lastIsInternal = lastIsInternal ?: return
            cutPart(lastIsInternal)
            this.lastIsInternal = null
        }

        private
        fun cutPart(isInternal: Boolean) {
            val text = drainBuffer()
            parts += StackTracePart(isInternal, text)
        }

        private
        fun drainBuffer(): String = buffer.toString().also { buffer.setLength(0) }
    }
}


private
fun StackTraceRelevance.isUserCode() = this == StackTraceRelevance.USER_CODE

fun JsonModelWriterCommon.writeStructuredMessage(message: StructuredMessage) {
    jsonObjectList(message.fragments) { fragment ->
        writeFragment(fragment)
    }
}

fun JsonModelWriterCommon.writeFragment(fragment: StructuredMessage.Fragment) {
    when (fragment) {
        is StructuredMessage.Fragment.Reference -> property("name", fragment.name)
        is StructuredMessage.Fragment.Text -> property("text", fragment.text)
    }
}
