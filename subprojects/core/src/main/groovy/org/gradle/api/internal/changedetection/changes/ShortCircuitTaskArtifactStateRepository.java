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
package org.gradle.api.internal.changedetection.changes;

import org.gradle.StartParameter;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.tasks.TaskInputChanges;

public class ShortCircuitTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private final StartParameter startParameter;
    private final TaskArtifactStateRepository repository;

    public ShortCircuitTaskArtifactStateRepository(StartParameter startParameter, TaskArtifactStateRepository repository) {
        this.startParameter = startParameter;
        this.repository = repository;
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        final TaskArtifactState state = repository.getStateFor(task);

        if (startParameter.isRerunTasks()) {
            return new RerunTaskArtifactState(state, task);
        }
        return state;
    }

    private static class RerunTaskArtifactState implements TaskArtifactState {
        private final TaskArtifactState delegate;
        private final TaskInternal task;

        private RerunTaskArtifactState(TaskArtifactState delegate, TaskInternal task) {
            this.delegate = delegate;
            this.task = task;
        }

        public boolean isUpToDate() {
            return false;
        }

        public TaskInputChanges getInputChanges() {
            return new RebuildTaskInputChanges(task);
        }

        public TaskExecutionHistory getExecutionHistory() {
            return delegate.getExecutionHistory();
        }

        public void beforeTask() {
            delegate.beforeTask();
        }

        public void afterTask() {
            delegate.afterTask();
        }

        public void finished() {
            delegate.finished();
        }
    }

}
