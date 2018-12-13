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
import org.gradle.util.TestUtil
import spock.lang.Specification

class ShortCircuitTaskArtifactStateRepositoryTest extends Specification {

    def startParameter = new StartParameter()
    def delegate = Mock(TaskArtifactStateRepository)
    def repository = new ShortCircuitTaskArtifactStateRepository(startParameter, TestUtil.instantiatorFactory().decorate(), delegate)
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
        when:
        TaskArtifactState state = repository.getStateFor(task, taskProperties)

        then:
        1 * upToDateSpec.isEmpty() >> true
        1 * taskProperties.hasDeclaredOutputs() >> false
        0 * taskArtifactState._

        and:
        state instanceof NoOutputsArtifactState
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
        when:
        startParameter.setRerunTasks(true)
        def state = repository.getStateFor(task, taskProperties)

        then:
        1 * upToDateSpec.empty >> false
        1 * delegate.getStateFor(task, taskProperties) >> taskArtifactState
        0 * taskArtifactState._
    }

    def taskArtifactsAreAlwaysOutOfDateWhenUpToDateSpecReturnsFalse() {
        when:
        def state = repository.getStateFor(task, taskProperties)

        then:
        1 * taskProperties.hasDeclaredOutputs() >> true
        1 * upToDateSpec.isSatisfiedBy(task) >> false

        and:
        1 * delegate.getStateFor(task, taskProperties) >> taskArtifactState
        0 * taskArtifactState._
    }

}
