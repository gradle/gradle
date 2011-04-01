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
package org.gradle.api.internal.changedetection

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.tasks.TaskInputs
import spock.lang.Specification

class FileCacheBroadcastTaskArtifactStateRepositoryTest extends Specification {
    final TaskArtifactStateRepository target = Mock()
    final TaskArtifactState targetState = Mock()
    final TaskInternal task = Mock()
    final TaskInputs taskInputs = Mock()
    final TaskOutputsInternal taskOutputs = Mock()
    final FileCollection outputs = Mock()
    final FileCollection inputs = Mock()
    final FileCacheListener listener = Mock()
    final FileCacheBroadcastTaskArtifactStateRepository repository = new FileCacheBroadcastTaskArtifactStateRepository(target, listener)

    def setup() {
        _ * task.inputs >> taskInputs
        _ * taskInputs.files >> inputs
        _ * task.outputs >> taskOutputs
        _ * taskOutputs.files >> outputs
    }
    
    def marksTaskInputsAndOutputsAsCacheableWhenCheckingUpToDate() {
        when:
        def state = repository.getStateFor(task)
        state.isUpToDate()

        then:
        1 * listener.cacheable(inputs)
        1 * listener.cacheable(outputs)
        1 * target.getStateFor(task) >> targetState
        1 * targetState.isUpToDate()
        0 * listener._
    }

    def invalidatesTaskOutputsWhenTaskIsToBeExecuted() {
        given:
        taskOutputs.hasOutput >> true

        when:
        def state = repository.getStateFor(task)
        state.beforeTask()

        then:
        1 * listener.invalidate(outputs)
        1 * target.getStateFor(task) >> targetState
        1 * targetState.beforeTask()
        0 * listener._
    }

    def invalidatesEverythingWhenTaskWhichDoesNotDeclareAnyOutputsIsToBeExecuted() {
        given:
        taskOutputs.hasOutput >> false
        
        when:
        def state = repository.getStateFor(task)
        state.beforeTask()

        then:
        1 * listener.invalidateAll()
        1 * target.getStateFor(task) >> targetState
        1 * targetState.beforeTask()
        0 * listener._
    }

    def marksTaskOutputsAsCacheableAfterTaskHasExecuted() {
        when:
        def state = repository.getStateFor(task)
        state.afterTask()

        then:
        1 * listener.cacheable(outputs)
        1 * target.getStateFor(task) >> targetState
        1 * targetState.afterTask()
        0 * listener._
    }

    def delegatesToBackingStateForOtherMethods() {
        when:
        def state = repository.getStateFor(task)
        state.finished()

        then:
        1 * target.getStateFor(task) >> targetState
        1 * targetState.finished()
        0 * listener._
    }
}
