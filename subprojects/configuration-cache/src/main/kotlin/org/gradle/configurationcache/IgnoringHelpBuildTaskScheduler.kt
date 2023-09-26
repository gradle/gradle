/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.internal.GradleInternal
import org.gradle.configurationcache.problems.ProblemFactory
import org.gradle.configurationcache.problems.ProblemsListener
import org.gradle.execution.BuildTaskScheduler
import org.gradle.execution.EntryTaskSelector
import org.gradle.execution.plan.ExecutionPlan
import org.gradle.internal.buildtree.BuildModelParameters


class IgnoringHelpBuildTaskScheduler(
    private val buildModelParameters: BuildModelParameters,
    private val problemFactory: ProblemFactory,
    private val problemsListener: ProblemsListener,
    private val delegate: BuildTaskScheduler
) : BuildTaskScheduler {

    override fun scheduleRequestedTasks(gradle: GradleInternal, selector: EntryTaskSelector?, plan: ExecutionPlan, isModelBuildingRequested: Boolean) {
        val helpTaskOnly = gradle.startParameter.taskRequests.size == 1 &&
            gradle.startParameter.taskRequests.first().args.contains("help")

        if (buildModelParameters.isIsolatedProjects && isModelBuildingRequested) {
            if (helpTaskOnly) {
                return // sync scenario
            } else {
                val problem = problemFactory.problem {
                    text("Requesting tasks execution during model building is not supported with isolated projects. Requested tasks: ${gradle.startParameter.taskNames}.")
                }.build()

                problemsListener.onProblem(problem)
                delegate.scheduleRequestedTasks(gradle, selector, plan, true)
            }
        } else {
            delegate.scheduleRequestedTasks(gradle, selector, plan, isModelBuildingRequested)
        }
    }
}
