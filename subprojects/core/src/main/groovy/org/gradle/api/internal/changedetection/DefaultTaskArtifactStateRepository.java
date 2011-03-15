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
import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentIndexedCache;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static java.util.Collections.singletonList;

public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private static final int MAX_OUT_OF_DATE_MESSAGES = 10;
    private static final Logger LOGGER = Logging.getLogger(DefaultTaskArtifactStateRepository.class);
    private final CacheRepository repository;
    private PersistentIndexedCache<String, TaskHistory> taskHistoryCache;
    private DefaultSerializer<TaskHistory> serializer;
    private final UpToDateRule upToDateRule;

    public DefaultTaskArtifactStateRepository(CacheRepository repository, FileSnapshotter inputFilesSnapshotter, FileSnapshotter outputFilesSnapshotter) {
        this.repository = repository;
        upToDateRule = new CompositeUpToDateRule(
                new TaskTypeChangedUpToDateRule(),
                new InputPropertiesChangedUpToDateRule(),
                new OutputFilesChangedUpToDateRule(outputFilesSnapshotter),
                new InputFilesChangedUpToDateRule(inputFilesSnapshotter));
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        if (taskHistoryCache == null) {
            loadTasks(task);
        }

        return new TaskArtifactStateImpl(task);
    }

    private void loadTasks(TaskInternal task) {
        serializer = new DefaultSerializer<TaskHistory>();
        taskHistoryCache = repository.cache("taskArtifacts").forObject(task.getProject().getGradle()).open().openIndexedCache(serializer);
    }

    private static Set<String> outputFiles(TaskInternal task) {
        Set<String> outputFiles = new HashSet<String>();
        for (File file : task.getOutputs().getFiles()) {
            outputFiles.add(file.getAbsolutePath());
        }
        return outputFiles;
    }

    private interface TaskExecutionState {
        List<String> isUpToDate();

        boolean snapshot();

        FileCollection getPreviousOutputFiles();
    }

    private static class TaskHistory implements Serializable {
        private static final int MAX_HISTORY_ENTRIES = 3;
        private final List<TaskExecution> configurations = new ArrayList<TaskExecution>();

        public void addConfiguration(TaskExecution configuration) {
            configurations.add(0, configuration);
            // Only keep a few of the most recent configurations
            while (configurations.size() > MAX_HISTORY_ENTRIES) {
                configurations.remove(MAX_HISTORY_ENTRIES);
            }
        }
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
        private final TaskHistory history;
        private final TaskInternal task;
        private final TaskExecution lastExecution;
        private boolean upToDate;
        private final UpToDateRule rule;
        private TaskExecution thisExecution;
        private UpToDateRule.TaskUpToDateState upToDateState;

        public HistoricExecution(TaskHistory history, TaskInternal task, TaskExecution lastExecution, UpToDateRule rule) {
            this.history = history;
            this.task = task;
            this.lastExecution = lastExecution;
            this.rule = rule;
        }

        private void calcCurrentState() {
            if (thisExecution != null) {
                return;
            }

            // Calculate current state - note this is potentially expensive
            thisExecution = new TaskExecution();
            thisExecution.setOutputFiles(outputFiles(task));
            upToDateState = rule.create(task, lastExecution, thisExecution);
            upToDateState.snapshotBeforeTask();
        }

        public FileCollection getPreviousOutputFiles() {
            return lastExecution != null ? lastExecution.getOutputFilesSnapshot().getFiles() : new SimpleFileCollection();
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
            history.addConfiguration(thisExecution);
            return true;
        }
    }

    private class TaskArtifactStateImpl implements TaskArtifactState, TaskExecutionHistory {
        private final TaskInternal task;
        private final TaskHistory history;
        private final TaskExecutionState execution;

        public TaskArtifactStateImpl(TaskInternal task) {
            this.task = task;
            history = getHistory();
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

        private TaskHistory getHistory() {
            ClassLoader original = serializer.getClassLoader();
            serializer.setClassLoader(task.getClass().getClassLoader());
            try {
                TaskHistory history = taskHistoryCache.get(task.getPath());
                return history == null ? new TaskHistory() : history;
            } finally {
                serializer.setClassLoader(original);
            }
        }

        public TaskExecutionState getExecution() {
            if (!task.getOutputs().getHasOutput()) {
                return new NoDeclaredArtifactsExecution(task);
            }
            Set<String> outputFiles = outputFiles(task);
            TaskExecution bestMatch = null;
            int bestMatchOverlap = 0;
            for (TaskExecution configuration : history.configurations) {
                if (outputFiles.size() == 0) {
                    if (configuration.getOutputFiles().size() == 0) {
                        bestMatch = configuration;
                        break;
                    }
                }

                Set<String> intersection = new HashSet<String>(outputFiles);
                intersection.retainAll(configuration.getOutputFiles());
                if (intersection.size() > bestMatchOverlap) {
                    bestMatch = configuration;
                    bestMatchOverlap = intersection.size();
                }
                if (bestMatchOverlap == outputFiles.size()) {
                    break;
                }
            }
            if (bestMatch == null) {
                return new HistoricExecution(history, task, null, upToDateRule);
            }
            return new HistoricExecution(history, task, bestMatch, upToDateRule);
        }

        public void update() {
            if (execution.snapshot()) {
                taskHistoryCache.put(task.getPath(), history);
            }
        }
    }
}
