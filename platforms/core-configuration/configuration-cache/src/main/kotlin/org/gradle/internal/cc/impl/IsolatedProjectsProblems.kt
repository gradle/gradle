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

package org.gradle.internal.cc.impl

import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import javax.inject.Inject

/**
 * Helper for reporting IP problems. Respects the current IP mode ([org.gradle.initialization.StartParameterBuildOptions.IsolatedProjectsOption.Mode]).
 */
@ServiceScope(Scope.BuildTree::class)
class IsolatedProjectsProblems
@Inject constructor(
    private val modelParameters: BuildModelParameters,
    private val problems: ProblemsListener,
) {
    /**
     * Report a problem, respecting the current IP mode.
     *
     * - In "disabled" mode, this should not be called as the service is not available. An error will be thrown if it is called.
     * - In "warn" mode, the problem is reported as a warning (non-interrupting).
     * - In "enabled" mode, the problem is reported as an error (interrupting).
     */
    fun onProblem(problem: PropertyProblem) {
        if (!modelParameters.isIsolatedProjectsProblemDetection) {
            throw AssertionError("IsolatedProjectsProblems service should not be used when isolated projects problem detection is disabled.")
        }
        problems.onProblem(problem, interrupting = modelParameters.isIsolatedProjects)
    }
}
