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
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository
import org.gradle.api.internal.tasks.execution.TaskProperties
import org.gradle.api.specs.AndSpec
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

class ShortCircuitTaskArtifactStateRepositoryTest extends Specification {

    def startParameter = new StartParameter()
    def delegate = Mock(TaskArtifactStateRepository)
    def repository = new ShortCircuitTaskArtifactStateRepository(startParameter, DirectInstantiator.INSTANCE, delegate)
    def taskArtifactState = Mock(TaskArtifactState)
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

    def doesNotLoadHistoryWhenTaskHasNoOutputs() {
        def messages = []

        when:
        TaskArtifactState state = repository.getStateFor(task, taskProperties)

        then:
        1 * upToDateSpec.isEmpty() >> true
        1 * taskProperties.hasDeclaredOutputs() >> false
        0 * taskArtifactState._

        and:
        state instanceof NoOutputsArtifactState
        !state.isUpToDate(messages)
        !messages.empty
    }

    def delegatesDirectToBackingRepositoryWithoutRerunTasks() {
        when:
        TaskArtifactState state = repository.getStateFor(task, taskProperties)

        then:
        1 * taskProperties.hasDeclaredOutputs() >> true
        1 * upToDateSpec.isSatisfiedBy(task) >> true

        and:
        1 * delegate.getStateFor(task, taskProperties) >> taskArtifactState
        state == taskArtifactState
    }

    def taskArtifactsAreAlwaysOutOfDateWithRerunTasks() {
        def messages = []

        when:
        startParameter.setRerunTasks(true)
        def state = repository.getStateFor(task, taskProperties)

        then:
        1 * upToDateSpec.empty >> false
        1 * delegate.getStateFor(task, taskProperties) >> taskArtifactState
        0 * taskArtifactState._

        and:
        !state.isUpToDate(messages)
        !messages.empty

        and:
        !state.getInputChanges().incremental
    }

    def taskArtifactsAreAlwaysOutOfDateWhenUpToDateSpecReturnsFalse() {
        def messages = []

        when:
        def state = repository.getStateFor(task, taskProperties)

        then:
        1 * taskProperties.hasDeclaredOutputs() >> true
        1 * upToDateSpec.isSatisfiedBy(task) >> false

        and:
        1 * delegate.getStateFor(task, taskProperties) >> taskArtifactState
        0 * taskArtifactState._

        and:
        !state.isUpToDate(messages)
        !messages.empty

        and:
        !state.getInputChanges().incremental
    }

}
