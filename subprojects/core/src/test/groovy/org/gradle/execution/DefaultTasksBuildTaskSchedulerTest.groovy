/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.configuration.project.BuiltInCommand
import org.gradle.execution.plan.ExecutionPlan
import org.gradle.internal.DefaultTaskExecutionRequest
import org.gradle.internal.RunDefaultTasksExecutionRequest
import spock.lang.Specification

import java.util.function.Function

class DefaultTasksBuildTaskSchedulerTest extends Specification {
    final buildInCommand = Mock(BuiltInCommand)
    final delegate = Mock(BuildTaskScheduler)
    final action = new DefaultTasksBuildTaskScheduler([buildInCommand], delegate)
    final defaultProject = Mock(ProjectInternal)
    final defaultProjectState = Mock(ProjectState)
    final gradle = Mock(GradleInternal)
    final selector = Mock(EntryTaskSelector)
    final plan = Mock(ExecutionPlan)

    def setup() {
        _ * gradle.defaultProjectState >> defaultProjectState
        _ * defaultProjectState.fromMutableState(_) >> { Function f -> f.apply(defaultProject) }
    }

    def "proceeds with given task requests"() {
        given:
        def requests = [new DefaultTaskExecutionRequest(['a'])]

        when:
        action.scheduleRequestedTasks(gradle, requests, selector, plan)

        then:
        1 * delegate.scheduleRequestedTasks(gradle, requests, selector, plan)
    }

    def "proceeds when no task requests given"() {
        given:
        def requests = []

        when:
        action.scheduleRequestedTasks(gradle, requests, selector, plan)

        then:
        1 * delegate.scheduleRequestedTasks(gradle, requests, selector, plan)
    }

    def "resolves default-tasks request to project defaults"() {
        given:
        _ * defaultProject.defaultTasks >> ['a', 'b']

        when:
        action.scheduleRequestedTasks(gradle, [new RunDefaultTasksExecutionRequest()], selector, plan)

        then:
        1 * defaultProjectState.ensureConfigured()
        1 * delegate.scheduleRequestedTasks(gradle, { List<TaskExecutionRequest> requests ->
            requests.size() == 1 && requests[0].args == ['a', 'b']
        }, selector, plan)
    }

    def "resolves default-tasks request to built-in tasks if project defines no default tasks"() {
        given:
        _ * defaultProject.defaultTasks >> []
        _ * buildInCommand.asDefaultTask() >> ['default1', 'default2']

        when:
        action.scheduleRequestedTasks(gradle, [new RunDefaultTasksExecutionRequest()], selector, plan)

        then:
        1 * delegate.scheduleRequestedTasks(gradle, { List<TaskExecutionRequest> requests ->
            requests.size() == 1 && requests[0].args == ['default1', 'default2']
        }, selector, plan)
    }
}
