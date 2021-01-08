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
import org.gradle.internal.DefaultTaskExecutionRequest
import spock.lang.Specification

class DefaultTasksBuildExecutionActionTest extends Specification {
    final projectConfigurer = Mock(ProjectConfigurer)
    final DefaultTasksBuildExecutionAction action = new DefaultTasksBuildExecutionAction(projectConfigurer)
    final context = Mock(BuildExecutionContext)
    final startParameter = Mock(StartParameterInternal)
    final defaultProject = Mock(ProjectInternal)

    def setup() {
        GradleInternal gradle = Mock()
        _ * context.gradle >> gradle
        _ * gradle.startParameter >> startParameter
        _ * gradle.defaultProject >> defaultProject
    }

    def "proceeds when task names specified in StartParameter"() {
        given:
        _ * startParameter.taskRequests >> [ new DefaultTaskExecutionRequest(['a']) ]

        when:
        action.configure(context)

        then:
        1 * context.proceed()
    }

    def "sets task names to project defaults when no requests specified in StartParameter"() {
        given:
        _ * startParameter.taskRequests >> []
        _ * defaultProject.defaultTasks >> ['a', 'b']

        when:
        action.configure(context)

        then:
        1 * startParameter.setTaskNames(['a', 'b'])
        1 * context.proceed()
    }

    def "uses the help task if no tasks specified in StartParameter or project"() {
        given:
        _ * startParameter.taskRequests >> []
        _ * defaultProject.defaultTasks >> []

        when:
        action.configure(context)

        then:
        1 * startParameter.setTaskNames(['help'])
        1 * context.proceed()
    }
}
