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

import org.gradle.internal.logging.ConsoleRenderer
import java.io.File


private
typealias UniquePropertyProblem = Pair<String, StructuredMessage>


internal
fun buildConsoleSummary(problems: List<PropertyProblem>, reportFile: File): String {
    val uniquePropertyProblems = uniquePropertyProblems(problems)
    return StringBuilder().apply {
        appendln()
        appendln(buildSummaryHeader(problems.size, uniquePropertyProblems))
        uniquePropertyProblems.forEach { (property, message) ->
            append("- ")
            append(property)
            append(": ")
            appendln(message)
        }
        appendln()
        append(buildSummaryReportLink(reportFile))
    }.toString()
}


private
fun uniquePropertyProblems(problems: List<PropertyProblem>): Set<UniquePropertyProblem> =
    problems.sortedBy { it.trace.sequence.toList().reversed().joinToString(".") }
        .groupBy { propertyDescriptionFor(it.trace) to it.message }
        .keys


private
fun buildSummaryHeader(totalProblemCount: Int, uniquePropertyProblems: Set<UniquePropertyProblem>): String {
    val problemOrProblems = if (totalProblemCount == 1) "problem was" else "problems were"
    val uniqueProblemCount = uniquePropertyProblems.size
    val seemsOrSeem = if (uniqueProblemCount == 1) "seems" else "seem"
    return "$totalProblemCount instant execution $problemOrProblems found, $uniqueProblemCount of which $seemsOrSeem unique."
}


private
fun buildSummaryReportLink(reportFile: File) =
    "See the complete report at ${clickableUrlFor(reportFile)}"


private
fun clickableUrlFor(file: File) =
    ConsoleRenderer().asClickableFileUrl(file)


