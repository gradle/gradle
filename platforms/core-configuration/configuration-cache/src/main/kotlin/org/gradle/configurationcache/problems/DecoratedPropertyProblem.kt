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

package org.gradle.configurationcache.problems

import org.gradle.internal.failure.FailurePrinter
import org.gradle.internal.failure.FailurePrinterListener
import org.gradle.internal.failure.StackFramePredicate
import org.gradle.internal.problems.failure.StackTraceRelevance
import org.gradle.internal.problems.failure.Failure


internal
data class DecoratedPropertyProblem(
    val trace: PropertyTrace,
    val message: StructuredMessage,
    val exception: DecoratedException? = null,
    val documentationSection: DocumentationSection? = null
)


internal
data class DecoratedException(
    val original: Throwable,
    val summary: StructuredMessage?,
    val parts: List<StackTracePart>
)


internal
data class StackTracePart(
    val isInternal: Boolean,
    val text: String
)


internal
fun String.isStackFrameLine(): Boolean =
    isStackFrameLine { true }


internal
inline fun String.isStackFrameLine(locationPredicate: (String) -> Boolean): Boolean {
    val at = "at "
    return startsWith("\t")
        && trimStart('\t').let { it.startsWith(at) && locationPredicate(it.substring(at.length)) }
}


internal
class ExceptionDecorator {

    private
    val stringBuilder = StringBuilder()

    fun decorateException(failure: Failure): DecoratedException {
        return DecoratedException(
            failure.original,
            exceptionSummaryFor(failure.original),
            partitionedTraceFor(failure)
        )
    }

    private
    fun partitionedTraceFor(failure: Failure): List<StackTracePart> {
        val listener = PartitioningFailurePrinterListener(stringBuilder)
        try {
            FailurePrinter()
                .print(stringBuilder, failure, DisplayStackFramePredicate, listener)
            return listener.parts
        } finally {
            stringBuilder.setLength(0)
        }
    }

    private
    object DisplayStackFramePredicate : StackFramePredicate {
        override fun test(frame: StackTraceElement, relevance: StackTraceRelevance): Boolean {
            return relevance == StackTraceRelevance.USER_CODE
                || relevance == StackTraceRelevance.RUNTIME
        }
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
            val curIsInternal = relevance.isInternalForDisplay()
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

        private
        fun StackTraceRelevance.isInternalForDisplay() = when (this) {
            StackTraceRelevance.USER_CODE -> false
            else -> true
        }
    }

    private
    fun exceptionSummaryFor(exception: Throwable): StructuredMessage? {
        val stackTrace = exception.stackTrace
        val deepestNonInternalCall = stackTrace.firstOrNull {
            !it.className.isInternalStackFrame()
        } ?: return null

        return exceptionSummaryFrom(deepestNonInternalCall)
    }

    private
    fun exceptionSummaryFrom(elem: StackTraceElement) = StructuredMessage.build {
        text("at ")
        reference(buildString {
            append("${elem.className}.${elem.methodName}(")
            val fileName = elem.fileName
            if (fileName != null) {
                append("$fileName:${elem.lineNumber}")
            } else {
                append("Unknown Source")
            }
            append(")")
        })
    }
}


private
fun String.isInternalStackFrame(): Boolean =
    // JDK calls
    startsWith("java.") ||
        startsWith("jdk.internal.") ||
        startsWith("com.sun.proxy.") ||
        // Groovy calls
        startsWith("groovy.lang.") ||
        startsWith("org.codehaus.groovy.") ||
        // Gradle calls
        startsWith("org.gradle.")
