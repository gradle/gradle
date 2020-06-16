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

import org.gradle.api.internal.DocumentationRegistry

import org.gradle.instantexecution.problems.PropertyProblem
import org.gradle.instantexecution.problems.buildConsoleSummary

import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.DefaultMultiCauseException

import java.io.File


/**
 * Marker interface for exception handling.
 */
internal
interface InstantExecutionThrowable


/**
 * State might be corrupted and should be discarded.
 */
@Contextual
class InstantExecutionError internal constructor(
    error: String,
    cause: Throwable? = null
) : Exception(
    "Configuration cache state could not be cached: $error",
    cause
), InstantExecutionThrowable


@Contextual
sealed class InstantExecutionException private constructor(
    message: () -> String,
    causes: Iterable<Throwable>
) : DefaultMultiCauseException(message, causes), InstantExecutionThrowable


open class InstantExecutionProblemsException : InstantExecutionException {

    protected
    object Documentation {

        val ignoreProblems: String
            get() = DocumentationRegistry().getDocumentationFor("configuration_cache", "ignore_problems")

        val maxProblems: String
            get() = DocumentationRegistry().getDocumentationFor("configuration_cache", "max_problems")
    }

    protected
    constructor(
        message: String,
        causes: List<Throwable>,
        cacheAction: String,
        problems: List<PropertyProblem>,
        htmlReportFile: File
    ) : super(
        { "$message\n${buildConsoleSummary(cacheAction, problems, htmlReportFile)}" },
        causes
    )

    internal
    constructor(
        causes: List<Throwable>,
        cacheAction: String,
        problems: List<PropertyProblem>,
        htmlReportFile: File
    ) : this(
        "Configuration cache problems found in this build.\n" +
            "Gradle can be made to ignore these problems, see ${Documentation.ignoreProblems}.",
        causes,
        cacheAction,
        problems,
        htmlReportFile
    )
}


class TooManyInstantExecutionProblemsException internal constructor(
    causes: List<Throwable>,
    cacheAction: String,
    problems: List<PropertyProblem>,
    htmlReportFile: File
) : InstantExecutionProblemsException(
    "Maximum number of configuration cache problems has been reached.\n" +
        "This behavior can be adjusted, see ${Documentation.maxProblems}.",
    causes,
    cacheAction,
    problems,
    htmlReportFile
)
