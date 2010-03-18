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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.util.ChangeListener;
import org.gradle.util.DiffUtil;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static java.util.Collections.*;

public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private static final int MAX_OUT_OF_DATE_MESSAGES = 20;
    private static final Logger LOGGER = Logging.getLogger(DefaultTaskArtifactStateRepository.class);
    private final CacheRepository repository;
    private final FileSnapshotter fileSnapshotter;
    private PersistentIndexedCache<String, TaskHistory> cache;

    public DefaultTaskArtifactStateRepository(CacheRepository repository, FileSnapshotter fileSnapshotter) {
        this.repository = repository;
        this.fileSnapshotter = fileSnapshotter;
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        if (cache == null) {
            loadTasks(task);
        }

        return new TaskArtifactStateImpl(task);
    }

    private void loadTasks(TaskInternal task) {
        cache = repository.cache("taskArtifacts").forObject(task.getProject().getGradle()).open().openIndexedCache();
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
    }

    private static class TaskHistory implements Serializable {
        private final List<TaskConfiguration> configurations = new ArrayList<TaskConfiguration>();

        public void addConfiguration(TaskConfiguration configuration) {
            configurations.add(0, configuration);
        }
    }

    private static class NoDeclaredArtifactsExecution implements TaskExecution {
        private final TaskInternal task;

        private NoDeclaredArtifactsExecution(TaskInternal task) {
            this.task = task;
        }

        public List<String> isUpToDate() {
            List<String> messages = new ArrayList<String>();
            if (!task.getInputs().getHasInputs()) {
                messages.add(String.format("%s has not declared any inputs.", StringUtils.capitalize(task.toString())));
            }
            if (!task.getOutputs().getHasOutputFiles()) {
                messages.add(String.format("%s has not declared any outputs.", StringUtils.capitalize(task.toString())));
            }
            assert !messages.isEmpty();
            return messages;
        }

        public boolean snapshot() {
            return false;
        }
    }

    private static class HistoricExecution implements TaskExecution {
        private final TaskHistory history;
        private final TaskInternal task;
        private final TaskConfiguration lastExecution;
        private final FileSnapshotter snapshotter;
        private boolean upToDate;
        private TaskConfiguration thisExecution;
        private FileCollectionSnapshot outputFilesBefore;

        public HistoricExecution(TaskHistory history, TaskInternal task, TaskConfiguration lastExecution, FileSnapshotter snapshotter) {
            this.history = history;
            this.task = task;
            this.lastExecution = lastExecution;
            this.snapshotter = snapshotter;
        }

        private void calcCurrentState() {
            if (thisExecution != null) {
                return;
            }

            // Calculate current state - note this is potentially expensive
            thisExecution = new TaskConfiguration(task, snapshotter);
            outputFilesBefore = snapshotter.snapshot(task.getOutputs().getCandidateFiles());
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
            checkOutputFiles(messages);
            if (!messages.isEmpty()) {
                return messages;
            }

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
                    // Don't care
                }

                public void removed(File element) {
                    messages.add(String.format("Output file %s for %s removed.", element.getAbsolutePath(), task));
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

        private void checkOutputFiles(final Collection<String> messages) {
            DiffUtil.diff(thisExecution.outputFiles, lastExecution.outputFiles, new ChangeListener<String>() {
                public void added(String element) {
                    messages.add(String.format("Output file '%s' has been added for %s", element, task));
                }

                public void removed(String element) {
                    messages.add(String.format("Output file '%s' has been removed for %s", element, task));
                }

                public void changed(String element) {
                    // should not happen
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

            FileCollectionSnapshot lastExecutionOutputFiles = lastExecution == null ? snapshotter.snapshot()
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
            FileCollectionSnapshot outputFilesAfter = snapshotter.snapshot(task.getOutputs().getCandidateFiles());
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

        private TaskConfiguration(TaskInternal task, FileSnapshotter fileSnapshotter) {
            this.taskClass = task.getClass().getName();
            this.outputFiles = outputFiles(task);
            this.inputProperties = new HashMap<String, Object>(task.getInputs().getProperties());
            this.inputFilesSnapshot = fileSnapshotter.snapshot(task.getInputs().getFiles());
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
                    formatter.format("%d more ...", messages.size() - MAX_OUT_OF_DATE_MESSAGES);
                }
                LOGGER.info(formatter.toString());
            }
            return false;
        }

        private TaskHistory getHistory() {
            TaskHistory history = cache.get(task.getPath());
            return history == null ? new TaskHistory() : history;
        }

        public TaskExecution getExecution() {
            if (!task.getInputs().getHasInputs() || !task.getOutputs().getHasOutputFiles()) {
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
                return new HistoricExecution(history, task, null, fileSnapshotter);
            }
            return new HistoricExecution(history, task, bestMatch, fileSnapshotter);
        }

        public void update() {
            if (execution.snapshot()) {
                cache.put(task.getPath(), history);
            }
        }
    }
}
