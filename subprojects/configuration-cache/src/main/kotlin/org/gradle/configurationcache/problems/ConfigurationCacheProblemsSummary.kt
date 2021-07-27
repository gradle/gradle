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
import io.usethesource.capsule.Set
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.logging.ConsoleRenderer
import java.io.File
import java.util.Comparator.comparing
import java.util.concurrent.atomic.AtomicReference


private
const val maxReportedProblems = 4096


private
const val maxConsoleProblems = 15


private
const val maxCauses = 5


internal
class ConfigurationCacheProblemsSummary {

    private
    val summary = AtomicReference(Summary.empty)

    fun get(): Summary =
        summary.get()

    fun onProblem(problem: PropertyProblem): Boolean =
        summary
            .updateAndGet { it.insert(problem) }
            .overflowed.not()
}


internal
class Summary private constructor(
    val problemCount: Int,

    private
    val uniqueProblems: Set.Immutable<UniquePropertyProblem>,

    val causes: List<Throwable>,

    val overflowed: Boolean
) {
    companion object {
        val empty = Summary(
            0,
            Set.Immutable.of(),
            emptyList(),
            false
        )
    }

    fun insert(problem: PropertyProblem): Summary {
        val newProblemCount = problemCount + 1
        if (overflowed || newProblemCount > maxReportedProblems) {
            return Summary(
                newProblemCount,
                uniqueProblems,
                causes,
                true
            )
        }
        val uniqueProblem = UniquePropertyProblem.of(problem)
        val newUniqueProblems = uniqueProblems.__insert(uniqueProblem)
        val newCauses = problem
            .takeIf { newUniqueProblems !== uniqueProblems && causes.size < maxCauses }
            ?.exception?.let { causes + it }
            ?: causes
        return Summary(
            newProblemCount,
            newUniqueProblems,
            newCauses,
            false
        )
    }

    fun textForConsole(cacheActionText: String, htmlReportFile: File): String {
        val documentationRegistry = DocumentationRegistry()
        val uniqueProblemCount = uniqueProblems.size
        return StringBuilder().apply {
            appendLine()
            appendSummaryHeader(cacheActionText, problemCount, uniqueProblemCount)
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
        if (overflowed) {
            append(", only the first ")
            append(maxReportedProblems)
            append(" were considered")
        }
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
