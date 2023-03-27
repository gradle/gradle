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

package org.gradle.internal.build

import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.execution.plan.ExecutionPlan
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.QueryableExecutionPlan
import org.gradle.execution.plan.ToPlannedNodeConverterRegistry
import org.gradle.execution.plan.ToPlannedTaskConverter
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.NodeIdentity
import org.gradle.util.Path
import spock.lang.Specification

import java.util.function.Consumer

class BuildOperationFiringBuildWorkPreparerTest extends Specification {

    def "build operation provides execution plan when queries with all node types"() {
        def ti1 = TestTaskIdentities.create("t1", Task, Stub(ProjectInternal) {
            projectPath(_) >> { args -> Path.path(":" + args[0]) }
        })
        def t = Stub(LocalTaskNode) {
            getTask() >> Stub(TaskInternal) {
                getTaskIdentity() >> ti1
            }
        }
        List<Node> nodes = [t]

        def scheduledNodesStub = Stub(QueryableExecutionPlan.ScheduledNodes) {
            visitNodes(_) >> { Consumer<List<Node>> consumer ->
                consumer.accept(nodes)
            }
        }

        def executionPlan = Stub(ExecutionPlan) {
            getContents() >> Stub(QueryableExecutionPlan) {
                getScheduledNodes() >> scheduledNodesStub
            }
        }

        def buildOperatorsExecutor = new TestBuildOperationExecutor()
        def converterRegistry = new ToPlannedNodeConverterRegistry([new ToPlannedTaskConverter()])
        def preparer = new BuildOperationFiringBuildWorkPreparer(buildOperatorsExecutor, Stub(BuildWorkPreparer), converterRegistry)

        when:
        preparer.populateWorkGraph(Stub(GradleInternal), executionPlan, {})
        then:
        def result = buildOperatorsExecutor.log.mostRecentResult(CalculateTaskGraphBuildOperationType)
        result != null
        def plannedNodes = result.getExecutionPlan(EnumSet.allOf(NodeIdentity.NodeType))
        plannedNodes.size() == 1
        with(plannedNodes[0]) {
            nodeIdentity.nodeType == NodeIdentity.NodeType.TASK
            (nodeIdentity as CalculateTaskGraphBuildOperationType.TaskIdentity).taskPath == ":t1"
        }
    }
}
