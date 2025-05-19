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

package org.gradle.internal.cc.impl.problems

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Ordering
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.internal.logging.ConsoleRenderer
import java.io.File
import java.util.Comparator.comparing
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


private
const val MAX_CONSOLE_PROBLEMS = 15


private
const val MAX_CAUSES = 5

/**
 * This class is thread-safe.
 */
internal
class ConfigurationCacheProblemsSummary(

    private
    val maxCollectedProblems: Int = 4096

) {
    /**
     * Reported more problems than can be collected.
     */
    private
    var overflowed: Boolean = false

    private
    var totalProblemCount: Int = 0

    private
    var deferredProblemCount: Int = 0

    private
    var suppressedProblemCount: Int = 0

    private
    val uniqueProblems = HashMap<UniquePropertyProblem, ProblemSeverity>()

    private
    var causes = ArrayList<Throwable>(MAX_CAUSES)

    private
    val severityComparator = consoleComparatorForSeverity()

    private
    val lock = ReentrantLock()

    fun get(): Summary = lock.withLock {
        Summary(
            totalProblemCount,
            deferredProblemCount,
            ImmutableMap.copyOf(uniqueProblems),
            ImmutableList.copyOf(causes),
            overflowed,
            maxCollectedProblems
        )
    }

    /**
     * Returns`true` if the problem was accepted, `false` if it was rejected because the maximum number of problems was reached.
     */
    fun onProblem(problem: PropertyProblem, severity: ProblemSeverity): Boolean {
        lock.withLock {
            totalProblemCount += 1
            when (severity) {
                ProblemSeverity.Deferred -> deferredProblemCount += 1
                ProblemSeverity.Suppressed -> suppressedProblemCount += 1
            }
            if (overflowed) {
                return false
            }
            if (totalProblemCount > maxCollectedProblems) {
                overflowed = true
                return false
            }
            val isNew = trackUniqueProblems(problem, severity)
            if (isNew && causes.size < MAX_CAUSES) {
                problem.exception?.let {
                    causes.add(it)
                }
            }
            return true
        }
    }

    /**
     * Return true if no similar problem has been seen before.
     */
    private
    fun trackUniqueProblems(problem: PropertyProblem, severity: ProblemSeverity): Boolean {
        val reducedProblem = UniquePropertyProblem.of(problem)
        val isNew = !uniqueProblems.containsKey(reducedProblem)
        uniqueProblems.merge(reducedProblem, severity) { old, new ->
            if (severityComparator.compare(old, new) < 0) old else new
        }
        return isNew
    }
}


internal
class Summary(
    /**
     * Total of all problems, regardless of severity.
     */
    val totalProblemCount: Int,

    /**
     * Number of [deferred][ProblemSeverity.Deferred] failures.
     */
    val deferredProblemCount: Int,

    private
    val uniqueProblems: Map<UniquePropertyProblem, ProblemSeverity>,

    val causes: List<Throwable>,

    private
    val overflowed: Boolean,

    private
    val maxCollectedProblems: Int
) {
    val uniqueProblemCount: Int
        get() = uniqueProblems.size

    fun textForConsole(cacheActionText: String, htmlReportFile: File? = null): String {
        val documentationRegistry = DocumentationRegistry()
        return StringBuilder().apply {
            appendLine()
            appendSummaryHeader(cacheActionText, totalProblemCount)
            appendLine()
            topProblemsForConsole().forEach { problem ->
                append("- ")
                append(problem.userCodeLocation.capitalized())
                append(": ")
                appendLine(problem.message)
                problem.documentationSection?.let {
                    appendLine("  See ${documentationRegistry.getDocumentationFor("configuration_cache", it)}")
                }
            }
            if (uniqueProblemCount > MAX_CONSOLE_PROBLEMS) {
                appendLine("plus ${uniqueProblemCount - MAX_CONSOLE_PROBLEMS} more problems. Please see the report for details.")
            }
            htmlReportFile?.let {
                appendLine()
                append(buildSummaryReportLink(it))
            }
        }.toString()
    }

    private
    fun topProblemsForConsole(): Sequence<UniquePropertyProblem> =
        Ordering.from(consoleComparatorForProblemWithSeverity()).leastOf(uniqueProblems.entries, MAX_CONSOLE_PROBLEMS)
            .asSequence()
            .map { it.key }

    private
    fun StringBuilder.appendSummaryHeader(
        cacheAction: String,
        totalProblemCount: Int
    ) {
        append(totalProblemCount)
        append(if (totalProblemCount == 1) " problem was found " else " problems were found ")
        append(cacheAction)
        append(" the configuration cache")
        if (overflowed) {
            append(", only the first ")
            append(maxCollectedProblems)
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
fun consoleComparatorForProblemWithSeverity(): Comparator<Map.Entry<UniquePropertyProblem, ProblemSeverity>> =
    comparing<Map.Entry<UniquePropertyProblem, ProblemSeverity>, ProblemSeverity>({ it.value }, consoleComparatorForSeverity())
        .thenComparing({ it.key }, consoleComparatorForProblem())


private
fun consoleComparatorForProblem(): Comparator<UniquePropertyProblem> =
    comparing { p: UniquePropertyProblem -> p.userCodeLocation }
        .thenComparing { p: UniquePropertyProblem -> p.message }


/**
 * Sorts the severities in the order suitable for a console summary.
 *
 * Deferred problems go first because their presence is the cause of the Configuration Cache build failure.
 * Suppressed problems are included, but their presence alone would not have triggered a build failure.
 */
private
fun consoleComparatorForSeverity(): Comparator<ProblemSeverity> =
    Comparator.comparingInt { it: ProblemSeverity ->
        when (it) {
            ProblemSeverity.Deferred -> 1
            ProblemSeverity.Suppressed -> 2
        }
    }


internal
data class UniquePropertyProblem(
    val userCodeLocation: String,
    val message: String,
    val documentationSection: String?
) {
    companion object {
        fun of(problem: PropertyProblem) = problem.run {
            UniquePropertyProblem(
                trace.containingUserCode,
                message.render(),
                documentationSection?.anchor
            )
        }
    }
}
