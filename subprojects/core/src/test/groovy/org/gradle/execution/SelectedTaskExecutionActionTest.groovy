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
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import spock.lang.Specification

class SelectedTaskExecutionActionTest extends Specification {
    final SelectedTaskExecutionAction action = new SelectedTaskExecutionAction()
    final BuildExecutionContext context = Mock()
    final TaskExecutionGraphInternal taskGraph = Mock()
    final GradleInternal gradleInternal = Mock()
    final StartParameterInternal startParameter = Mock()

    def setup() {
        _ * context.gradle >> gradleInternal
        _ * gradleInternal.taskGraph >> taskGraph
        _ * gradleInternal.startParameter >> startParameter
    }

    def "executes selected tasks"() {
        def failures = []

        given:
        _ * startParameter.continueOnFailure >> false
        _ * taskGraph.allTasks >> []

        when:
        action.execute(context, failures)

        then:
        1 * taskGraph.execute(failures)
    }

    def "executes selected tasks when continue specified"() {
        def failures = []

        given:
        _ * startParameter.continueOnFailure >> true
        _ * taskGraph.allTasks >> []

        when:
        action.execute(context, failures)

        then:
        1 * taskGraph.setContinueOnFailure(true)
        1 * taskGraph.execute(failures)
    }
}
