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

import org.gradle.internal.problems.failure.FailurePrinter
import org.gradle.internal.problems.failure.FailurePrinterListener
import org.gradle.internal.problems.failure.StackFramePredicate
import org.gradle.internal.problems.failure.StackTraceRelevance
import org.gradle.internal.problems.failure.Failure


internal
data class DecoratedPropertyProblem(
    val trace: PropertyTrace,
    val message: StructuredMessage,
    val error: DecoratedError? = null,
    val documentationSection: DocumentationSection? = null
)


internal
data class DecoratedError(
    val summary: StructuredMessage?,
    val parts: List<StackTracePart>
)


internal
data class StackTracePart(
    val isInternal: Boolean,
    val text: String
)


internal
class ErrorDecorator {

    private
    val stringBuilder = StringBuilder()

    fun decorate(failure: Failure): DecoratedError {
        return DecoratedError(
            exceptionSummaryFor(failure),
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
    fun exceptionSummaryFor(failure: Failure): StructuredMessage? {
        failure.stackTrace.forEachIndexed { index, element ->
            if (failure.getStackTraceRelevance(index) == StackTraceRelevance.USER_CODE) {
                return exceptionSummaryFrom(element)
            }
        }

        return null
    }

    private
    fun exceptionSummaryFrom(elem: StackTraceElement) = StructuredMessage.build {
        text("at ")
        reference(elem.toString())
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
    object DisplayStackFramePredicate : StackFramePredicate {
        override fun test(frame: StackTraceElement, relevance: StackTraceRelevance): Boolean {
            return relevance == StackTraceRelevance.USER_CODE
                || relevance == StackTraceRelevance.RUNTIME
        }
    }
}
