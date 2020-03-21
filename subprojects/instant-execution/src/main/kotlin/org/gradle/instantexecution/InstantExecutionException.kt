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
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.DefaultMultiCauseException


// TODO lazy message?
@Contextual
sealed class InstantExecutionException(
    message: String,
    causes: Iterable<Throwable>
) : DefaultMultiCauseException(message, causes)


class InstantExecutionErrorsException(
    summary: String,
    problems: List<PropertyProblem>
) : InstantExecutionException(
    "${MESSAGE}\n$summary",
    problems.map(PropertyProblem::exception)
) {
    companion object {
        const val MESSAGE = "Instant execution state could not be cached."
    }
}


@Contextual
class InstantExecutionErrorException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    override fun fillInStackTrace(): Throwable =
        if (cause == null) super.fillInStackTrace()
        else this
}


class TooManyInstantExecutionProblemsException(
    summary: String,
    problems: List<PropertyProblem>
) : InstantExecutionException(
    "${MESSAGE}\n$summary",
    problems.map(PropertyProblem::exception)
) {
    companion object {
        const val MESSAGE = "Maximum number of instant execution problems has been reached.\nThis behavior can be adjusted via -D${SystemProperties.maxProblems}=<integer>."
    }
}


class InstantExecutionProblemsException(
    summary: String,
    problems: List<PropertyProblem>
) : InstantExecutionException(
    "$MESSAGE\n$summary",
    problems.map(PropertyProblem::exception)
) {
    companion object {
        const val MESSAGE = "Problems found while caching instant execution state.\nFailing because -D${SystemProperties.failOnProblems} is 'true'."
    }
}


@Contextual
class InstantExecutionProblemException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    override fun fillInStackTrace(): Throwable =
        if (cause == null) super.fillInStackTrace()
        else this
}
