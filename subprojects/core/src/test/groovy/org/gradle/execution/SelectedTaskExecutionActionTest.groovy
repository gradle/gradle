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

package org.gradle.execution

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.execution.plan.AbstractExecutionPlanSpec
import org.gradle.execution.plan.DefaultPlanExecutor
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.NodeExecutor
import org.gradle.execution.plan.PlanExecutor
import org.gradle.execution.plan.QueryableExecutionPlan
import org.gradle.execution.plan.ScheduledWork
import org.gradle.execution.plan.WorkSource
import org.gradle.execution.taskgraph.TaskExecutionGraphExecutionListener
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.operations.BuildOperationRef
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.service.ServiceRegistry
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.junit.Rule

import java.util.concurrent.atomic.AtomicReference

/**
 * Tests {@link SelectedTaskExecutionAction}.
 */
class SelectedTaskExecutionActionTest extends AbstractExecutionPlanSpec {

    @Rule
    ConcurrentTestUtil concurrent = new ConcurrentTestUtil()

    ServiceRegistry buildScopeServices = Stub(ServiceRegistry) {
        get(TaskDependencyFactory) >> TestFiles.taskDependencyFactory()
    }
    PlanExecutor planExecutor = Mock(DefaultPlanExecutor)
    BuildOperationRunner buildOperationRunner = new TestBuildOperationRunner()
    NodeExecutor nodeExecutor = Mock(NodeExecutor)

    SelectedTaskExecutionAction underTest = new SelectedTaskExecutionAction(
        buildScopeServices,
        planExecutor,
        buildOperationRunner,
        nodeExecutor
    )

    TaskExecutionGraphExecutionListener executionGraphExecutionListener = Mock(TaskExecutionGraphExecutionListener)
    FinalizedExecutionPlan populatedPlan
    TaskExecutionGraphInternal taskExecutionGraph = Mock(TaskExecutionGraphInternal) {
        getGraphExecutionListeners() >> executionGraphExecutionListener
        getExecutionPlan() >> { populatedPlan }
    }
    GradleInternal gradle = Mock(GradleInternal) {
        getTaskGraph() >> taskExecutionGraph
    }

    def "closes plan after executing"() {
        def plan = newPlan()

        when:
        underTest.execute(gradle, plan)

        then:
        1 * plan.close()
    }

    def "depopulates task graph after executing"() {
        def plan = newPlan()

        when:
        underTest.execute(gradle, plan)

        then:
        1 * taskExecutionGraph.depopulate()
    }

    def "notifies task graph execution listener after executing"() {
        def plan = newPlan()

        when:
        underTest.execute(gradle, plan)

        then:
        1 * executionGraphExecutionListener.beforeGraphExecutionStarts(plan.getContents())
    }

    def "notifies task graph execution listener before starting execution"() {
        def plan = newPlan()

        when:
        underTest.execute(gradle, plan)

        then:
        1 * executionGraphExecutionListener.beforeGraphExecutionStarts(_)

        then:
        1 * planExecutor.process(_, _)
    }

    def "closes plan and depopulates task graph when execution fails"() {
        def plan = newPlan()
        def failure = new RuntimeException("broken")

        when:
        underTest.execute(gradle, plan)

        then:
        1 * planExecutor.process(_, _) >> { throw failure }
        1 * plan.close()
        1 * taskExecutionGraph.depopulate()

        and:
        def thrown = thrown(RuntimeException)
        thrown.is(failure)
    }

    def "binds the model rules of every project that owns a scheduled task"() {
        def otherProject = project(project, "other")
        def plan = newPlan([taskNodeOwnedBy(project), taskNodeOwnedBy(otherProject)])

        when:
        underTest.execute(gradle, plan)

        then:
        1 * project.bindAllModelRules()
        1 * otherProject.bindAllModelRules()
    }

