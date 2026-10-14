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

package org.gradle.api.internal.tasks.execution.durations

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.TaskInternal
import org.gradle.execution.plan.AbstractExecutionPlanSpec
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.QueryableExecutionPlan
import org.gradle.execution.plan.ScheduledWork

import java.time.Duration

class ExecutionTimeEstimatesTest extends AbstractExecutionPlanSpec {
    def history = Mock(TaskDurationHistory)
    def estimates = new ExecutionTimeEstimates(history)

    def "estimates total duration and the longest chain of dependent tasks"() {
        given:
        def a = taskNode("a", 1000)
        def b = taskNode("b", 2000)
        def slow = taskNode("slow", 5000)
        def all = taskNode("all", 0)
        a.node.getDependencyPredecessors() >> [slow.node]
        b.node.getDependencyPredecessors() >> [slow.node]
        slow.node.getDependencyPredecessors() >> [all.node]

        when:
        def estimate = estimates.estimatePlan(plan([a, b, slow, all]))

        then:
        estimate.durationMillis == 8000
        estimate.criticalPathMillis == 7000
        estimateOf(a) == [1000, 6000]
        estimateOf(b) == [2000, 7000]
        estimateOf(slow) == [5000, 5000]
        estimateOf(all) == [0, 0]
    }

    def "includes finalizers in the chain of a task"() {
        given:
        def a = taskNode("a", 1000)
        def finalizer = taskNode("finalizer", 3000)
        a.node.getFinalizers() >> [finalizer.node]

        when:
        def estimate = estimates.estimatePlan(plan([a, finalizer]))

        then:
        estimate.criticalPathMillis == 4000
        estimateOf(a) == [1000, 4000]
    }

    def "chains pass through nodes other than tasks"() {
        given:
        def a = taskNode("a", 1000)
        def b = taskNode("b", 2000)
        def between = Stub(Node)
        a.node.getDependencyPredecessors() >> [between]
        between.getDependencyPredecessors() >> [b.node]

        when:
        def estimate = estimates.estimatePlan(plan([a.node, between, b.node], [a.task, b.task]))

        then:
        estimate.durationMillis == 3000
        estimate.criticalPathMillis == 3000
        estimateOf(a) == [1000, 3000]
    }

    def "does not estimate when any task has no recorded duration"() {
        given:
        def a = taskNode("a", 1000)
        def b = taskNode("b", null)

        expect:
        estimates.estimatePlan(plan([a, b])) == null
        estimates.estimateTask(a.task.taskIdentity) == null
    }

    def "does not estimate when the plan contains work other than tasks"() {
        given:
        def a = taskNode("a", 1000)
        def contents = Stub(QueryableExecutionPlan) {
            getTasks() >> ([a.task] as Set)
            size() >> 2
        }

        expect:
        estimates.estimatePlan(contents) == null
    }

    def "forgets the estimates of the tasks of a previous plan"() {
        given:
        def a = taskNode("a", 1000)
        def b = taskNode("b", null)
        estimates.estimatePlan(plan([a]))

        when:
        estimates.estimatePlan(plan([b]))

        then:
        estimates.estimateTask(a.task.taskIdentity) == null
    }

    private Map taskNode(String name, Long millis) {
        TaskInternal task = createTask(name)
        LocalTaskNode node = Stub(LocalTaskNode) {
            getTask() >> task
        }
        history.getDurations({ it.name == name }) >> (millis == null ? null : TaskDurations.of([Duration.ofMillis(millis)]))
        [task: task, node: node]
    }

    private QueryableExecutionPlan plan(List<Map> taskNodes) {
        plan(taskNodes.collect { it.node as Node }, taskNodes.collect { it.task as TaskInternal })
    }

    private QueryableExecutionPlan plan(List<Node> nodes, List<TaskInternal> tasks) {
        Stub(QueryableExecutionPlan) {
            getTasks() >> (tasks as Set)
            size() >> tasks.size()
            getScheduledNodes() >> new ScheduledWork(ImmutableList.copyOf(nodes), ImmutableSet.of())
        }
    }

    private List<Long> estimateOf(Map taskNode) {
        def estimate = estimates.estimateTask((taskNode.task as TaskInternal).taskIdentity)
        [estimate.durationMillis, estimate.criticalPathMillis]
    }
}
