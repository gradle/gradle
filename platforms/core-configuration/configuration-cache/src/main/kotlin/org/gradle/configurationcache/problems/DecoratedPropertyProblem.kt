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
class ExceptionDecorator(
    private val stackTraceExtractor: (Throwable) -> String
) {

    fun decorateException(exception: Throwable): DecoratedException {
        val fullText = stackTraceExtractor(exception)
        val parts = fullText.lines().chunkedBy { line ->
            line.isStackFrameLine { it.isInternalStackFrame() }
        }

        return DecoratedException(
            exception,
            exceptionSummaryFor(exception),
            parts.map { (isInternal, lines) ->
                StackTracePart(isInternal, lines.joinToString("\n"))
            }
        )
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


/**
 * Splits the list into chunks where each chunk corresponds to the continuous
 * range of list items corresponding to the same [category][categorize].
 *
 * It differs from `groupBy`, because grouping does not take continuity of item ranges
 * with the same category into account.
 */
private
fun <T, C> List<T>.chunkedBy(categorize: (T) -> C): List<Pair<C, List<T>>> {
    return this.fold(mutableListOf<Pair<C, MutableList<T>>>()) { acc, item ->
        val category = categorize(item)
        if (acc.isNotEmpty() && acc.last().first == category) {
            acc.last().second.add(item)
        } else {
            acc.add(category to mutableListOf(item))
        }
        acc
    }
}
