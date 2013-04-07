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
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository
import spock.lang.Specification

public class ShortCircuitTaskArtifactStateRepositoryTest extends Specification {
    StartParameter startParameter = new StartParameter()
    TaskArtifactStateRepository delegate = Mock(TaskArtifactStateRepository)
    ShortCircuitTaskArtifactStateRepository repository = new ShortCircuitTaskArtifactStateRepository(startParameter, delegate)
    TaskArtifactState taskArtifactState = Mock(TaskArtifactState)

    def delegatesDirectToBackingRepositoryWithoutRerunTasks() {
        TaskInternal task = Mock(TaskInternal)

        when:
        TaskArtifactState state = repository.getStateFor(task);

        then:
        1 * delegate.getStateFor(task) >> taskArtifactState
        state == taskArtifactState
    }

    def taskArtifactsAreAlwaysOutOfDateWithRerunTasks() {
        TaskInternal task = Mock(TaskInternal)

        when:
        startParameter.setRerunTasks(true);
        def state = repository.getStateFor(task)

        then:
        1 * delegate.getStateFor(task) >> taskArtifactState
        0 * taskArtifactState._

        and:
        !state.upToDate

        and:
        state.inputChanges.allOutOfDate
    }
}
