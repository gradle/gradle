/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.base.problems

import org.gradle.api.Task
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * A stub implementation of ProblemsListener.
 */
@ServiceScope(Scope.BuildTree::class)
object IgnoringProblemsListener : ProblemsListener {
    override fun onProblem(problem: PropertyProblem) = Unit

    override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) = Unit

    override fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener = this

    override fun forTask(task: Task): ProblemsListener = this

    override fun onExecutionTimeProblem(problem: PropertyProblem) = Unit
}
