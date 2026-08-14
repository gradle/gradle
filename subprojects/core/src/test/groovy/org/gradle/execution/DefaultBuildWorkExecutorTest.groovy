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
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.execution.plan.AbstractExecutionPlanSpec
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.PlanExecutor
import org.gradle.execution.plan.QueryableExecutionPlan
import org.gradle.execution.plan.ScheduledWork
import org.gradle.execution.plan.TaskNode
import org.gradle.execution.plan.WorkSource
import org.gradle.execution.taskgraph.TaskExecutionGraphExecutionListener
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.service.ServiceRegistry
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.Path
import org.junit.Rule

/**
 * Tests {@link DefaultBuildWorkExecutor}.
 */
class DefaultBuildWorkExecutorTest extends AbstractExecutionPlanSpec {

    @Rule
    ConcurrentTestUtil concurrent = new ConcurrentTestUtil()

    ServiceRegistry buildScopeServices = Stub(ServiceRegistry) {
        get(TaskDependencyFactory) >> TestFiles.taskDependencyFactory()
    }
    PlanExecutor planExecutor = Mock(PlanExecutor)
    StyledTextOutputFactory textOutputFactory = new TestStyledTextOutputFactory()
    ConfigurationTimeBarrier configurationTimeBarrier = Mock(ConfigurationTimeBarrier)

    DefaultBuildWorkExecutor underTest = new DefaultBuildWorkExecutor(
        buildScopeServices,
        planExecutor,
        textOutputFactory,
        configurationTimeBarrier,
        new TestBuildOperationRunner()
    )

    TaskExecutionGraphExecutionListener executionGraphExecutionListener = Mock(TaskExecutionGraphExecutionListener)
    FinalizedExecutionPlan populatedPlan
    TaskExecutionGraphInternal taskExecutionGraph = Mock(TaskExecutionGraphInternal) {
        getGraphExecutionListeners() >> executionGraphExecutionListener
        getExecutionPlan() >> { populatedPlan }
    }
    StartParameterInternal startParameter = Mock(StartParameterInternal)
    GradleInternal gradle = Mock(GradleInternal) {
        getTaskGraph() >> taskExecutionGraph
        getStartParameter() >> startParameter
    }

    def "closes plan after executing"() {
        def plan = newPlan()

        when:
        underTest.execute(gradle, plan)

        then:
        1 * planExecutor.process(_, _) >> ExecutionResult.succeeded()

        then:
        1 * plan.close()
    }

    def "depopulates task graph after executing"() {
        def plan = newPlan()

        when:
        underTest.execute(gradle, plan)

        then:
        1 * planExecutor.process(_, _) >> ExecutionResult.succeeded()

        then:
        1 * taskExecutionGraph.depopulate()
    }

    def "notifies task graph execution listener after executing"() {
        def plan = newPlan()

        when:
        underTest.execute(gradle, plan)

        then:
        1 * executionGraphExecutionListener.beforeGraphExecutionStarts(plan.getContents())

        then:
        1 * planExecutor.process(_, _) >> ExecutionResult.succeeded()
    }

    def "notifies task graph execution listener before starting execution"() {
        def plan = newPlan()

        when:
        underTest.execute(gradle, plan)

        then:
        1 * executionGraphExecutionListener.beforeGraphExecutionStarts(_)

        then:
        1 * planExecutor.process(_, _) >> ExecutionResult.succeeded()
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

    def "print all selected tasks before proceeding when dry run is enabled"() {
        def task1 = Mock(TaskNode) {
            getTask() >> Mock(TaskInternal)
        }
        def task2 = Mock(TaskNode) {
            getTask() >> Mock(TaskInternal)
        }

        given:
        startParameter.isDryRun() >> true
        configurationTimeBarrier.isAtConfigurationTime() >> false
        def plan = newPlan([task1, task2])

        when:
        underTest.execute(gradle, plan)

        then:
        textOutputFactory.category == DefaultBuildWorkExecutor.canonicalName
        textOutputFactory.output == """:task1 {progressstatus}SKIPPED
:task2 {progressstatus}SKIPPED
"""
        1 * task1.getTask().getIdentityPath() >> Path.path(':task1')
        1 * task2.getTask().getIdentityPath() >> Path.path(':task2')
    }

    FinalizedExecutionPlan newPlan(List<Node> scheduledNodes) {
        populatedPlan = Mock(FinalizedExecutionPlan) {
            getContents() >> Stub(QueryableExecutionPlan) {
                getScheduledNodes() >> new ScheduledWork(ImmutableList.copyOf(scheduledNodes), ImmutableSet.copyOf(scheduledNodes))
                getTasks() >> {
                    scheduledNodes.findAll { it instanceof TaskNode }.collect { ((TaskNode) it).getTask() }
                }
            }
            asWorkSource() >> Stub(WorkSource)
        }
    }

    FinalizedExecutionPlan newPlan() {
        populatedPlan = Mock(FinalizedExecutionPlan) {
            getContents() >> Stub(QueryableExecutionPlan)
            asWorkSource() >> Stub(WorkSource)
        }

    }

}
