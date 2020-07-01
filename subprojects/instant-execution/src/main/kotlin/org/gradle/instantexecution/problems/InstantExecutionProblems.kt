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

import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.instantexecution.InstantExecutionCacheAction
import org.gradle.instantexecution.InstantExecutionCacheAction.LOAD
import org.gradle.instantexecution.InstantExecutionCacheAction.STORE
import org.gradle.instantexecution.InstantExecutionProblemsException
import org.gradle.instantexecution.InstantExecutionReport
import org.gradle.instantexecution.TooManyInstantExecutionProblemsException
import org.gradle.instantexecution.extensions.getBroadcaster
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import java.util.concurrent.CopyOnWriteArrayList


private
const val maxCauses = 5


@ServiceScope(Scopes.BuildTree)
class InstantExecutionProblems(

    private
    val startParameter: InstantExecutionStartParameter,

    private
    val report: InstantExecutionReport,

    private
    val listenerManager: ListenerManager

) : ProblemsListener, AutoCloseable {

    private
    val broadcaster = listenerManager.getBroadcaster<ProblemsListener>()

    private
    val problemHandler = ProblemHandler()

    private
    val buildFinishedHandler = BuildFinishedProblemsHandler()

    private
    val problems = CopyOnWriteArrayList<PropertyProblem>()

    private
    var isFailOnProblems = startParameter.failOnProblems

    private
    var isFailingBuildDueToSerializationError = false

    private
    var cacheAction: InstantExecutionCacheAction? = null

    init {
        listenerManager.addListener(problemHandler)
        listenerManager.addListener(buildFinishedHandler)
    }

    override fun close() {
        listenerManager.removeListener(problemHandler)
        listenerManager.removeListener(buildFinishedHandler)
    }

    fun storing() {
        cacheAction = STORE
    }

    fun loading() {
        cacheAction = LOAD
    }

    override fun onProblem(problem: PropertyProblem) {
        broadcaster.onProblem(problem)
    }

    fun failingBuildDueToSerializationError() {
        isFailingBuildDueToSerializationError = true
        isFailOnProblems = false
    }

    private
    fun List<PropertyProblem>.causes() = mapNotNull { it.exception }.take(maxCauses)

    private
    inner class ProblemHandler : ProblemsListener {

        override fun onProblem(problem: PropertyProblem) {
            problems.add(problem)
        }
    }

    private
    inner class BuildFinishedProblemsHandler : BuildAdapter() {

        override fun buildFinished(result: BuildResult) {
            if (result.gradle?.parent != null || cacheAction == null || problems.isEmpty()) {
                return
            }
            val cacheActionText = requireNotNull(cacheAction).summaryText()
            report.writeReportFiles(cacheActionText, problems)
            when {
                isFailOnProblems -> {
                    // TODO - always include this as a build failure; currently it is disabled when a serialization problem happens
                    throw newProblemsException(cacheActionText)
                }
                problems.size > startParameter.maxProblems -> {
                    throw newTooManyProblemsException(cacheActionText)
                }
                else -> {
                    report.logConsoleSummary(cacheActionText, problems)
                }
            }
        }

        private
        fun InstantExecutionCacheAction.summaryText() =
            when (this) {
                LOAD -> "reusing"
                STORE -> "storing"
            }

        private
        fun newProblemsException(cacheActionText: String) =
            InstantExecutionProblemsException(
                problems.causes(),
                cacheActionText,
                problems,
                report.htmlReportFile
            )

        private
        fun newTooManyProblemsException(cacheActionText: String) =
            TooManyInstantExecutionProblemsException(
                problems.causes(),
                cacheActionText,
                problems,
                report.htmlReportFile
            )
    }

    internal
    val outcome: Outcome
        get() {
            if (cacheAction == null) return Outcome(CacheEntryOutcome.NONE, problems.size)
            return when {
                isFailingBuildDueToSerializationError -> Outcome(CacheEntryOutcome.DISCARDED, problems.size)
                cacheAction == LOAD -> Outcome(CacheEntryOutcome.REUSED, problems.size)
                else -> Outcome(CacheEntryOutcome.STORED, problems.size)
            }
        }

    internal
    class Outcome(val entryOutcome: CacheEntryOutcome, val problemCount: Int)

    internal
    enum class CacheEntryOutcome {
        NONE, DISCARDED, STORED, REUSED
    }
}
