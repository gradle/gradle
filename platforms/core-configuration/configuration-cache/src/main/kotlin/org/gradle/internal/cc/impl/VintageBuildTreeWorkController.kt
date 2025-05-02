/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.execution.EntryTaskSelector
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.buildtree.BuildTreeWorkController
import org.gradle.internal.buildtree.BuildTreeWorkExecutor
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.buildtree.BuildTreeWorkPreparer


class VintageBuildTreeWorkController(
    private val workPreparer: BuildTreeWorkPreparer,
    private val workExecutor: BuildTreeWorkExecutor,
    private val taskGraph: BuildTreeWorkGraphController
) : BuildTreeWorkController {

    override fun scheduleAndRunRequestedTasks(taskSelector: EntryTaskSelector?): ExecutionResult<Void> {
        return taskGraph.withNewWorkGraph { graph: BuildTreeWorkGraph ->
            val finalizedGraph: BuildTreeWorkGraph.FinalizedGraph = workPreparer.scheduleRequestedTasks(graph, taskSelector)
            workExecutor.execute(finalizedGraph)
        }
    }
}
