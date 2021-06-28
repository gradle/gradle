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

import com.google.common.collect.Ordering
import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.logging.ConsoleRenderer
import java.io.File
import java.util.Comparator.comparing
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
    val causesSummary = ArrayList<Throwable>(maxCauses)

    val problemCount: Int
        get() = problemCountSummary.get()

    val causes: List<Throwable>
        get() = synchronized(causesSummary) {
            causesSummary.toList()
        }

    fun onProblem(problem: PropertyProblem) {

        problemCountSummary.getAndIncrement()

        val uniqueProblem = UniquePropertyProblem.of(problem)
        if (uniqueProblems.add(uniqueProblem)) {
            // TODO: summarize console problems here instead of keeping all problems until the end
            problem.exception?.let { cause ->
                synchronized(causesSummary) {
                    if (causesSummary.size < maxCauses) {
                        causesSummary.add(cause)
                    }
                }
            }
        }
    }

    fun textForConsole(cacheActionText: String, htmlReportFile: File): String {
        val documentationRegistry = DocumentationRegistry()
        val totalProblemCount = problemCount
        val uniqueProblemCount = uniqueProblems.size
        return StringBuilder().apply {
            appendLine()
            appendSummaryHeader(cacheActionText, totalProblemCount, uniqueProblemCount)
            appendLine()
            Ordering.from(consoleComparator()).leastOf(uniqueProblems, maxConsoleProblems).forEach { problem ->
                append("- ")
                append(problem.userCodeLocation.capitalize())
                append(": ")
                appendLine(problem.message)
                problem.documentationSection?.let<String, Unit> {
                    appendLine("  See ${documentationRegistry.getDocumentationFor("configuration_cache", it)}")
                }
            }
            if (uniqueProblemCount > maxConsoleProblems) {
                appendLine("plus ${uniqueProblemCount - maxConsoleProblems} more problems. Please see the report for details.")
            }
            appendLine()
            append(buildSummaryReportLink(htmlReportFile))
        }.toString()
    }

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
}


private
fun consoleComparator() =
    comparing { p: UniquePropertyProblem -> p.userCodeLocation }
        .thenComparing { p: UniquePropertyProblem -> p.message }


private
data class UniquePropertyProblem(
    val userCodeLocation: String,
    val message: String,
    val documentationSection: String?
) {
    companion object {
        fun of(problem: PropertyProblem) = problem.run {
            UniquePropertyProblem(
                trace.containingUserCode,
                message.toString(),
                documentationSection?.anchor
            )
        }
    }
}
