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
package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.TaskInternal;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;

import java.io.File;
import java.util.*;

public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {
    private final TaskArtifactStateCacheAccess cacheAccess;
    private final FileSnapshotRepository snapshotRepository;
    private final PersistentIndexedCache<String, TaskHistory> taskHistoryCache;
    private final TaskHistorySerializer serializer = new TaskHistorySerializer();

    public CacheBackedTaskHistoryRepository(TaskArtifactStateCacheAccess cacheAccess, FileSnapshotRepository snapshotRepository) {
        this.cacheAccess = cacheAccess;
        this.snapshotRepository = snapshotRepository;
        taskHistoryCache = cacheAccess.createCache("taskArtifacts", String.class, serializer);
    }

    public History getHistory(final TaskInternal task) {
        final TaskHistory history = loadHistory(task);
        final LazyTaskExecution currentExecution = new LazyTaskExecution();
        currentExecution.snapshotRepository = snapshotRepository;
        currentExecution.cacheAccess = cacheAccess;
        currentExecution.setOutputFiles(outputFiles(task));
        final LazyTaskExecution previousExecution = findPreviousExecution(currentExecution, history);
        if (previousExecution != null) {
            previousExecution.snapshotRepository = snapshotRepository;
            previousExecution.cacheAccess = cacheAccess;
        }

        return new History() {
            public TaskExecution getPreviousExecution() {
                return previousExecution;
            }

            public TaskExecution getCurrentExecution() {
                return currentExecution;
            }

            public void update() {
                cacheAccess.useCache("Update task history", new Runnable() {
                    public void run() {
                        history.configurations.add(0, currentExecution);
                        if (currentExecution.inputFilesSnapshotId == null && currentExecution.inputFilesSnapshot != null) {
                            currentExecution.inputFilesSnapshotId = snapshotRepository.add(currentExecution.inputFilesSnapshot);
                        }
                        if (currentExecution.outputFilesSnapshotId == null && currentExecution.outputFilesSnapshot != null) {
                            currentExecution.outputFilesSnapshotId = snapshotRepository.add(currentExecution.outputFilesSnapshot);
                        }
                        while (history.configurations.size() > TaskHistory.MAX_HISTORY_ENTRIES) {
                            LazyTaskExecution execution = history.configurations.remove(history.configurations.size() - 1);
                            if (execution.inputFilesSnapshotId != null) {
                                snapshotRepository.remove(execution.inputFilesSnapshotId);
                            }
                            if (execution.outputFilesSnapshotId != null) {
                                snapshotRepository.remove(execution.outputFilesSnapshotId);
                            }
                        }
                        history.beforeSerialized();
                        taskHistoryCache.put(task.getPath(), history);
                    }
                });
            }
        };
    }

    private TaskHistory loadHistory(final TaskInternal task) {
        return cacheAccess.useCache("Load task history", new Factory<TaskHistory>() {
            public TaskHistory create() {
                ClassLoader original = serializer.getClassLoader();
                serializer.setClassLoader(task.getClass().getClassLoader());
                try {
                    TaskHistory history = taskHistoryCache.get(task.getPath());
                    return history == null ? new TaskHistory() : history;
                } finally {
                    serializer.setClassLoader(original);
                }
            }
        });
    }

    private static Set<String> outputFiles(TaskInternal task) {
        Set<String> outputFiles = new HashSet<String>();
        for (File file : task.getOutputs().getFiles()) {
            outputFiles.add(file.getAbsolutePath());
        }
        return outputFiles;
    }

    private LazyTaskExecution findPreviousExecution(TaskExecution currentExecution, TaskHistory history) {
        Set<String> outputFiles = currentExecution.getOutputFiles();
        LazyTaskExecution bestMatch = null;
        int bestMatchOverlap = 0;
        for (LazyTaskExecution configuration : history.configurations) {
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
        return bestMatch;
    }

    private static class TaskHistorySerializer implements Serializer<TaskHistory> {

        private ClassLoader classLoader;

        public TaskHistory read(Decoder decoder) throws Exception {
            byte executions = decoder.readByte();
            TaskHistory history = new TaskHistory();
            LazyTaskExecution.TaskHistorySerializer executionSerializer = new LazyTaskExecution.TaskHistorySerializer(classLoader);
            for (int i = 0; i < executions; i++) {
                LazyTaskExecution exec = executionSerializer.read(decoder);
                history.configurations.add(exec);
            }
            return history;
        }

        public void write(Encoder encoder, TaskHistory value) throws Exception {
            int size = value.configurations.size();
            encoder.writeByte((byte) size);
            LazyTaskExecution.TaskHistorySerializer executionSerializer = new LazyTaskExecution.TaskHistorySerializer(classLoader);
            for (LazyTaskExecution execution : value.configurations) {
                executionSerializer.write(encoder, execution);
            }
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public void setClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }
    }

    private static class TaskHistory {
        private static final int MAX_HISTORY_ENTRIES = 3;
        private final List<LazyTaskExecution> configurations = new ArrayList<LazyTaskExecution>();
        public String toString() {
            return super.toString() + "[" + configurations.size() + "]";
        }

        public void beforeSerialized() {
            //cleaning up the transient fields, so that any in-memory caching is happy
            for (LazyTaskExecution c : configurations) {
                c.cacheAccess = null;
                c.snapshotRepository = null;
            }
        }
    }

    //TODO SF extract & unit test
    private static class LazyTaskExecution extends TaskExecution {
        private Long inputFilesSnapshotId;
        private Long outputFilesSnapshotId;
        private transient FileSnapshotRepository snapshotRepository;
        private transient FileCollectionSnapshot inputFilesSnapshot;
        private transient FileCollectionSnapshot outputFilesSnapshot;
        private transient TaskArtifactStateCacheAccess cacheAccess;

        @Override
        public FileCollectionSnapshot getInputFilesSnapshot() {
            if (inputFilesSnapshot == null) {
                inputFilesSnapshot = cacheAccess.useCache("fetch input files", new Factory<FileCollectionSnapshot>() {
                    public FileCollectionSnapshot create() {
                        return snapshotRepository.get(inputFilesSnapshotId);
                    }
                });
            }
            return inputFilesSnapshot;
        }

        @Override
        public void setInputFilesSnapshot(FileCollectionSnapshot inputFilesSnapshot) {
            this.inputFilesSnapshot = inputFilesSnapshot;
            this.inputFilesSnapshotId = null;
        }

        @Override
        public FileCollectionSnapshot getOutputFilesSnapshot() {
            if (outputFilesSnapshot == null) {
                outputFilesSnapshot = cacheAccess.useCache("fetch output files", new Factory<FileCollectionSnapshot>() {
                    public FileCollectionSnapshot create() {
                        return snapshotRepository.get(outputFilesSnapshotId);
                    }
                });
            }
            return outputFilesSnapshot;
        }

        @Override
        public void setOutputFilesSnapshot(FileCollectionSnapshot outputFilesSnapshot) {
            this.outputFilesSnapshot = outputFilesSnapshot;
            outputFilesSnapshotId = null;
        }

        static class TaskHistorySerializer implements Serializer<LazyTaskExecution> {
            private ClassLoader classLoader;

            public TaskHistorySerializer(ClassLoader classLoader) {
                this.classLoader = classLoader;
            }

            public LazyTaskExecution read(Decoder decoder) throws Exception {
                LazyTaskExecution execution = new LazyTaskExecution();
                execution.inputFilesSnapshotId = decoder.readLong();
                execution.outputFilesSnapshotId = decoder.readLong();
                execution.setTaskClass(decoder.readString());
                int outputFiles = decoder.readInt();
                Set<String> files = new HashSet<String>();
                for (int j = 0; j < outputFiles; j++) {
                    files.add(decoder.readString());
                }
                execution.setOutputFiles(files);

                boolean inputProperties = decoder.readBoolean();
                if (inputProperties) {
                    DefaultSerializer<Map> defaultSerializer = new DefaultSerializer<Map>(classLoader);
                    Map<String, Object> map = defaultSerializer.read(decoder);
                    execution.setInputProperties(map);
                } else {
                    execution.setInputProperties(new HashMap<String, Object>());
                }
                return execution;
            }

            public void write(Encoder encoder, LazyTaskExecution execution) throws Exception {
                encoder.writeLong(execution.inputFilesSnapshotId);
                encoder.writeLong(execution.outputFilesSnapshotId);
                encoder.writeString(execution.getTaskClass());
                encoder.writeInt(execution.getOutputFiles().size());
                for (String outputFile : execution.getOutputFiles()) {
                    encoder.writeString(outputFile);
                }
                if (execution.getInputProperties() == null || execution.getInputProperties().isEmpty()) {
                    encoder.writeBoolean(false);
                } else {
                    encoder.writeBoolean(true);
                    DefaultSerializer<Map> defaultSerializer = new DefaultSerializer<Map>(classLoader);
                    defaultSerializer.write(encoder, execution.getInputProperties());
                }
            }
        }
    }
}
