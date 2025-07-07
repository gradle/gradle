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


interface ProblemsListener {

    fun onProblem(problem: PropertyProblem)

    fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder)

    /**
     * Execution-time problems are treated as [interrupting][org.gradle.internal.cc.impl.problems.ProblemSeverity.Interrupting]
     * and must have an [PropertyProblem.exception].
     *
     * These problems arise from access to the configuration-time state that is generally not available after
     * loading the tasks from cache, so the code accessing it must not proceed, and the task should immediately fail.
     */
    fun onExecutionTimeProblem(problem: PropertyProblem)

    fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener

    fun forTask(task: Task): ProblemsListener
}


