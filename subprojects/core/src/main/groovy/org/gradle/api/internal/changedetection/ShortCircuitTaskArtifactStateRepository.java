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
package org.gradle.api.internal.changedetection;

import org.apache.commons.lang.StringUtils;
import org.gradle.StartParameter;
import org.gradle.api.internal.execution.RebuildTaskInputChanges;
import org.gradle.api.execution.TaskInputChanges;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class ShortCircuitTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private static final Logger LOGGER = Logging.getLogger(ShortCircuitTaskArtifactStateRepository.class);
    private final StartParameter startParameter;
    private final TaskArtifactStateRepository repository;

    public ShortCircuitTaskArtifactStateRepository(StartParameter startParameter, TaskArtifactStateRepository repository) {
        this.startParameter = startParameter;
        this.repository = repository;
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        if (task.getOutputs().getHasOutput()) {
            return new ShortCircuitArtifactState(task, repository.getStateFor(task));
        }
        LOGGER.info(String.format("%s has not declared any outputs, assuming that it is out-of-date.", StringUtils.capitalize(task.toString())));
        return new NoHistoryArtifactState(task);
    }

    private static class NoHistoryArtifactState implements TaskArtifactState, TaskExecutionHistory {
        private final TaskInternal task;

        public NoHistoryArtifactState(TaskInternal task) {
            this.task = task;
        }

        public boolean isUpToDate() {
            return false;
        }

        public TaskInputChanges getExecutionContext() {
            return new RebuildTaskInputChanges(task);
        }

        public void beforeTask() {
        }

        public void afterTask() {
        }

        public void finished() {
        }

        public TaskExecutionHistory getExecutionHistory() {
            return this;
        }

        public FileCollection getOutputFiles() {
            throw new UnsupportedOperationException();
        }
    }

    private class ShortCircuitArtifactState implements TaskArtifactState {
        private final TaskInternal task;
        private final TaskArtifactState state;

        public ShortCircuitArtifactState(TaskInternal task, TaskArtifactState state) {
            this.task = task;
            this.state = state;
        }

        public boolean isUpToDate() {
            return !startParameter.isRerunTasks() && task.getOutputs().getUpToDateSpec().isSatisfiedBy(task) && state.isUpToDate();
        }

        public TaskInputChanges getExecutionContext() {
            // If we would normally re-run the task, then use a rebuild context
            // TODO: Don't want to re-execute the upToDateSpec
            if (startParameter.isRerunTasks() || !task.getOutputs().getUpToDateSpec().isSatisfiedBy(task)) {
                return new RebuildTaskInputChanges(task);
            }
            return state.getExecutionContext();
        }

        public TaskExecutionHistory getExecutionHistory() {
            return state.getExecutionHistory();
        }

        public void beforeTask() {
            state.beforeTask();
        }

        public void afterTask() {
            state.afterTask();
        }

        public void finished() {
            state.finished();
        }
    }
}