    def "binds the model rules of a project only once when it owns several scheduled tasks"() {
        def plan = newPlan([taskNodeOwnedBy(project), taskNodeOwnedBy(project)])

        when:
        underTest.execute(gradle, plan)

        then:
        1 * project.bindAllModelRules()
    }

    def "does not bind model rules for scheduled nodes that are not tasks"() {
        def plan = newPlan([Mock(Node) { getOwningProject() >> project }])

        when:
        underTest.execute(gradle, plan)

        then:
        0 * project.bindAllModelRules()
    }

    def "fails when the node executor does not know how to execute a node"() {
        def node = Mock(Node)
        def plan = newPlan()

        when:
        underTest.execute(gradle, plan)

        then:
        1 * planExecutor.process(_, _) >> { WorkSource<Node> source, Action<Node> nodeAction ->
            nodeAction.execute(node)
        }
        1 * nodeExecutor.execute(node, _) >> false

        and:
        def failure = thrown(IllegalStateException)
        failure.message.startsWith("Unknown type of node: ")
    }

    def "runs nodes with the build operation captured on the thread that started execution"() {
        def callingThread = Thread.currentThread()
        def parentOperation = Stub(BuildOperationRef) {
            getId() >> new OperationIdentifier(42L)
        }
        def buildOperationRunner = Stub(BuildOperationRunner) {
            getCurrentOperation() >> {
                // Verify the build operation from the calling thread is captured
                assert Thread.currentThread() == callingThread
                parentOperation
            }
        }
        def action = new SelectedTaskExecutionAction(buildScopeServices, planExecutor, buildOperationRunner, nodeExecutor)
        def node = Mock(Node)
        def plan = newPlan()
        def operationWhileExecuting = new AtomicReference<BuildOperationRef>()

        when:
        action.execute(gradle, plan)

        then:
        1 * planExecutor.process(_, _) >> { WorkSource<Node> source, Action<Node> nodeAction ->
            concurrent.start { nodeAction.execute(node) }.completed()
        }
        1 * nodeExecutor.execute(node, _) >> {
            operationWhileExecuting.set(CurrentBuildOperationRef.instance().get())
            true
        }

        and:
        operationWhileExecuting.get().is(parentOperation)
    }

    def "can execute multiple times"() {
        def plan = newPlan()
        def plan2 = newPlan()
        def planResult = Stub(ExecutionResult)
        def planResult2 = Stub(ExecutionResult)

        when:
        populatedPlan = plan
        def result = underTest.execute(gradle, plan)

        then:
        1 * planExecutor.process(plan.asWorkSource(), _) >> planResult
        result.is(planResult)

        when:
        populatedPlan = plan2
        def result2 = underTest.execute(gradle, plan2)

        then:
        1 * planExecutor.process(plan2.asWorkSource(), _) >> planResult2
        result2.is(planResult2)
    }

    def "fails when given a plan that the task graph was not populated with"() {
        def otherPlan = newPlan()
        def plan = newPlan()
        populatedPlan = otherPlan

        when:
        underTest.execute(gradle, plan)

        then:
        thrown(IllegalStateException)
        0 * planExecutor.process(_, _)
        0 * taskExecutionGraph.depopulate()
    }

    FinalizedExecutionPlan newPlan(List<Node> scheduledNodes) {
        populatedPlan = Mock(FinalizedExecutionPlan) {
            getContents() >> Stub(QueryableExecutionPlan) {
                getScheduledNodes() >> new ScheduledWork(ImmutableList.copyOf(scheduledNodes), ImmutableSet.copyOf(scheduledNodes))
            }
            asWorkSource() >> Stub(WorkSource)
        }
    }

    LocalTaskNode taskNodeOwnedBy(ProjectInternal owner) {
        Mock(LocalTaskNode) {
            getOwningProject() >> owner
        }
    }

    FinalizedExecutionPlan newPlan() {
        populatedPlan = Mock(FinalizedExecutionPlan) {
            getContents() >> Stub(QueryableExecutionPlan)
            asWorkSource() >> Stub(WorkSource)
        }

    }

}
