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

package org.gradle.configurationcache.problems

import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.logging.ConsoleRenderer
import java.io.File
import java.util.concurrent.atomic.AtomicInteger


private
const val maxConsoleProblems = 15


private
const val maxCauses = 5


internal
class ConfigurationCacheProblemsSummary {

    private
    val uniqueProblems = newConcurrentHashSet<UniquePropertyProblem>()

    private
    val problemCountSummary = AtomicInteger()

    private
    val causesSummary = ArrayDeque<Throwable>(maxCauses + 1)

    val problemCount: Int
        get() = problemCountSummary.get()

    val causes: List<Throwable>
        get() = synchronized(causesSummary) {
            causesSummary.toList()
        }

    fun onProblem(problem: PropertyProblem) {
        val uniquePropertyProblem = problem.toUniquePropertyProblem()
        @Suppress("ControlFlowWithEmptyBody")
        if (uniqueProblems.add(uniquePropertyProblem)) {
            // TODO: summarize console problems here instead of keeping all problems until the end
        }
        problemCountSummary.incrementAndGet()
        problem.exception?.let { cause ->
            synchronized(causesSummary) {
                causesSummary.addLast(cause)
                if (causesSummary.size > maxCauses) {
                    causesSummary.removeFirst()
                }
            }
        }
    }

    fun textForConsole(cacheActionText: String, htmlReportFile: File): String =
        buildConsoleSummary(
            cacheActionText,
            htmlReportFile,
            problemCount,
            uniqueProblems.size,
            uniqueProblems.sortedWith(consoleComparator()).take(maxConsoleProblems)
        )
}


private
fun consoleComparator() = Comparator<UniquePropertyProblem> { x, y ->
    x.userCodeLocation.compareTo(y.userCodeLocation)
}.thenComparing { x, y ->
    x.message.toString().compareTo(y.message.toString())
}


private
data class UniquePropertyProblem(
    val userCodeLocation: String,
    val message: StructuredMessage,
    val documentationSection: String?
)


private
fun buildConsoleSummary(
    cacheAction: String,
    reportFile: File,
    totalProblemCount: Int,
    uniqueProblemCount: Int,
    problems: List<UniquePropertyProblem>
): String {
    val documentationRegistry = DocumentationRegistry()
    return StringBuilder().apply {
        appendLine()
        appendSummaryHeader(cacheAction, totalProblemCount, uniqueProblemCount)
        appendLine()
        problems.forEach { problem ->
            append("- ")
            append(problem.userCodeLocation.capitalize())
            append(": ")
            appendLine(problem.message)
            problem.documentationSection?.let {
                appendLine("  See ${documentationRegistry.getDocumentationFor("configuration_cache", it)}")
            }
        }
        if (uniqueProblemCount > maxConsoleProblems) {
            appendLine("plus ${uniqueProblemCount - maxConsoleProblems} more problems. Please see the report for details.")
        }
        appendLine()
        append(buildSummaryReportLink(reportFile))
    }.toString()
}


private
fun PropertyProblem.toUniquePropertyProblem() = UniquePropertyProblem(
    trace.containingUserCode,
    message,
    documentationSection?.anchor
)


private
fun StringBuilder.appendSummaryHeader(
    cacheAction: String,
    totalProblemCount: Int,
    uniqueProblemCount: Int
) {
    append(totalProblemCount)
    append(if (totalProblemCount == 1) " problem was found " else " problems were found ")
    append(cacheAction)
    append(" the configuration cache")
    if (totalProblemCount != uniqueProblemCount) {
        append(", ")
        append(uniqueProblemCount)
        append(" of which ")
        append(if (uniqueProblemCount == 1) "seems unique" else "seem unique")
    }
    append(".")
}


private
fun buildSummaryReportLink(reportFile: File) =
    "See the complete report at ${clickableUrlFor(reportFile)}"


private
fun clickableUrlFor(file: File) =
    ConsoleRenderer().asClickableFileUrl(file)
