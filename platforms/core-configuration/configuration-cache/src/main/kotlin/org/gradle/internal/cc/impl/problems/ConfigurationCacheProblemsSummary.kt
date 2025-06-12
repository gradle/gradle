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

import com.google.common.collect.Comparators
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.configuration.problems.DocumentationSection
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
const val MAX_PROBLEM_EXCEPTIONS = 5

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

    /**
     * Unique problem causes observed among all reported problems.
     *
     * We also track severity per cause to provide useful ordering of problems when reporting the summary.
     */
    private
    val problemCauses = HashMap<ProblemCause, ProblemSeverity>()

    /**
     * As some problems come with original exceptions attached,
     * we collect a small number of them to include as part of the build failure
     */
    private
    val originalProblemExceptions = ArrayList<Throwable>(MAX_PROBLEM_EXCEPTIONS)

    private
    val severityComparator = consoleComparatorForSeverity()

    private
    val lock = ReentrantLock()

    fun get(): Summary = lock.withLock {
        Summary(
            totalProblemCount,
            deferredProblemCount,
            ImmutableMap.copyOf(problemCauses),
            ImmutableList.copyOf(originalProblemExceptions),
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
                ProblemSeverity.Interrupting -> {}
            }
            if (overflowed) {
                return false
            }
            if (totalProblemCount > maxCollectedProblems) {
                overflowed = true
                return false
            }
            val isNewCause = recordProblemCause(problem, severity)
            if (isNewCause) {
                collectOriginalException(problem)
            }
            return true
        }
    }

    private
    fun collectOriginalException(problem: PropertyProblem) {
        if (originalProblemExceptions.size < MAX_PROBLEM_EXCEPTIONS) {
            problem.exception?.let {
                originalProblemExceptions.add(it)
            }
        }
    }

    /**
     * Returns true if problems with the same cause have not been seen before.
     */
    private
    fun recordProblemCause(problem: PropertyProblem, severity: ProblemSeverity): Boolean {
        val cause = ProblemCause.of(problem)
        val isNew = !problemCauses.containsKey(cause)
        problemCauses.merge(cause, severity) { old, new ->
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
    val problemCauses: Map<ProblemCause, ProblemSeverity>,

    val originalProblemExceptions: List<Throwable>,

    private
    val overflowed: Boolean,

    private
    val maxCollectedProblems: Int
) {
    val problemCauseCount: Int
        get() = problemCauses.size

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
                    appendLine("  See ${documentationRegistry.getDocumentationFor(it.page, it.anchor)}")
                }
            }
            if (problemCauseCount > MAX_CONSOLE_PROBLEMS) {
                appendLine("plus ${problemCauseCount - MAX_CONSOLE_PROBLEMS} more problems. Please see the report for details.")
            }
            htmlReportFile?.let {
                appendLine()
                append(buildSummaryReportLink(it))
            }
        }.toString()
    }

    private
    fun topProblemsForConsole(): Sequence<ProblemCause> =
        problemCauses.entries.stream()
            .collect(Comparators.least(MAX_CONSOLE_PROBLEMS, consoleComparatorForProblemCauseWithSeverity()))
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
        if (totalProblemCount != problemCauseCount) {
            append(", ")
            append(problemCauseCount)
            append(" of which ")
            append(if (problemCauseCount == 1) "seems unique" else "seem unique")
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
fun consoleComparatorForProblemCauseWithSeverity(): Comparator<Map.Entry<ProblemCause, ProblemSeverity>> =
    comparing<Map.Entry<ProblemCause, ProblemSeverity>, ProblemSeverity>({ it.value }, consoleComparatorForSeverity())
        .thenComparing({ it.key }, consoleComparatorForProblemCause())


private
fun consoleComparatorForProblemCause(): Comparator<ProblemCause> =
    comparing { p: ProblemCause -> p.userCodeLocation }
        .thenComparing { p: ProblemCause -> p.message }


/**
 * Sorts the severities in the order suitable for a console summary.
 *
 * Deferred problems go first because their presence is the cause of the Configuration Cache build failure.
 * Suppressed problems are included, but their presence alone would not have triggered a build failure.
 * Interrupting problems will have a dedicated build failure, so they have the lowest summary priority.
 */
private
fun consoleComparatorForSeverity(): Comparator<ProblemSeverity> =
    Comparator.comparingInt { it: ProblemSeverity ->
        when (it) {
            ProblemSeverity.Deferred -> 1
            ProblemSeverity.Suppressed -> 2
            ProblemSeverity.Interrupting -> 3
        }
    }


/**
 * A subset of [PropertyProblem] information used for summarization of all observed problems.
 *
 * For instance, we omit the stacktrace.
 */
internal
data class ProblemCause(
    val userCodeLocation: String,
    val message: String,
    val documentationSection: DocumentationSection?
) {
    companion object {
        fun of(problem: PropertyProblem) = problem.run {
            ProblemCause(
                trace.containingUserCode,
                message.render(),
                documentationSection
            )
        }
    }
}
