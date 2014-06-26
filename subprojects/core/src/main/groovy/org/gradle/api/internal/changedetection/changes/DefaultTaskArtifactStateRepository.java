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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChanges;
import org.gradle.api.internal.changedetection.rules.TaskUpToDateState;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.reflect.Instantiator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {

    private final TaskHistoryRepository taskHistoryRepository;
    private final FileCollectionSnapshotter outputFilesSnapshotter;
    private final FileCollectionSnapshotter inputFilesSnapshotter;
    private final Instantiator instantiator;

    public DefaultTaskArtifactStateRepository(TaskHistoryRepository taskHistoryRepository, Instantiator instantiator,
                                              FileCollectionSnapshotter outputFilesSnapshotter, FileCollectionSnapshotter inputFilesSnapshotter) {
        this.taskHistoryRepository = taskHistoryRepository;
        this.instantiator = instantiator;
        this.outputFilesSnapshotter = outputFilesSnapshotter;
        this.inputFilesSnapshotter = inputFilesSnapshotter;
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        return new TaskArtifactStateImpl(task, taskHistoryRepository.getHistory(task));
    }

    private class TaskArtifactStateImpl implements TaskArtifactState, TaskExecutionHistory {
        private final TaskInternal task;
        private final TaskHistoryRepository.History history;
        private boolean upToDate;
        private TaskUpToDateState states;

        public TaskArtifactStateImpl(TaskInternal task, TaskHistoryRepository.History history) {
            this.task = task;
            this.history = history;
        }

        public boolean isUpToDate(Collection<String> messages) {
            final List<String> reasons = getChangeMessages(getStates().getAllTaskChanges());
            messages.addAll(reasons);
            if (reasons.isEmpty()) {
                upToDate = true;
                return true;
            }
            return false;
        }

        private List<String> getChangeMessages(TaskStateChanges stateChanges) {
            final List<String> messages = new ArrayList<String>();
            for (TaskStateChange stateChange : stateChanges) {
                messages.add(stateChange.getMessage());
            }
            return messages;
        }

        public IncrementalTaskInputs getInputChanges() {
            assert !upToDate : "Should not be here if the task is up-to-date";

            if (canPerformIncrementalBuild()) {
                return instantiator.newInstance(ChangesOnlyIncrementalTaskInputs.class, getStates().getInputFilesChanges(), getStates().getInputFilesSnapshot());
            }
            return instantiator.newInstance(RebuildIncrementalTaskInputs.class, task, getStates().getInputFilesSnapshot());
        }

        private boolean canPerformIncrementalBuild() {
            final List<String> messages = getChangeMessages(getStates().getRebuildChanges());
            return messages.isEmpty();
        }

        public FileCollection getOutputFiles() {
            TaskExecution lastExecution = history.getPreviousExecution();
            return lastExecution != null && lastExecution.getOutputFilesSnapshot() != null ? lastExecution.getOutputFilesSnapshot().getFiles() : new SimpleFileCollection();
        }

        public TaskExecutionHistory getExecutionHistory() {
            return this;
        }

        public void beforeTask() {
        }

        public void afterTask() {
            if (upToDate) {
                return;
            }

            getStates().getAllTaskChanges().snapshotAfterTask();
            history.update();
        }

        public void finished() {}

        private TaskUpToDateState getStates() {
            if (states == null) {
                // Calculate initial state - note this is potentially expensive
                states = new TaskUpToDateState(task, history, outputFilesSnapshotter, inputFilesSnapshotter);
            }
            return states;
        }
    }

}
