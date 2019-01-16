/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.changedetection.changes

import org.gradle.StartParameter
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.TaskExecutionMode
import org.gradle.api.internal.tasks.properties.TaskProperties
import org.gradle.api.specs.AndSpec
import spock.lang.Specification

import static org.gradle.api.internal.changedetection.TaskExecutionMode.INCREMENTAL
import static org.gradle.api.internal.changedetection.TaskExecutionMode.NO_OUTPUTS_WITHOUT_ACTIONS
import static org.gradle.api.internal.changedetection.TaskExecutionMode.NO_OUTPUTS_WITH_ACTIONS
import static org.gradle.api.internal.changedetection.TaskExecutionMode.RERUN_TASKS_ENABLED
import static org.gradle.api.internal.changedetection.TaskExecutionMode.UP_TO_DATE_WHEN_FALSE

class DefaultTaskExecutionModeResolverTest extends Specification {

    def startParameter = new StartParameter()
    def repository = new DefaultTaskExecutionModeResolver(startParameter)
    def inputs = Mock(TaskInputsInternal)
    def outputs = Mock(TaskOutputsInternal)
    def taskProperties = Mock(TaskProperties)
    def task = Mock(TaskInternal)
    def upToDateSpec = Mock(AndSpec)

    def setup() {
        _ * task.getInputs() >> inputs
        _ * task.getOutputs() >> outputs
        _ * outputs.getUpToDateSpec() >> upToDateSpec
    }

    def "no actions with no outputs"() {
        when:
        TaskExecutionMode state = repository.getExecutionMode(task, taskProperties)

        then:
        state == NO_OUTPUTS_WITHOUT_ACTIONS
        1 * upToDateSpec.isEmpty() >> true
        1 * taskProperties.hasDeclaredOutputs() >> false
        1 * task.hasTaskActions() >> false
    }

    def "no actions with outputs"() {
        when:
        TaskExecutionMode state = repository.getExecutionMode(task, taskProperties)

        then:
        state == NO_OUTPUTS_WITH_ACTIONS
        1 * upToDateSpec.isEmpty() >> true
        1 * taskProperties.hasDeclaredOutputs() >> false
        1 * task.hasTaskActions() >> true
    }

    def "default"() {
        when:
        TaskExecutionMode state = repository.getExecutionMode(task, taskProperties)

        then:
        state == INCREMENTAL
        1 * taskProperties.hasDeclaredOutputs() >> true
        1 * upToDateSpec.isSatisfiedBy(task) >> true
    }

    def "--rerun-tasks enabled"() {
        when:
        startParameter.setRerunTasks(true)
        def state = repository.getExecutionMode(task, taskProperties)

        then:
        state == RERUN_TASKS_ENABLED
        1 * upToDateSpec.empty >> false
    }

    def "uoToDateSpec evaluates to false"() {
        when:
        def state = repository.getExecutionMode(task, taskProperties)

        then:
        state == UP_TO_DATE_WHEN_FALSE
        1 * taskProperties.hasDeclaredOutputs() >> true
        1 * upToDateSpec.isSatisfiedBy(task) >> false
    }
}
