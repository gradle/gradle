/*
 * Copyright 2026 the original author or authors.
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

import com.google.errorprone.annotations.ThreadSafe
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Reports Isolated Projects violation problems.
 *
 * This must be used instead of [IsolatedProjectsProblemsListener] to ensure the mechanism of ignoring problems
 * is honored by all reported problems.
 *
 * The service is registered in all build modes; outside Isolated Projects the wired implementation is a
 * no-op so that callers can invoke [report] and [runIgnoringProblemsOnCurrentThread] unconditionally.
 */
@ThreadSafe
@ServiceScope(Scope.BuildTree::class)
interface IsolatedProjectsProblemsReporter {
    /**
     * Reports an Isolated Projects violation problem.
     *
     * The problem may be ignored entirely (not just suppressed), in case the current call runs
     * in the context of [runIgnoringProblemsOnCurrentThread].
     */
    fun report(builder: ProblemFactory.() -> PropertyProblem)

    /**
     * Runs the given action, ignoring any problems reported by [report].
     *
     * This is useful when a root-cause problem has just been reported and further problems
     * either stem from Gradle's internals and/or might be misleading to the user.
     */
    fun <T> runIgnoringProblemsOnCurrentThread(action: () -> T): T
}
