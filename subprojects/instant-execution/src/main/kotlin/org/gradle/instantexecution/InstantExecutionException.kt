/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.instantexecution.problems.PropertyProblem
import org.gradle.instantexecution.problems.buildExceptionSummary

import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.DefaultMultiCauseExceptionNoStackTrace

import java.io.File


@Contextual
sealed class InstantExecutionException(
    message: String,
    problems: List<PropertyProblem>,
    htmlReportFile: File
) : DefaultMultiCauseExceptionNoStackTrace(
    { "$message\n${buildExceptionSummary(problems, htmlReportFile)}" },
    problems.map(PropertyProblem::exception)
)

@Contextual
class InstantExecutionErrorException(
    error: String,
    cause: Throwable? = null
) : Exception(error, cause) {
    override fun fillInStackTrace() = this
}


class TooManyInstantExecutionProblemsException(
    problems: List<PropertyProblem>,
    htmlReportFile: File
) : InstantExecutionException(MESSAGE, problems, htmlReportFile) {
    companion object {
        const val MESSAGE =
            "Maximum number of instant execution problems has been reached.\n" +
                "This behavior can be adjusted via -D${SystemProperties.maxProblems}=<integer>."
    }
}


class InstantExecutionProblemsException(
    problems: List<PropertyProblem>,
    htmlReportFile: File
) : InstantExecutionException(MESSAGE, problems, htmlReportFile) {
    companion object {
        const val MESSAGE =
            "Problems found while caching instant execution state.\n" +
                "Failing because -D${SystemProperties.failOnProblems} is 'true'."
    }
}


@Contextual
class InstantExecutionProblemException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    override fun fillInStackTrace() = this
}
