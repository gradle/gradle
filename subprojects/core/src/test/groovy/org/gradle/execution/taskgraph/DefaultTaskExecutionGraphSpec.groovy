/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.execution.taskgraph

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.internal.BuildScopeListenerRegistrationListener
import org.gradle.configuration.internal.TestListenerBuildOperationDecorator
import org.gradle.execution.plan.AbstractExecutionPlanSpec
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.plan.QueryableExecutionPlan
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.service.scopes.Scope

class DefaultTaskExecutionGraphSpec extends AbstractExecutionPlanSpec {

    def listenerManager = new DefaultListenerManager(Scope.Build)
    def graphListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class)
    def internalGraphListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionGraphExecutionListener.class)
    def taskExecutionListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class)
    def listenerRegistrationListener = listenerManager.getBroadcaster(BuildScopeListenerRegistrationListener.class)
    def buildOperationRunner = new TestBuildOperationRunner()
    def listenerBuildOperationDecorator = new TestListenerBuildOperationDecorator()
    def taskGraph = new DefaultTaskExecutionGraph(
        buildOperationRunner,
        listenerBuildOperationDecorator,
        thisBuild,
        graphListeners,
        internalGraphListeners,
        taskExecutionListeners,
        listenerRegistrationListener,
    )

    def "is empty when no tasks have been added"() {
        expect:
        !taskGraph.hasTask(":a")
        taskGraph.allTasks.empty
    }

    def "retains all tasks list after execute until next execution"() {
        Task a = createTask("a")
        Task b = createTask("b")
        Task c = createTask("c")

        when:
        populate([a, b])
        taskGraph.allTasks
        taskGraph.depopulate()

        then:
        // tests existing behaviour, not desired behaviour
        !taskGraph.hasTask(":a")
        !taskGraph.hasTask(a)
        taskGraph.allTasks == [a, b]

        when:
        populate([c])

        then:
        !taskGraph.hasTask(":a")
        !taskGraph.hasTask(a)
        taskGraph.allTasks == [c]
    }

    def "allTasks returns tasks from populated plan"() {
        final Task a = createTask("a")
        final Task b = createTask("b")

        when:
        populate([a, b])

        then:
        taskGraph.allTasks == [a, b]
    }

    def "can populate multiple times"() {
        Task a = createTask("a")
        Task b = createTask("b")

        when:
        populate([a, b])

        then:
        taskGraph.allTasks == [a, b]

        when:
        Task c = createTask("c")
        populate([c])

        then:
        taskGraph.allTasks == [c]
    }

    def "notifies graph listener before first execute"() {
        def taskGraph = new DefaultTaskExecutionGraph(
            buildOperationRunner,
            listenerBuildOperationDecorator,
            thisBuild,
            graphListeners,
            internalGraphListeners,
            taskExecutionListeners,
            listenerRegistrationListener
        )
        TaskExecutionGraphListener listener = Mock(TaskExecutionGraphListener)

        when:
        taskGraph.addTaskExecutionGraphListener(listener)
        def finalizedPlan = Stub(FinalizedExecutionPlan)
        taskGraph.populate(finalizedPlan)

        then:
        1 * listener.graphPopulated(_)

        when:
        taskGraph.populate(finalizedPlan)

        then:
        0 * listener._
    }

    def "executes whenReady listener before first execute"() {
        def taskGraph = new DefaultTaskExecutionGraph(
            buildOperationRunner,
            listenerBuildOperationDecorator,
            thisBuild,
            graphListeners,
            internalGraphListeners,
            taskExecutionListeners,
            listenerRegistrationListener
        )
        def closure = Mock(Closure)
        def action = Mock(Action)

        when:
        taskGraph.whenReady(closure)
        taskGraph.whenReady(action)
        def finalizedPlan = Stub(FinalizedExecutionPlan)
        taskGraph.populate(finalizedPlan)

        then:
        1 * closure.call()
        1 * action.execute(_)

        and:
        with(buildOperationRunner.operations[0]) {
            name == 'Notify task graph whenReady listeners'
            displayName == 'Notify task graph whenReady listeners'
            details.buildPath == ':'
        }

        when:
        def finalizedPlan2 = Stub(FinalizedExecutionPlan)
        taskGraph.populate(finalizedPlan2)

        then:
        0 * closure._
        0 * action._
    }

    def "notifies before task listeners"() {
        def closure = Mock(Closure) {
            _ * getMaximumNumberOfParameters() >> 1
        }
        def action = Mock(Action)

        final Task a = createTask("a")
        final Task b = createTask("b")

        when:
        taskGraph.beforeTask(closure)
        taskGraph.beforeTask(action)
        taskExecutionListeners.source.beforeExecute(a)
        taskExecutionListeners.source.beforeExecute(b)

        then:
        1 * closure.call(a)
        1 * closure.call(b)
        1 * action.execute(a)
        1 * action.execute(b)
    }

    def "notifies after task listeners"() {
        def closure = Mock(Closure) {
            _ * getMaximumNumberOfParameters() >> 1
        }
        def action = Mock(Action)

        final Task a = createTask("a")
        final Task b = createTask("b")

        when:
        taskGraph.afterTask(closure)
        taskGraph.afterTask(action)
        taskExecutionListeners.source.afterExecute(a, a.state)
        taskExecutionListeners.source.afterExecute(b, b.state)

        then:
        1 * closure.call(a)
        1 * closure.call(b)
        1 * action.execute(a)
        1 * action.execute(b)
    }

    private populate(List<Task> tasks) {
        def finalizedPlan = Mock(FinalizedExecutionPlan) {
            getContents() >> Mock(QueryableExecutionPlan) {
                getTasks() >> (tasks as Set)
            }
        }
        taskGraph.populate(finalizedPlan)
    }

}
