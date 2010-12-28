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

package org.gradle.api.internal.changedetection;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.SimpleFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.util.ChangeListener;
import org.gradle.util.DiffUtil;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static java.util.Collections.*;

public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private static final int MAX_OUT_OF_DATE_MESSAGES = 10;
    private static final Logger LOGGER = Logging.getLogger(DefaultTaskArtifactStateRepository.class);
    private final CacheRepository repository;
    private final FileSnapshotter inputFilesSnapshotter;
    private final FileSnapshotter outputFilesSnapshotter;
    private PersistentIndexedCache<String, TaskHistory> taskHistoryCache;
    private DefaultSerializer<TaskHistory> serializer;

    public DefaultTaskArtifactStateRepository(CacheRepository repository, FileSnapshotter inputFilesSnapshotter, FileSnapshotter outputFilesSnapshotter) {
        this.repository = repository;
        this.inputFilesSnapshotter = inputFilesSnapshotter;
        this.outputFilesSnapshotter = outputFilesSnapshotter;
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

    private interface TaskExecution {
        List<String> isUpToDate();

        boolean snapshot();

        FileCollection getPreviousOutputFiles();
    }

    private static class TaskHistory implements Serializable {
        private static final int MAX_HISTORY_ENTRIES = 3;
        private final List<TaskConfiguration> configurations = new ArrayList<TaskConfiguration>();

        public void addConfiguration(TaskConfiguration configuration) {
            configurations.add(0, configuration);
            // Only keep a few of the most recent configurations
            while (configurations.size() > MAX_HISTORY_ENTRIES) {
                configurations.remove(MAX_HISTORY_ENTRIES);
            }
        }
    }

    private static class NoDeclaredArtifactsExecution implements TaskExecution {
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

    private static class HistoricExecution implements TaskExecution {
        private final TaskHistory history;
        private final TaskInternal task;
        private final TaskConfiguration lastExecution;
        private final FileSnapshotter inputFilesSnapshotter;
        private final FileSnapshotter outputFilesSnapshotter;
        private boolean upToDate;
        private TaskConfiguration thisExecution;
        private FileCollectionSnapshot outputFilesBefore;

        public HistoricExecution(TaskHistory history, TaskInternal task, TaskConfiguration lastExecution,
                                 FileSnapshotter inputFilesSnapshotter, FileSnapshotter outputFilesSnapshotter) {
            this.history = history;
            this.task = task;
            this.lastExecution = lastExecution;
            this.inputFilesSnapshotter = inputFilesSnapshotter;
            this.outputFilesSnapshotter = outputFilesSnapshotter;
        }

        private void calcCurrentState() {
            if (thisExecution != null) {
                return;
            }

            // Calculate current state - note this is potentially expensive
            FileCollectionSnapshot inputFilesSnapshot = inputFilesSnapshotter.snapshot(task.getInputs().getFiles());
            thisExecution = new TaskConfiguration(task, inputFilesSnapshot);
            outputFilesBefore = outputFilesSnapshotter.snapshot(task.getOutputs().getFiles());
        }

        public FileCollection getPreviousOutputFiles() {
            return lastExecution != null ? lastExecution.outputFilesSnapshot.getFiles() : new SimpleFileCollection();
        }

        public List<String> isUpToDate() {
            calcCurrentState();

            // Now determine if we're out of date
            if (lastExecution == null) {
                return singletonList(String.format("No history is available for %s.", task));
            }

            if (!task.getClass().getName().equals(lastExecution.taskClass)) {
                return singletonList(String.format("%s has changed type from '%s' to '%s'.", StringUtils.capitalize(
                        task.toString()), lastExecution.taskClass, task.getClass().getName()));
            }

            List<String> messages = new ArrayList<String>();
            checkInputProperties(messages);
            if (!messages.isEmpty()) {
                return messages;
            }

            checkInputs(messages);
            if (!messages.isEmpty()) {
                return messages;
            }

            checkOutputs(messages);
            if (!messages.isEmpty()) {
                return messages;
            }

            upToDate = true;
            return emptyList();
        }

        private void checkOutputs(final Collection<String> messages) {
            outputFilesBefore.changesSince(lastExecution.outputFilesSnapshot, new ChangeListener<File>() {
                public void added(File element) {
                    messages.add(String.format("Output file '%s' has been added for %s.", element, task));
                }

                public void removed(File element) {
                    messages.add(String.format("Output file %s has been removed for %s.", element.getAbsolutePath(), task));
                }

                public void changed(File element) {
                    messages.add(String.format("Output file %s for %s has changed.", element.getAbsolutePath(), task));
                }
            });
        }

        private void checkInputs(final Collection<String> messages) {
            thisExecution.inputFilesSnapshot.changesSince(lastExecution.inputFilesSnapshot, new ChangeListener<File>() {
                public void added(File file) {
                    messages.add(String.format("Input file %s for %s added.", file, task));
                }

                public void removed(File file) {
                    messages.add(String.format("Input file %s for %s removed.", file, task));
                }

                public void changed(File file) {
                    messages.add(String.format("Input file %s for %s has changed.", file, task));
                }
            });
        }

        private void checkInputProperties(final Collection<String> messages) {
            DiffUtil.diff(thisExecution.inputProperties, lastExecution.inputProperties, new ChangeListener<Map.Entry<String, Object>>() {
                public void added(Map.Entry<String, Object> element) {
                    messages.add(String.format("Input property '%s' has been added for %s", element.getKey(), task));
                }

                public void removed(Map.Entry<String, Object> element) {
                    messages.add(String.format("Input property '%s' has been removed for %s", element.getKey(), task));
                }

                public void changed(Map.Entry<String, Object> element) {
                    messages.add(String.format("Value of input property '%s' has changed for %s", element.getKey(), task));
                }
            });
        }

        public boolean snapshot() {
            calcCurrentState();
            
            if (upToDate) {
                return false;
            }

            FileCollectionSnapshot lastExecutionOutputFiles = lastExecution == null ? outputFilesSnapshotter.snapshot()
                    : lastExecution.outputFilesSnapshot;
            FileCollectionSnapshot newOutputFiles = outputFilesBefore.changesSince(lastExecutionOutputFiles).applyTo(
                    lastExecutionOutputFiles, new ChangeListener<FileCollectionSnapshot.Merge>() {
                        public void added(FileCollectionSnapshot.Merge element) {
                            // Ignore added files
                            element.ignore();
                        }

                        public void removed(FileCollectionSnapshot.Merge element) {
                            // Discard any files removed since the task was last executed
                        }

                        public void changed(FileCollectionSnapshot.Merge element) {
                            // Update any files which were change since the task was last executed
                        }
                    });
            FileCollectionSnapshot outputFilesAfter = outputFilesSnapshotter.snapshot(task.getOutputs().getFiles());
            thisExecution.outputFilesSnapshot = outputFilesAfter.changesSince(outputFilesBefore).applyTo(
                    newOutputFiles);
            history.addConfiguration(thisExecution);
            return true;
        }
    }

    private static class TaskConfiguration implements Serializable {
        private final String taskClass;
        private Set<String> outputFiles;
        private Map<String, Object> inputProperties;
        private FileCollectionSnapshot inputFilesSnapshot;
        private FileCollectionSnapshot outputFilesSnapshot;

        private TaskConfiguration(TaskInternal task, FileCollectionSnapshot inputFilesSnapshot) {
            this.taskClass = task.getClass().getName();
            this.outputFiles = outputFiles(task);
            this.inputProperties = new HashMap<String, Object>(task.getInputs().getProperties());
            this.inputFilesSnapshot = inputFilesSnapshot;
        }
    }

    private class TaskArtifactStateImpl implements TaskArtifactState {
        private final TaskInternal task;
        private final TaskHistory history;
        private final TaskExecution execution;

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

        public TaskExecution getExecution() {
            if (!task.getOutputs().getHasOutput()) {
                return new NoDeclaredArtifactsExecution(task);
            }
            Set<String> outputFiles = outputFiles(task);
            TaskConfiguration bestMatch = null;
            int bestMatchOverlap = 0;
            for (TaskConfiguration configuration : history.configurations) {
                if (outputFiles.size() == 0) {
                    if (configuration.outputFiles.size() == 0) {
                        bestMatch = configuration;
                        break;
                    }
                }

                Set<String> intersection = new HashSet<String>(outputFiles);
                intersection.retainAll(configuration.outputFiles);
                if (intersection.size() > bestMatchOverlap) {
                    bestMatch = configuration;
                    bestMatchOverlap = intersection.size();
                }
                if (bestMatchOverlap == outputFiles.size()) {
                    break;
                }
            }
            if (bestMatch == null) {
                return new HistoricExecution(history, task, null, inputFilesSnapshotter, outputFilesSnapshotter);
            }
            return new HistoricExecution(history, task, bestMatch, inputFilesSnapshotter, outputFilesSnapshotter);
        }

        public void update() {
            if (execution.snapshot()) {
                taskHistoryCache.put(task.getPath(), history);
            }
        }
    }
}
