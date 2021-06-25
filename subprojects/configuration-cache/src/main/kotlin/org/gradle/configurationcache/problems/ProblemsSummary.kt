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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.logging.ConsoleRenderer
import java.io.File


private
data class UniquePropertyProblem(
    val userCodeLocation: String,
    val message: StructuredMessage,
    val documentationSection: String?
)


private
const val maxConsoleProblems = 15


internal
fun buildConsoleSummary(cacheAction: String, problems: List<PropertyProblem>, reportFile: File): String =
    buildConsoleSummary(cacheAction, problems.size, uniquePropertyProblems(problems), reportFile)


private
fun buildConsoleSummary(
    cacheAction: String,
    totalProblemCount: Int,
    uniqueProblems: Set<UniquePropertyProblem>,
    reportFile: File
): String {

    val documentationRegistry = DocumentationRegistry()
    return StringBuilder().apply {
        appendLine()
        appendSummaryHeader(cacheAction, totalProblemCount, uniqueProblems)
        appendLine()
        uniqueProblems.take(maxConsoleProblems).forEach { problem ->
            append("- ")
            append(problem.userCodeLocation.capitalize())
            append(": ")
            appendLine(problem.message)
            problem.documentationSection?.let {
                appendLine("  See ${documentationRegistry.getDocumentationFor("configuration_cache", it)}")
            }
        }
        if (uniqueProblems.size > maxConsoleProblems) {
            appendLine("plus ${uniqueProblems.size - maxConsoleProblems} more problems. Please see the report for details.")
        }
        appendLine()
        append(buildSummaryReportLink(reportFile))
    }.toString()
}


private
fun uniquePropertyProblems(problems: List<PropertyProblem>): Set<UniquePropertyProblem> =
    problems
        .map { UniquePropertyProblem(it.trace.containingUserCode, it.message, it.documentationSection?.anchor) }
        .sortedBy { it.userCodeLocation }
        .toSet()


private
fun StringBuilder.appendSummaryHeader(
    cacheAction: String,
    totalProblemCount: Int,
    uniquePropertyProblems: Set<UniquePropertyProblem>
) {
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
}


private
fun buildSummaryReportLink(reportFile: File) =
    "See the complete report at ${clickableUrlFor(reportFile)}"


private
fun clickableUrlFor(file: File) =
    ConsoleRenderer().asClickableFileUrl(file)
