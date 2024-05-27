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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Ordering
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.gradle.api.Task
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.internal.logging.ConsoleRenderer
import java.io.File
import java.util.Comparator.comparing
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


private
const val maxConsoleProblems = 15


private
const val maxCauses = 5


internal
enum class ProblemSeverity {
    Info,
    Failure,

    /**
     * A problem produced by a task marked as [notCompatibleWithConfigurationCache][Task.notCompatibleWithConfigurationCache].
     */
    Suppressed
}


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
    var problemCount: Int = 0

    private
    var failureCount: Int = 0

    private
    var suppressedCount: Int = 0

    private
    var uniqueProblems = ObjectOpenHashSet<UniquePropertyProblem>()

    private
    var causes = ArrayList<Throwable>(maxCauses)

    private
    val lock = ReentrantLock()

    fun get(): Summary = lock.withLock {
        Summary(
            problemCount,
            failureCount,
            suppressedCount,
            ImmutableSet.copyOf(uniqueProblems),
            ImmutableList.copyOf(causes),
            overflowed,
            maxCollectedProblems
        )
    }

    fun onProblem(problem: PropertyProblem, severity: ProblemSeverity): Boolean {
        lock.withLock {
            problemCount += 1
            when (severity) {
                ProblemSeverity.Failure -> failureCount += 1
                ProblemSeverity.Suppressed -> suppressedCount += 1
                ProblemSeverity.Info -> {}
            }
            if (overflowed) {
                return false
            }
            if (problemCount > maxCollectedProblems) {
                overflowed = true
                return false
            }
            val uniqueProblem = UniquePropertyProblem.of(problem)
            if (uniqueProblems.add(uniqueProblem) && causes.size < maxCauses) {
                problem.exception?.let {
                    causes.add(it)
                }
            }
            return true
        }
    }
}


internal
class Summary(
    /**
     * Total of all problems, regardless of severity.
     */
    val problemCount: Int,

    /**
     * Total number of problems that are failures.
     */
    val failureCount: Int,

    /**
     * Total number of [suppressed][ProblemSeverity.Suppressed] problems.
     */
    private
    val suppressedCount: Int,

    private
    val uniqueProblems: Set<UniquePropertyProblem>,

    val causes: List<Throwable>,

    private
    val overflowed: Boolean,

    private
    val maxCollectedProblems: Int
) {
    val uniqueProblemCount: Int
        get() = uniqueProblems.size

    val nonSuppressedProblemCount: Int
        get() = problemCount - suppressedCount

    fun textForConsole(cacheActionText: String, htmlReportFile: File? = null): String {
        val documentationRegistry = DocumentationRegistry()
        return StringBuilder().apply {
            appendLine()
            appendSummaryHeader(cacheActionText, problemCount)
            appendLine()
            Ordering.from(consoleComparator()).leastOf(uniqueProblems, maxConsoleProblems).forEach { problem ->
                append("- ")
                append(problem.userCodeLocation.capitalized())
                append(": ")
                appendLine(problem.message)
                problem.documentationSection?.let<String, Unit> {
                    appendLine("  See ${documentationRegistry.getDocumentationFor("configuration_cache", it)}")
                }
            }
            if (uniqueProblemCount > maxConsoleProblems) {
                appendLine("plus ${uniqueProblemCount - maxConsoleProblems} more problems. Please see the report for details.")
            }
            htmlReportFile?.let {
                appendLine()
                append(buildSummaryReportLink(it))
            }
        }.toString()
    }

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
fun consoleComparator() =
    comparing { p: UniquePropertyProblem -> p.userCodeLocation }
        .thenComparing { p: UniquePropertyProblem -> p.message }


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
                message.toString(),
                documentationSection?.anchor
            )
        }
    }
}
