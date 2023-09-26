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


import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.configuration.project.BuiltInCommand
import org.gradle.execution.plan.ExecutionPlan
import org.gradle.internal.DefaultTaskExecutionRequest
import org.gradle.internal.RunDefaultTasksExecutionRequest
import spock.lang.Specification

class DefaultTasksBuildTaskSchedulerTest extends Specification {
    final projectConfigurer = Mock(ProjectConfigurer)
    final buildInCommand = Mock(BuiltInCommand)
    final delegate = Mock(BuildTaskScheduler)
    final action = new DefaultTasksBuildTaskScheduler(projectConfigurer, [buildInCommand], delegate)
    final startParameter = Mock(StartParameterInternal)
    final defaultProject = Mock(ProjectInternal)
    final gradle = Mock(GradleInternal)
    final selector = Mock(EntryTaskSelector)
    final plan = Mock(ExecutionPlan)

    def setup() {
        _ * gradle.startParameter >> startParameter
        _ * gradle.defaultProject >> defaultProject
    }

    def "proceeds when task request specified in StartParameter"() {
        given:
        _ * startParameter.taskRequests >> [new DefaultTaskExecutionRequest(['a'])]

        when:
        action.scheduleRequestedTasks(gradle, selector, plan, false)

        then:
        1 * delegate.scheduleRequestedTasks(gradle, selector, plan, false)
    }

    def "proceeds when no task requests specified in StartParameter"() {
        given:
        _ * startParameter.taskRequests >> []

        when:
        action.scheduleRequestedTasks(gradle, selector, plan, false)

        then:
        1 * delegate.scheduleRequestedTasks(gradle, selector, plan, false)
    }

    def "sets task names to project defaults when single task requests specified in StartParameter"() {
        given:
        _ * startParameter.taskRequests >> [new RunDefaultTasksExecutionRequest()]
        _ * defaultProject.defaultTasks >> ['a', 'b']

        when:
        action.scheduleRequestedTasks(gradle, selector, plan, false)

        then:
        1 * startParameter.setTaskNames(['a', 'b'])
        1 * delegate.scheduleRequestedTasks(gradle, selector, plan, false)
    }

    def "uses default build-in tasks if no tasks specified in StartParameter or project"() {
        given:
        _ * startParameter.taskRequests >> [new RunDefaultTasksExecutionRequest()]
        _ * defaultProject.defaultTasks >> []
        _ * buildInCommand.asDefaultTask() >> ['default1', 'default2']

        when:
        action.scheduleRequestedTasks(gradle, selector, plan, false)

        then:
        1 * startParameter.setTaskNames(['default1', 'default2'])
        1 * delegate.scheduleRequestedTasks(gradle, selector, plan, false)
    }
}
