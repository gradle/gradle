/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.TaskExecutionRequest
import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.execution.commandline.CommandLineTaskParser
import org.gradle.execution.plan.ExecutionPlan
import org.gradle.execution.selection.BuildTaskSelector
import spock.lang.Specification

class TaskNameResolvingBuildTaskSchedulerSpec extends Specification {
    GradleInternal gradle
    ExecutionPlan executionPlan
    CommandLineTaskParser parser
    EntryTaskSelector selector
    TaskNameResolvingBuildTaskScheduler action

    def setup() {
        gradle = Mock(GradleInternal)
        executionPlan = Mock(ExecutionPlan)
        parser = Mock(CommandLineTaskParser)
        selector = Mock(EntryTaskSelector)
        action = new TaskNameResolvingBuildTaskScheduler(parser, Stub(BuildTaskSelector.BuildSpecificSelector))
    }

    def "empty task parameters are no-op action"() {
        given:
        def startParameters = Mock(StartParameterInternal)

        when:
        _ * gradle.getStartParameter() >> startParameters
        _ * startParameters.getTaskRequests() >> []

        action.scheduleRequestedTasks(gradle, null, executionPlan, false)

        then:
        0 * executionPlan._
    }

    def "expand task parameters to tasks"() {
        def startParameters = Mock(StartParameterInternal)
        TaskExecutionRequest request1 = Stub(TaskExecutionRequest)
        TaskExecutionRequest request2 = Stub(TaskExecutionRequest)
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        def task3 = Stub(Task)
        def selection1 = Stub(TaskSelection)
        def selection2 = Stub(TaskSelection)

        given:
        _ * gradle.startParameter >> startParameters
        _ * startParameters.taskRequests >> [request1, request2]

        def tasks1 = [task1, task2] as Set
        _ * selection1.tasks >> tasks1

        def tasks2 = [task3] as Set
        _ * selection2.tasks >> tasks2

        when:
        action.scheduleRequestedTasks(gradle, null, executionPlan, false)

        then:
        1 * parser.parseTasks(request1) >> [selection1]
        1 * parser.parseTasks(request2) >> [selection2]
        1 * executionPlan.addEntryTasks(tasks1)
        1 * executionPlan.addEntryTasks(tasks2)
    }

    def "invokes given selector"() {
        given:
        def startParameters = Mock(StartParameterInternal)

        when:
        _ * gradle.getStartParameter() >> startParameters
        _ * startParameters.getTaskRequests() >> []

        action.scheduleRequestedTasks(gradle, selector, executionPlan,)

        then:
        1 * selector.applyTasksTo(_, executionPlan)
        0 * executionPlan._
    }

}
