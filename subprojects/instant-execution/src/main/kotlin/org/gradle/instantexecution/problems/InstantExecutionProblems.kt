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
import org.gradle.instantexecution.InstantExecutionProblemsException
import org.gradle.instantexecution.InstantExecutionReport
import org.gradle.instantexecution.TooManyInstantExecutionProblemsException
import org.gradle.instantexecution.extensions.getBroadcaster
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


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
    val problems = mutableListOf<PropertyProblem>()

    init {
        listenerManager.addListener(problemHandler)
        listenerManager.addListener(buildFinishedHandler)
    }

    override fun close() {
        listenerManager.removeListener(problemHandler)
        listenerManager.removeListener(buildFinishedHandler)
    }

    override fun onProblem(problem: PropertyProblem) {
        broadcaster.onProblem(problem)
    }

    private
    var isConsoleSummaryRequested = true

    private
    var isFailOnProblems = startParameter.failOnProblems

    fun failingBuildDueToSerializationError() {
        isFailOnProblems = false
    }

    private
    inner class ProblemHandler : ProblemsListener {

        override fun onProblem(problem: PropertyProblem) {
            problems.add(problem)
        }
    }

    private
    inner class BuildFinishedProblemsHandler : BuildAdapter() {

        override fun buildFinished(result: BuildResult) {
            if (result.gradle?.parent != null || problems.isEmpty()) {
                return
            }
            report.writeReportFiles(problems)
            if (isFailOnProblems) {
                // TODO - always include this as a build failure, currently it is disabled when a serialization problem happens
                throw InstantExecutionProblemsException(problems, report.htmlReportFile)
            } else if (problems.size > startParameter.maxProblems) {
                throw TooManyInstantExecutionProblemsException(problems, report.htmlReportFile)
            } else {
                report.logConsoleSummary(problems)
            }
        }
    }
}
