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
package org.gradle.internal.cc.impl.barrier

import org.gradle.execution.EntryTaskSelector
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.buildtree.BuildTreeWorkPreparer

/**
 * Builds task graph while managing the configuration time barrier in the vintage mode.
 */
internal class BarrierAwareBuildTreeWorkPreparer(
    private val runner: VintageConfigurationTimeActionRunner,
    private val delegate: BuildTreeWorkPreparer
) : BuildTreeWorkPreparer {
    override fun scheduleRequestedTasks(
        graph: BuildTreeWorkGraph,
        selector: EntryTaskSelector?
    ): BuildTreeWorkGraph.FinalizedGraph {
        return runner.runConfigurationTimeAction {
            delegate.scheduleRequestedTasks(graph, selector)
        }
    }
}
