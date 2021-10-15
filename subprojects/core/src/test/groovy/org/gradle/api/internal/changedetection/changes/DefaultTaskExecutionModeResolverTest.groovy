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
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.TaskExecutionMode
import org.gradle.api.internal.project.taskfactory.IncrementalInputsTaskAction
import org.gradle.api.internal.tasks.properties.TaskProperties
import org.gradle.api.specs.AndSpec
import spock.lang.Specification

import static org.gradle.api.internal.changedetection.TaskExecutionMode.INCREMENTAL
import static org.gradle.api.internal.changedetection.TaskExecutionMode.NO_OUTPUTS
import static org.gradle.api.internal.changedetection.TaskExecutionMode.RERUN_TASKS_ENABLED
import static org.gradle.api.internal.changedetection.TaskExecutionMode.UNTRACKED
import static org.gradle.api.internal.changedetection.TaskExecutionMode.UP_TO_DATE_WHEN_FALSE

class DefaultTaskExecutionModeResolverTest extends Specification {

    def startParameter = new StartParameter()
    def repository = new DefaultTaskExecutionModeResolver(startParameter)
    def inputs = Stub(TaskInputsInternal)
    def outputs = Stub(TaskOutputsInternal)
    def taskProperties = Mock(TaskProperties)
    def task = Stub(TaskInternal)
    def upToDateSpec = Mock(AndSpec)

    def setup() {
        _ * task.getInputs() >> inputs
        _ * task.getOutputs() >> outputs
        _ * outputs.getUpToDateSpec() >> upToDateSpec
    }

    def "untracked"() {
        when:
        TaskExecutionMode state = repository.getExecutionMode(task, taskProperties)

        then:
        state == UNTRACKED
        _ * task.getDoNotTrackStateReason() >> Optional.of("For testing")
        0 * _
    }

    def "no outputs"() {
        when:
        TaskExecutionMode state = repository.getExecutionMode(task, taskProperties)

        then:
        state == NO_OUTPUTS
        1 * taskProperties.hasDeclaredOutputs() >> false
        1 * upToDateSpec.isEmpty() >> true
        _ * task.getTaskActions() >> []
        0 * _
    }

    def "default"() {
        when:
        TaskExecutionMode state = repository.getExecutionMode(task, taskProperties)

        then:
        state == INCREMENTAL
        1 * taskProperties.hasDeclaredOutputs() >> true
        1 * upToDateSpec.isSatisfiedBy(task) >> true
        0 * _
    }

    def "--rerun-tasks enabled"() {
        when:
        startParameter.setRerunTasks(true)
        def state = repository.getExecutionMode(task, taskProperties)

        then:
        state == RERUN_TASKS_ENABLED
        1 * taskProperties.hasDeclaredOutputs() >> false
        1 * upToDateSpec.empty >> false
        0 * _
    }

    def "upToDateSpec evaluates to false"() {
        when:
        def state = repository.getExecutionMode(task, taskProperties)

        then:
        state == UP_TO_DATE_WHEN_FALSE
        1 * taskProperties.hasDeclaredOutputs() >> true
        1 * upToDateSpec.isSatisfiedBy(task) >> false
        0 * _
    }

    def "fails when no outputs with incremental task action"() {
        when:
        repository.getExecutionMode(task, taskProperties)

        then:
        def ex = thrown InvalidUserCodeException
        ex.message == "You must declare outputs or use `TaskOutputs.upToDateWhen()` when using the incremental task API"

        1 * taskProperties.hasDeclaredOutputs() >> false
        1 * upToDateSpec.isEmpty() >> true
        _ * task.getTaskActions() >> [Mock(IncrementalInputsTaskAction)]
        0 * _
    }
}
