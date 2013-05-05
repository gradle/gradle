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
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

public class ShortCircuitTaskArtifactStateRepositoryTest extends Specification {
    StartParameter startParameter = new StartParameter()
    TaskArtifactStateRepository delegate = Mock(TaskArtifactStateRepository)
    ShortCircuitTaskArtifactStateRepository repository = new ShortCircuitTaskArtifactStateRepository(startParameter, new DirectInstantiator(), delegate)
    TaskArtifactState taskArtifactState = Mock(TaskArtifactState)
    TaskInternal task = Mock(TaskInternal)
    TaskOutputsInternal outputs = Mock(TaskOutputsInternal)

    def doesNotLoadHistoryWhenTaskHasNoDeclaredOutputs() {
        when:
        TaskArtifactState state = repository.getStateFor(task);

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.getHasOutput() >> false
        0 * _

        and:
        state instanceof NoHistoryArtifactState
        !state.upToDate
        !state.executionHistory.hasHistory()

    }

    def delegatesDirectToBackingRepositoryWithoutRerunTasks() {
        when:
        TaskArtifactState state = repository.getStateFor(task);

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.getHasOutput() >> true
        1 * delegate.getStateFor(task) >> taskArtifactState
        state == taskArtifactState
    }

    def taskArtifactsAreAlwaysOutOfDateWithRerunTasks() {
        when:
        startParameter.setRerunTasks(true);
        def state = repository.getStateFor(task)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.getHasOutput() >> true
        1 * delegate.getStateFor(task) >> taskArtifactState
        0 * taskArtifactState._

        and:
        !state.upToDate

        and:
        !state.inputChanges.incremental
    }
}
