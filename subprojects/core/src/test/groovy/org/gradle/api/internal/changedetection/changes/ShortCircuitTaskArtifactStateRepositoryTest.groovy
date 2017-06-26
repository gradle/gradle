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
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.id.UniqueId
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

class ShortCircuitTaskArtifactStateRepositoryTest extends Specification {

    def startParameter = new StartParameter()
    def delegate = Mock(TaskArtifactStateRepository)
    def repository = new ShortCircuitTaskArtifactStateRepository(startParameter, DirectInstantiator.INSTANCE, delegate)
    def taskArtifactState = Mock(TaskArtifactState)
    def inputs = Mock(TaskInputsInternal)
    def outputs = Mock(TaskOutputsInternal)
    def task = Mock(TaskInternal)
    def upToDateSpec = Mock(Spec)

    def setup() {
        _ * task.getInputs() >> inputs
        _ * task.getOutputs() >> outputs
    }

    def doesNotLoadHistoryWhenTaskHasNoDeclaredOutputs() {
        def messages = []

        when:
        TaskArtifactState state = repository.getStateFor(task)

        then:
        1 * outputs.getHasOutput() >> false
        0 * taskArtifactState._

        and:
        state instanceof NoHistoryArtifactState
        !state.isUpToDate(messages)
        !messages.empty
    }

    def delegatesDirectToBackingRepositoryWithoutRerunTasks() {
        when:
        TaskArtifactState state = repository.getStateFor(task)

        then:
        1 * outputs.getHasOutput() >> true
        1 * outputs.getUpToDateSpec() >> upToDateSpec
        1 * upToDateSpec.isSatisfiedBy(task) >> true

        and:
        1 * delegate.getStateFor(task) >> taskArtifactState
        state == taskArtifactState
    }

    def taskArtifactsAreAlwaysOutOfDateWithRerunTasks() {
        def messages = []

        when:
        startParameter.setRerunTasks(true)
        def state = repository.getStateFor(task)

        then:
        1 * outputs.getHasOutput() >> true
        1 * delegate.getStateFor(task) >> taskArtifactState
        0 * taskArtifactState._

        and:
        !state.isUpToDate(messages)
        !messages.empty

        and:
        !state.inputChanges.incremental
    }

    def taskArtifactsAreAlwaysOutOfDateWhenUpToDateSpecReturnsFalse() {
        def messages = []

        when:
        def state = repository.getStateFor(task)

        then:
        1 * outputs.getHasOutput() >> true
        1 * outputs.getUpToDateSpec() >> upToDateSpec
        1 * upToDateSpec.isSatisfiedBy(task) >> false

        and:
        1 * delegate.getStateFor(task) >> taskArtifactState
        0 * taskArtifactState._

        and:
        !state.isUpToDate(messages)
        !messages.empty

        and:
        !state.inputChanges.incremental
    }

    def "origin build ID is null task has no output"() {
        given:
        1 * outputs.getHasOutput() >> false

        when:
        def state = repository.getStateFor(task)

        then:
        state.originBuildInvocationId == null
    }

    def "origin build ID is null if forcing rerun"() {
        given:
        1 * outputs.getHasOutput() >> true
        1 * delegate.getStateFor(_) >> taskArtifactState
        taskArtifactState.getOriginBuildInvocationId() >> UniqueId.generate()
        startParameter.rerunTasks = true

        when:
        def state = repository.getStateFor(task)

        then:
        state.originBuildInvocationId == null
    }

    def "origin build ID is null up to date spec declares out of date"() {
        given:
        1 * outputs.getHasOutput() >> true
        1 * delegate.getStateFor(_) >> taskArtifactState
        taskArtifactState.getOriginBuildInvocationId() >> UniqueId.generate()
        1 * outputs.getUpToDateSpec() >> Specs.SATISFIES_NONE

        when:
        def state = repository.getStateFor(task)

        then:
        state.originBuildInvocationId == null
    }

    def "propagates origin build ID if reusing state"() {
        given:
        def id = UniqueId.generate()
        1 * outputs.getHasOutput() >> true
        1 * delegate.getStateFor(_) >> taskArtifactState
        1 * outputs.getUpToDateSpec() >> Specs.SATISFIES_ALL
        taskArtifactState.getOriginBuildInvocationId() >> id

        when:
        def state = repository.getStateFor(task)

        then:
        state.originBuildInvocationId == id
    }

}
