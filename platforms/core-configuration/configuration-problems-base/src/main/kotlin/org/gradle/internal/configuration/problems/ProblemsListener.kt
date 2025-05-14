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

import org.gradle.internal.service.scopes.EventScope
import org.gradle.internal.service.scopes.Scope


@EventScope(Scope.BuildTree::class)
interface ProblemsListener {

    fun onProblem(problem: PropertyProblem)

    /**
     * Execution-time problems are treated as immediate errors and must have an [PropertyProblem.exception].
     *
     * These problems arise from access to execution-time state that is not available when running with CC,
     * so the code accessing it must not proceed.
     */
    fun onExecutionTimeProblem(problem: PropertyProblem)

    fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder)

    fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener
}


