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
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.tasks.execution.TaskProperties
import org.gradle.api.specs.AndSpec
import spock.lang.Specification

import static org.gradle.api.internal.changedetection.TaskArtifactState.INCREMENTAL
import static org.gradle.api.internal.changedetection.TaskArtifactState.RERUN_TASKS_ENABLED
import static org.gradle.api.internal.changedetection.TaskArtifactState.UP_TO_DATE_WHEN_FALSE
import static org.gradle.api.internal.changedetection.TaskArtifactState.WITHOUT_ACTIONS
import static org.gradle.api.internal.changedetection.TaskArtifactState.WITH_ACTIONS

class DefaultTaskArtifactStateRepositoryTest extends Specification {

    def startParameter = new StartParameter()
    def repository = new DefaultTaskArtifactStateRepository(startParameter)
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
        TaskArtifactState state = repository.getStateFor(task, taskProperties)

        then:
        state == WITHOUT_ACTIONS
        1 * upToDateSpec.isEmpty() >> true
        1 * taskProperties.hasDeclaredOutputs() >> false
        1 * task.hasTaskActions() >> false
    }

    def "no actions with outputs"() {
        when:
        TaskArtifactState state = repository.getStateFor(task, taskProperties)

        then:
        state == WITH_ACTIONS
        1 * upToDateSpec.isEmpty() >> true
        1 * taskProperties.hasDeclaredOutputs() >> false
        1 * task.hasTaskActions() >> true
    }

    def "default"() {
        when:
        TaskArtifactState state = repository.getStateFor(task, taskProperties)

        then:
        state == INCREMENTAL
        1 * taskProperties.hasDeclaredOutputs() >> true
        1 * upToDateSpec.isSatisfiedBy(task) >> true
    }

    def "--rerun-tasks enabled"() {
        when:
        startParameter.setRerunTasks(true)
        def state = repository.getStateFor(task, taskProperties)

        then:
        state == RERUN_TASKS_ENABLED
        1 * upToDateSpec.empty >> false
    }

    def "uoToDateSpec evaluates to false"() {
        when:
        def state = repository.getStateFor(task, taskProperties)

        then:
        state == UP_TO_DATE_WHEN_FALSE
        1 * taskProperties.hasDeclaredOutputs() >> true
        1 * upToDateSpec.isSatisfiedBy(task) >> false
    }
}
