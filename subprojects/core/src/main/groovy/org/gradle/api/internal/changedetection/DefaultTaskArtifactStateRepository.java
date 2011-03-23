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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.SimpleFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.CacheRepository;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import static java.util.Collections.singletonList;

public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private static final int MAX_OUT_OF_DATE_MESSAGES = 10;
    private static final Logger LOGGER = Logging.getLogger(DefaultTaskArtifactStateRepository.class);
    private final TaskHistoryRepository taskHistoryRepository;
    private final UpToDateRule upToDateRule;

    public DefaultTaskArtifactStateRepository(CacheRepository repository, FileSnapshotter inputFilesSnapshotter, FileSnapshotter outputFilesSnapshotter) {
        this.taskHistoryRepository = new CacheBackedTaskHistoryRepository(repository, new CacheBackedFileSnapshotRepository(repository));
        upToDateRule = new CompositeUpToDateRule(
                new TaskTypeChangedUpToDateRule(),
                new InputPropertiesChangedUpToDateRule(),
                new OutputFilesChangedUpToDateRule(outputFilesSnapshotter),
                new InputFilesChangedUpToDateRule(inputFilesSnapshotter));
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        return new TaskArtifactStateImpl(task, taskHistoryRepository.getHistory(task));
    }

    private interface TaskExecutionState {
        List<String> isUpToDate();

        boolean snapshot();

        FileCollection getPreviousOutputFiles();
    }

    private static class NoDeclaredArtifactsExecution implements TaskExecutionState {
        private final TaskInternal task;

        private NoDeclaredArtifactsExecution(TaskInternal task) {
            this.task = task;
        }

        public List<String> isUpToDate() {
            List<String> messages = new ArrayList<String>();
            if (!task.getOutputs().getHasOutput()) {
                messages.add(String.format("%s has not declared any outputs.", StringUtils.capitalize(task.toString())));
            }
            return messages;
        }

        public boolean snapshot() {
            return false;
        }

        public FileCollection getPreviousOutputFiles() {
            return new SimpleFileCollection();
        }
    }

    private static class HistoricExecution implements TaskExecutionState {
        private final TaskInternal task;
        private final TaskExecution lastExecution;
        private boolean upToDate;
        private final UpToDateRule rule;
        private TaskExecution thisExecution;
        private UpToDateRule.TaskUpToDateState upToDateState;

        public HistoricExecution(TaskInternal task, TaskHistoryRepository.History history, UpToDateRule rule) {
            this.task = task;
            this.lastExecution = history.getPreviousExecution();
            this.thisExecution = history.getCurrentExecution();
            this.rule = rule;
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

        public FileCollection getOutputFiles() {
            return execution.getPreviousOutputFiles();
        }

        public TaskExecutionHistory getExecutionHistory() {
            return this;
        }

        public TaskExecutionState getExecution() {
            if (!task.getOutputs().getHasOutput()) {
                return new NoDeclaredArtifactsExecution(task);
            }
            return new HistoricExecution(task, history, upToDateRule);
        }

        public void update() {
            if (execution.snapshot()) {
                history.update();
            }
        }
    }
}
