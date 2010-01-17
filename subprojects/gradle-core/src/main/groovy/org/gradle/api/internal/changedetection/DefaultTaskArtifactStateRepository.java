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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentIndexedCache;

import java.io.File;
import java.io.Serializable;
import java.util.*;

public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private static Logger logger = Logging.getLogger(DefaultTaskArtifactStateRepository.class);
    private static final byte FILE = 0;
    private static final byte DIR = 1;
    private static final byte MISSING = 3;
    private final CacheRepository repository;
    private final FileSnapshotter fileSnapshotter;
    private PersistentIndexedCache<File, OutputGenerators> cache;

    public DefaultTaskArtifactStateRepository(CacheRepository repository, FileSnapshotter fileSnapshotter) {
        this.repository = repository;
        this.fileSnapshotter = fileSnapshotter;
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        if (cache == null) {
            loadTasks(task);
        }

        final TaskKey key = new TaskKey(task);
        final TaskInfo thisExecution = getThisExecution(task);
        final TaskExecution lastExecution = getLastExecution(key, thisExecution);

        return new TaskArtifactState() {
            public boolean isUpToDate() {
                List<String> messages = thisExecution.isSameAs(lastExecution);
                if (messages == null || messages.isEmpty()) {
                    logger.info("Skipping {} as it is up-to-date.", task);
                    return true;
                }
                if (logger.isInfoEnabled()) {
                    Formatter formatter = new Formatter();
                    formatter.format("Executing %s due to:", task);
                    for (String message : messages) {
                        formatter.format("%n%s", message);
                    }
                    logger.info(formatter.toString());
                }
                return false;
            }

            public void invalidate() {
                for (File file : thisExecution.outputFiles.keySet()) {
                    OutputGenerators generators = cache.get(file);
                    generators.remove(key);
                    cache.put(file, generators);
                }
            }

            public void update() {
                thisExecution.snapshotOutputFiles();
                TaskExecution taskInfo = thisExecution;
                for (Map.Entry<File, OutputFileInfo> entry : thisExecution.outputFiles.entrySet()) {
                    OutputGenerators generators = cache.get(entry.getKey());
                    generators.update(entry.getValue(), key, taskInfo);
                    cache.put(entry.getKey(), generators);
                    taskInfo = new TaskInfoToken();
                }
            }
        };
    }

    private TaskInfo getThisExecution(TaskInternal task) {
        return new TaskInfo(task, fileSnapshotter);
    }

    private TaskExecution getLastExecution(TaskKey key, TaskInfo thisExecution) {
        TaskExecution taskInfo = null;
        List<String> outOfDateMessages = new ArrayList<String>();
        for (File outputFile : thisExecution.outputFiles.keySet()) {
            OutputGenerators generators = cache.get(outputFile);
            if (generators == null) {
                cache.put(outputFile, new OutputGenerators());
                outOfDateMessages.add(String.format("No history is available for %s.", outputFile));
                continue;
            }
            if (generators.invalidate(outputFile)) {
                cache.put(outputFile, generators);
            }
            TaskExecution lastExecution = generators.get(key);
            if (lastExecution == null) {
                outOfDateMessages.add(String.format("Task did not produce %s.", outputFile));
                continue;
            }
            if (lastExecution instanceof TaskInfo) {
                taskInfo = lastExecution;
            }
        }
        if (!outOfDateMessages.isEmpty()) {
            return new EmptyTaskInfo(outOfDateMessages);
        }
        if (taskInfo == null) {
            return new EmptyTaskInfo();
        }
        return taskInfo;
    }

    private void loadTasks(TaskInternal task) {
        cache = repository.getCacheFor(task.getProject().getGradle(), "taskArtifacts").openIndexedCache();
    }

    private static byte type(File file) {
        if (file.isFile()) {
            return FILE;
        } else if (file.isDirectory()) {
            return DIR;
        } else {
            return MISSING;
        }
    }

    private static class OutputGenerators implements Serializable {
        private final Map<TaskKey, TaskExecution> generators = new HashMap<TaskKey, TaskExecution>();
        private OutputFileInfo fileInfo;

        public TaskExecution remove(TaskKey task) {
            return generators.remove(task);
        }

        public void add(TaskKey key, TaskExecution info) {
            generators.put(key, info);
        }

        public void replace(TaskKey key, TaskExecution info) {
            generators.clear();
            generators.put(key, info);
        }

        public TaskExecution get(TaskKey key) {
            return generators.get(key);
        }

        public boolean invalidate(File outputFile) {
            if (fileInfo != null && !fileInfo.isUpToDate(outputFile)) {
                generators.clear();
                return true;
            }
            return false;
        }

        public void update(OutputFileInfo outputFile, TaskKey key, TaskExecution thisExecution) {
            fileInfo = outputFile;
            if (fileInfo.type == FILE) {
                replace(key, thisExecution);
            }
            else {
                add(key, thisExecution);
            }
        }
    }

    private static class TaskKey implements Serializable {
        private final String type;
        private final String path;

        private TaskKey(TaskInternal task) {
            path = task.getPath();
            type = task.getClass().getName();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            TaskKey other = (TaskKey) o;
            return other.type.equals(type) && other.path.equals(path);
        }

        @Override
        public int hashCode() {
            return type.hashCode() ^ path.hashCode();
        }
    }

    private interface TaskExecution {
    }

    private static class EmptyTaskInfo implements TaskExecution {
        private final List<String> outOfDateMessages;

        public EmptyTaskInfo() {
            outOfDateMessages = Arrays.asList("Task does not produce any output files");
        }

        public EmptyTaskInfo(List<String> outOfDateMessages) {
            this.outOfDateMessages = outOfDateMessages;
        }
    }

    private static class TaskInfoToken implements Serializable, TaskExecution {
    }

    private static class TaskInfo implements Serializable, TaskExecution {
        private final Map<File, OutputFileInfo> outputFiles = new HashMap<File, OutputFileInfo>();
        private boolean acceptInputs;
        private final FileCollectionSnapshot inputsSnapshot;
        private final Map<String, Object> inputProperties;
        private FileCollectionSnapshot outputsSnapshot;

        // Transient state
        private transient final FileCollectionSnapshot outputsBefore;
        private transient final TaskInternal task;
        private transient final FileSnapshotter fileSnapshotter;

        public TaskInfo(TaskInternal task, FileSnapshotter fileSnapshotter) {
            this.task = task;
            this.fileSnapshotter = fileSnapshotter;
            for (File file : task.getOutputs().getFiles()) {
                outputFiles.put(file, null);
            }
            acceptInputs = task.getInputs().getHasInputFiles();
            inputsSnapshot = fileSnapshotter.snapshot(task.getInputs().getFiles());
            outputsBefore = fileSnapshotter.snapshot(task.getOutputs().getCandidateFiles());
            inputProperties = task.getInputs().getProperties();
        }

        public void snapshotOutputFiles() {
            for (File file : outputFiles.keySet()) {
                outputFiles.put(file, new OutputFileInfo(file));
            }
            outputsSnapshot = fileSnapshotter.snapshot(task.getOutputs().getCandidateFiles());
        }

        public List<String> isSameAs(TaskExecution last) {
            if (last instanceof EmptyTaskInfo) {
                EmptyTaskInfo emptyTaskInfo = (EmptyTaskInfo) last;
                return emptyTaskInfo.outOfDateMessages;
            }
            
            if (!acceptInputs) {
                return Arrays.asList("Task does not accept any input files.");
            }

            TaskInfo lastExecution = (TaskInfo) last;

            final List<String> messages = new ArrayList<String>();
            outputsBefore.changesSince(lastExecution.outputsSnapshot, new FileCollectionSnapshot.ChangeListener() {
                public void added(File file) {
                    // Don't care about extra files
                }

                public void removed(File file) {
                    messages.add(String.format("Output file %s removed.", file));
                }

                public void changed(File file) {
                    messages.add(String.format("Output file %s changed.", file));
                }
            });
            if (!messages.isEmpty()) {
                return messages;
            }

            inputsSnapshot.changesSince(lastExecution.inputsSnapshot, new FileCollectionSnapshot.ChangeListener() {
                public void added(File file) {
                    messages.add(String.format("Input file %s added.", file));
                }

                public void removed(File file) {
                    messages.add(String.format("Input file %s removed.", file));
                }

                public void changed(File file) {
                    messages.add(String.format("Input file %s changed.", file));
                }
            });
            if (!messages.isEmpty()) {
                return messages;
            }

            if (!inputProperties.equals(lastExecution.inputProperties)) {
                messages.add("Input properties have changed.");
                return messages;
            }
            
            return null;
        }
    }

    private static class OutputFileInfo implements Serializable {
        private final byte type;
        private final boolean empty;

        private OutputFileInfo(File file) {
            type = type(file);
            empty = type == DIR && file.list().length == 0;
        }

        public boolean isUpToDate(File file) {
            if (type == MISSING) {
                // Was missing, don't care whether it exists or not
                return true;
            }

            byte newType = type(file);
            if (type == FILE) {
                // Was a file, must still be a file
                return newType == FILE;
            }
            if (newType != DIR) {
                // Was a dir, must still be a dir
                return false;
            }

            if (empty) {
                // Was empty, don't care if it is now not empty
                return true;
            }

            // Was not empty, must still be not empty
            return file.list().length != 0;
        }
    }
}
