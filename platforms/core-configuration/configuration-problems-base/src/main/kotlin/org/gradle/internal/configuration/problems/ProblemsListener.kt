/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.configuration.problems

import org.gradle.api.Task
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


/**
 * Collects configuration cache problems discovered during serialization, deserialization, and execution.
 *
 * Problems are reported as they are discovered, and the listener implementation decides how to handle them
 * (e.g. record in a report, fail the build, or suppress for incompatible tasks).
 *
 * The [forIncompatibleTask] and [forTask] methods return a scoped listener that adjusts problem severity
 * for a particular task context (e.g. suppressing errors for tasks opted out of the configuration cache).
 */
@ServiceScope(Scope.BuildTree::class)
interface ProblemsListener {

    /**
     * Reports a configuration cache problem discovered during serialization or deserialization.
     */
    fun onProblem(problem: PropertyProblem)

    /**
     * Reports an unexpected error encountered during serialization or deserialization.
     *
     * Implementations typically wrap the error into a [PropertyProblem] and decide whether to
     * re-throw or record it depending on the error type.
     */
    fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder)

    /**
     * Execution-time problems are treated as [interrupting][org.gradle.internal.cc.impl.problems.ProblemSeverity.Interrupting]
     * and must have an [PropertyProblem.exception].
     *
     * These problems arise from access to the configuration-time state that is generally not available after
     * loading the tasks from cache, so the code accessing it must not proceed, and the task should immediately fail.
     */
    fun onExecutionTimeProblem(problem: PropertyProblem)

    /**
     * Returns a scoped listener for a task that has been marked as incompatible with the configuration cache.
     *
     * Problems reported through the returned listener are typically suppressed or downgraded in severity.
     */
    fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener

    /**
     * Returns a scoped listener for the given [task].
     *
     * If the task is subject to graceful degradation, the returned listener suppresses its problems.
     */
    fun forTask(task: Task): ProblemsListener
}


