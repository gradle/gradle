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

package org.gradle.instantexecution.problems

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.logging.ConsoleRenderer
import java.io.File


private
data class UniquePropertyProblem(val property: String, val message: StructuredMessage, val documentationSection: String?)


private
const val maxConsoleProblems = 15


internal
fun buildConsoleSummary(cacheAction: String, problems: List<PropertyProblem>, reportFile: File): String {
    val documentationRegistry = DocumentationRegistry()
    val uniquePropertyProblems = uniquePropertyProblems(problems)
    return StringBuilder().apply {
        appendln()
        appendln(buildSummaryHeader(cacheAction, problems.size, uniquePropertyProblems))
        uniquePropertyProblems.take(maxConsoleProblems).forEach { problem ->
            append("- ")
            append(problem.property)
            append(": ")
            appendln(problem.message)
            if (problem.documentationSection != null) {
                appendln("  See ${documentationRegistry.getDocumentationFor("configuration_cache", problem.documentationSection)}")
            }
        }
        if (uniquePropertyProblems.size > maxConsoleProblems) {
            appendln("plus ${uniquePropertyProblems.size - maxConsoleProblems} more problems. Please see the report for details.")
        }
        appendln()
        append(buildSummaryReportLink(reportFile))
    }.toString()
}


private
fun uniquePropertyProblems(problems: List<PropertyProblem>): Collection<UniquePropertyProblem> =
    problems
        .sortedBy(::uniquePropertyProblemSortingKey)
        .groupBy { problem ->
            UniquePropertyProblem(
                propertyDescriptionFor(problem.trace),
                problem.message,
                problem.documentationSection?.anchor
            )
        }.keys


private
fun uniquePropertyProblemSortingKey(problem: PropertyProblem) =
    StringBuilder().run {
        problem.trace.sequence.toList().asReversed().forEach { trace ->
            trace.run {
                appendStringOf(trace)
            }
        }
        toString()
    }


private
fun buildSummaryHeader(
    cacheAction: String,
    totalProblemCount: Int,
    uniquePropertyProblems: Collection<UniquePropertyProblem>
): String = StringBuilder().run {
    append(totalProblemCount)
    append(if (totalProblemCount == 1) " problem was found " else " problems were found ")
    append(cacheAction)
    append(" the configuration cache")
    val uniqueProblemCount = uniquePropertyProblems.size
    if (totalProblemCount != uniquePropertyProblems.size) {
        append(", ")
        append(uniqueProblemCount)
        append(" of which ")
        append(if (uniqueProblemCount == 1) "seems unique" else "seem unique")
    }
    append(".")
    toString()
}


private
fun buildSummaryReportLink(reportFile: File) =
    "See the complete report at ${clickableUrlFor(reportFile)}"


private
fun clickableUrlFor(file: File) =
    ConsoleRenderer().asClickableFileUrl(file)


