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

import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Reports Isolated Projects violation problems.
 *
 * This must be used instead of [IsolatedProjectsProblemsListener] to ensure the mechanism of ignoring problems
 * is honered by all reported problems.
 */
@ServiceScope(Scope.BuildTree::class)
class IsolatedProjectsProblemsReporter(
    private val problemFactory: ProblemFactory,
    private val problemsListener: IsolatedProjectsProblemsListener
) {

    private val ignoreDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    /**
     * Reports an Isolated Projects violation problem.
     *
     * The problem may be ignored entirely (not just suppressed), in case the current call runs
     * in the context of [runIgnoringProblemsOnCurrentThread].
     */
    fun report(builder: ProblemFactory.() -> PropertyProblem) {
        if (ignoreDepth.get() > 0) {
            return
        }
        problemsListener.onIsolatedProjectsProblem(problemFactory.builder())
    }

    /**
     * Runs the given action, ignoring any problems reported by [report].
     *
     * This is useful when a root-cause problem has just been reported and further problems
     * either stem from Gradle's internals and/or might be misleading to the user.
     */
    fun <T> runIgnoringProblemsOnCurrentThread(action: () -> T): T {
        val depth = ignoreDepth.get()
        ignoreDepth.set(depth + 1)
        try {
            return action()
        } finally {
            ignoreDepth.set(depth)
        }
    }
}
