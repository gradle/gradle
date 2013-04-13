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

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChanges;
import org.gradle.api.internal.changedetection.rules.TaskUpToDateState;
import org.gradle.api.internal.changedetection.rules.UpToDateChangeListener;
import org.gradle.api.internal.changedetection.state.FileSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {

    private static final Logger LOGGER = Logging.getLogger(DefaultTaskArtifactStateRepository.class);
    private final TaskHistoryRepository taskHistoryRepository;
    private final FileSnapshotter outputFilesSnapshotter;
    private final FileSnapshotter inputFilesSnapshotter;

    public DefaultTaskArtifactStateRepository(TaskHistoryRepository taskHistoryRepository, FileSnapshotter outputFilesSnapshotter, FileSnapshotter inputFilesSnapshotter) {
        this.taskHistoryRepository = taskHistoryRepository;
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

        public boolean isUpToDate() {
            final List<String> messages = getChangeMessages(getStates().getAllTaskChanges());
            if (messages.isEmpty()) {
                upToDate = true;
                LOGGER.info("Skipping {} as it is up-to-date.", task);
                return true;
            }
            logUpToDateMessages(messages, "Executing");
            return false;
        }

        public boolean canPerformIncrementalBuild() {
            final List<String> messages = getChangeMessages(getStates().getRebuildChanges());
            if (messages.isEmpty()) {
                LOGGER.info("Executing {} against out-of-date files only.", task);
                return true;
            }
            logUpToDateMessages(messages, "All files are considered out-of-date for");
            return false;
        }

        private List<String> getChangeMessages(TaskStateChanges stateChanges) {
            final List<String> messages = new ArrayList<String>();
            stateChanges.findChanges(new UpToDateChangeAction() {
                public void execute(TaskStateChange taskUpToDateStateChange) {
                    messages.add(taskUpToDateStateChange.getMessage());
                }
            });
            return messages;
        }

        private void logUpToDateMessages(List<String> messages, String action) {
            if (LOGGER.isInfoEnabled()) {
                Formatter formatter = new Formatter();
                formatter.format("%s %s due to:", action, task);
                for (String message : messages) {
                    formatter.format("%n  %s", message);
                }
                LOGGER.info(formatter.toString());
            }
        }

        public IncrementalTaskInputs getInputChanges() {
            assert !upToDate : "Should not be here if the task is up-to-date";

            if (canPerformIncrementalBuild()) {
                return new ChangesOnlyIncrementalTaskInputs(getStates().getInputFilesChanges());
            }
            return new RebuildIncrementalTaskInputs(task);
        }

        public boolean hasHistory() {
            return history.getPreviousExecution() != null;
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

        public void finished() {
        }

        private TaskUpToDateState getStates() {
            if (states == null) {
                // Calculate initial state - note this is potentially expensive
                states = new TaskUpToDateState(task, history, outputFilesSnapshotter, inputFilesSnapshotter);
            }
            return states;
        }
    }

    static class UpToDateChangeAction implements UpToDateChangeListener, Action<TaskStateChange> {
        public int executeCount;

        public void accept(TaskStateChange change) {
            executeCount++;
            execute(change);
        }

        public void execute(TaskStateChange taskStateChange) {
        }

        public boolean isAccepting() {
            return true;
        }
    }
}
