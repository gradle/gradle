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

import org.gradle.api.execution.DefaultInputFileChange;
import org.gradle.api.execution.IncrementalTaskExecutionContext;
import org.gradle.api.execution.RebuildTaskExecutionContext;
import org.gradle.api.execution.TaskExecutionContext;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.ChangeListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import static java.util.Collections.singletonList;

public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private static final int MAX_OUT_OF_DATE_MESSAGES = 10;
    private static final Logger LOGGER = Logging.getLogger(DefaultTaskArtifactStateRepository.class);
    private final TaskHistoryRepository taskHistoryRepository;
    private final UpToDateRule upToDateRule;
    private final UpToDateRule incrementalUpToDateRule;
    private final FileSnapshotter inputFilesSnapshotter;

    public DefaultTaskArtifactStateRepository(TaskHistoryRepository taskHistoryRepository, FileSnapshotter inputFilesSnapshotter, FileSnapshotter outputFilesSnapshotter) {
        this.taskHistoryRepository = taskHistoryRepository;
        upToDateRule = new CompositeUpToDateRule(
                new TaskTypeChangedUpToDateRule(),
                new InputPropertiesChangedUpToDateRule(),
                new OutputFilesChangedUpToDateRule(outputFilesSnapshotter),
                new InputFilesChangedUpToDateRule(inputFilesSnapshotter));
        incrementalUpToDateRule = new CompositeUpToDateRule(
                new TaskTypeChangedUpToDateRule(),
                new InputPropertiesChangedUpToDateRule(),
                new OutputFilesChangedUpToDateRule(outputFilesSnapshotter));
        this.inputFilesSnapshotter = inputFilesSnapshotter;
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        return new TaskArtifactStateImpl(task, taskHistoryRepository.getHistory(task));
    }

    private interface TaskExecutionState {
        List<String> isUpToDate();

        boolean incrementalRequiresRebuild();

        List<TaskExecutionContext.InputFileChange> getInputFileChanges();

        boolean snapshot();

        FileCollection getPreviousOutputFiles();
    }

    private static class HistoricExecution implements TaskExecutionState {
        private final TaskInternal task;
        private final TaskExecution lastExecution;
        private boolean upToDate;
        private final UpToDateRule rule;
        private final UpToDateRule incrementalRule;
        private final FileSnapshotter inputFilesSnapshotter;
        private TaskExecution thisExecution;
        private UpToDateRule.TaskUpToDateState upToDateState;
        private UpToDateRule.TaskUpToDateState incrementalUpToDateState;

        public HistoricExecution(TaskInternal task, TaskHistoryRepository.History history, UpToDateRule rule, UpToDateRule incrementalRule, FileSnapshotter inputFilesSnapshotter) {
            this.task = task;
            this.inputFilesSnapshotter = inputFilesSnapshotter;
            this.lastExecution = history.getPreviousExecution();
            this.thisExecution = history.getCurrentExecution();
            this.rule = rule;
            this.incrementalRule = incrementalRule;
        }

        private void calcCurrentState() {
            if (upToDateState != null) {
                return;
            }

            // Calculate initial state - note this is potentially expensive
            upToDateState = rule.create(task, lastExecution, thisExecution);
        }

        public FileCollection getPreviousOutputFiles() {
            return lastExecution != null && lastExecution.getOutputFilesSnapshot() != null ? lastExecution.getOutputFilesSnapshot().getFiles() : new SimpleFileCollection();
        }

        public List<String> isUpToDate() {
            calcCurrentState();

            // Now determine if we're out of date
            if (lastExecution == null) {
                return singletonList(String.format("No history is available for %s.", task));
            }

            List<String> messages = new ArrayList<String>();
            upToDateState.checkUpToDate(messages);

            if (messages.isEmpty()) {
                upToDate = true;
            }
            return messages;
        }

        public boolean snapshot() {
            calcCurrentState();
            
            if (upToDate) {
                return false;
            }

            upToDateState.snapshotAfterTask();
            return true;
        }

        public boolean incrementalRequiresRebuild() {
            if (lastExecution == null) {
                return true;
            }

            incrementalUpToDateState = incrementalRule.create(task, lastExecution, thisExecution);
            List<String> messages = new ArrayList<String>();
            incrementalUpToDateState.checkUpToDate(messages);
            return !messages.isEmpty();
        }

        public List<TaskExecutionContext.InputFileChange> getInputFileChanges() {
            final FileCollectionSnapshot inputFilesSnapshot = inputFilesSnapshotter.snapshot(task.getInputs().getFiles());
            final List<TaskExecutionContext.InputFileChange> changes = new ArrayList<TaskExecutionContext.InputFileChange>();
            inputFilesSnapshot.changesSince(lastExecution.getInputFilesSnapshot(), new ChangeListener<File>() {
                public void added(File element) {
                    changes.add(new DefaultInputFileChange(element, DefaultInputFileChange.ChangeType.ADDED));
                }

                public void removed(File element) {
                    changes.add(new DefaultInputFileChange(element, DefaultInputFileChange.ChangeType.REMOVED));
                }

                public void changed(File element) {
                    changes.add(new DefaultInputFileChange(element, DefaultInputFileChange.ChangeType.MODIFIED));
                }
            });
            return changes;
        }
    }

    private class TaskArtifactStateImpl implements TaskArtifactState, TaskExecutionHistory {
        private final TaskInternal task;
        private final TaskHistoryRepository.History history;
        private final TaskExecutionState execution;

        public TaskArtifactStateImpl(TaskInternal task, TaskHistoryRepository.History history) {
            this.task = task;
            this.history = history;
            execution = getExecution();
        }

        public boolean isUpToDate() {
            List<String> messages = execution.isUpToDate();
            if (messages == null || messages.isEmpty()) {
                LOGGER.info("Skipping {} as it is up-to-date.", task);
                return true;
            }
            if (LOGGER.isInfoEnabled()) {
                Formatter formatter = new Formatter();
                formatter.format("Executing %s due to:", task);
                for (int i = 0; i < messages.size() && i < MAX_OUT_OF_DATE_MESSAGES; i++) {
                    String message = messages.get(i);
                    formatter.format("%n%s", message);
                }
                if (messages.size() > MAX_OUT_OF_DATE_MESSAGES) {
                    formatter.format("%n%d more ...", messages.size() - MAX_OUT_OF_DATE_MESSAGES);
                }
                LOGGER.info(formatter.toString());
            }
            return false;
        }

        public TaskExecutionContext getExecutionContext() {
            if (execution.incrementalRequiresRebuild()) {
                return new RebuildTaskExecutionContext(task);
            }
            return new IncrementalTaskExecutionContext(execution.getInputFileChanges());
        }

        public FileCollection getOutputFiles() {
            return execution.getPreviousOutputFiles();
        }

        public TaskExecutionHistory getExecutionHistory() {
            return this;
        }

        public TaskExecutionState getExecution() {
            return new HistoricExecution(task, history, upToDateRule, incrementalUpToDateRule, inputFilesSnapshotter);
        }

        public void afterTask() {
            if (execution.snapshot()) {
                history.update();
            }
        }

        public void beforeTask() {
        }

        public void finished() {
        }
    }
}
