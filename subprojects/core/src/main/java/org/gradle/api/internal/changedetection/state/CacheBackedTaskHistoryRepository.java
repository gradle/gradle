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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {
    private final TaskArtifactStateCacheAccess cacheAccess;
    private final FileSnapshotRepository snapshotRepository;
    private final PersistentIndexedCache<String, TaskHistory> taskHistoryCache;
    private final TaskHistorySerializer serializer;
    private final StringInterner stringInterner;

    public CacheBackedTaskHistoryRepository(TaskArtifactStateCacheAccess cacheAccess, FileSnapshotRepository snapshotRepository, StringInterner stringInterner) {
        this.cacheAccess = cacheAccess;
        this.snapshotRepository = snapshotRepository;
        this.stringInterner = stringInterner;
        this.serializer = new TaskHistorySerializer(stringInterner);
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
                        if (currentExecution.inputFilesSnapshotIds == null && currentExecution.inputFilesSnapshot != null) {
                            ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
                            for (Map.Entry<String, FileCollectionSnapshot> entry : currentExecution.inputFilesSnapshot.entrySet()) {
                                builder.put(entry.getKey(), snapshotRepository.add(entry.getValue()));
                            }
                            currentExecution.inputFilesSnapshotIds = builder.build();
                        }
                        if (currentExecution.outputFilesSnapshotIds == null && currentExecution.outputFilesSnapshot != null) {
                            ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
                            for (Map.Entry<String, FileCollectionSnapshot> entry : currentExecution.outputFilesSnapshot.entrySet()) {
                                builder.put(entry.getKey(), snapshotRepository.add(entry.getValue()));
                            }
                            currentExecution.outputFilesSnapshotIds = builder.build();
                        }
                        if (currentExecution.discoveredFilesSnapshotId == null && currentExecution.discoveredFilesSnapshot != null) {
                            currentExecution.discoveredFilesSnapshotId = snapshotRepository.add(currentExecution.discoveredFilesSnapshot);
                        }
                        while (history.configurations.size() > TaskHistory.MAX_HISTORY_ENTRIES) {
                            LazyTaskExecution execution = history.configurations.remove(history.configurations.size() - 1);
                            if (execution.inputFilesSnapshotIds != null) {
                                for (Long id : execution.inputFilesSnapshotIds.values()) {
                                    snapshotRepository.remove(id);
                                }
                            }
                            if (execution.outputFilesSnapshotIds != null) {
                                for (Long id : execution.outputFilesSnapshotIds.values()) {
                                    snapshotRepository.remove(id);
                                }
                            }
                            if (execution.discoveredFilesSnapshotId != null) {
                                snapshotRepository.remove(execution.discoveredFilesSnapshotId);
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

    private Set<String> outputFiles(TaskInternal task) {
        Set<String> outputFiles = new HashSet<String>();
        for (File file : task.getOutputs().getFiles()) {
            outputFiles.add(stringInterner.intern(file.getAbsolutePath()));
        }
        return outputFiles;
    }

    private LazyTaskExecution findPreviousExecution(TaskExecution currentExecution, TaskHistory history) {
        Set<String> outputFiles = currentExecution.getOutputFiles();
        int outputFilesSize = outputFiles.size();
        LazyTaskExecution bestMatch = null;
        int bestMatchOverlap = 0;
        for (LazyTaskExecution previousExecution : history.configurations) {
            if (outputFilesSize == 0) {
                if (previousExecution.getOutputFiles().size() == 0) {
                    bestMatch = previousExecution;
                    break;
                }
            }

            Set<String> intersection = Sets.intersection(outputFiles, previousExecution.getOutputFiles());
            int overlap = intersection.size();
            if (overlap > bestMatchOverlap) {
                bestMatch = previousExecution;
                bestMatchOverlap = overlap;
            }
            if (bestMatchOverlap == outputFilesSize) {
                break;
            }
        }
        return bestMatch;
    }

    private static class TaskHistorySerializer implements Serializer<TaskHistory> {

        private ClassLoader classLoader;
        private final StringInterner stringInterner;

        public TaskHistorySerializer(StringInterner stringInterner) {
            this.stringInterner = stringInterner;
        }

        public TaskHistory read(Decoder decoder) throws Exception {
            byte executions = decoder.readByte();
            TaskHistory history = new TaskHistory();
            LazyTaskExecution.TaskHistorySerializer executionSerializer = new LazyTaskExecution.TaskHistorySerializer(classLoader, stringInterner);
            for (int i = 0; i < executions; i++) {
                LazyTaskExecution exec = executionSerializer.read(decoder);
                history.configurations.add(exec);
            }
            return history;
        }

        public void write(Encoder encoder, TaskHistory value) throws Exception {
            int size = value.configurations.size();
            encoder.writeByte((byte) size);
            LazyTaskExecution.TaskHistorySerializer executionSerializer = new LazyTaskExecution.TaskHistorySerializer(classLoader, stringInterner);
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
        private Map<String, Long> inputFilesSnapshotIds;
        private Map<String, Long> outputFilesSnapshotIds;
        private Long discoveredFilesSnapshotId;
        private transient FileSnapshotRepository snapshotRepository;
        private transient Map<String, FileCollectionSnapshot> inputFilesSnapshot;
        private transient Map<String, FileCollectionSnapshot> outputFilesSnapshot;
        private transient FileCollectionSnapshot discoveredFilesSnapshot;
        private transient TaskArtifactStateCacheAccess cacheAccess;

        @Override
        public Map<String, FileCollectionSnapshot> getInputFilesSnapshot() {
            if (inputFilesSnapshot == null) {
                inputFilesSnapshot = cacheAccess.useCache("fetch input files", new Factory<Map<String, FileCollectionSnapshot>>() {
                    public Map<String, FileCollectionSnapshot> create() {
                        ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
                        for (Map.Entry<String, Long> entry : inputFilesSnapshotIds.entrySet()) {
                            builder.put(entry.getKey(), snapshotRepository.get(entry.getValue()));
                        }
                        return builder.build();
                    }
                });
            }
            return inputFilesSnapshot;
        }

        @Override
        public void setInputFilesSnapshot(Map<String, FileCollectionSnapshot> inputFilesSnapshot) {
            this.inputFilesSnapshot = inputFilesSnapshot;
            this.inputFilesSnapshotIds = null;
        }

        @Override
        public FileCollectionSnapshot getDiscoveredInputFilesSnapshot() {
            if (discoveredFilesSnapshot == null) {
                discoveredFilesSnapshot = cacheAccess.useCache("fetch discovered input files", new Factory<FileCollectionSnapshot>() {
                    public FileCollectionSnapshot create() {
                        return snapshotRepository.get(discoveredFilesSnapshotId);
                    }
                });
            }
            return discoveredFilesSnapshot;
        }

        @Override
        public void setDiscoveredInputFilesSnapshot(FileCollectionSnapshot discoveredFilesSnapshot) {
            this.discoveredFilesSnapshot = discoveredFilesSnapshot;
            this.discoveredFilesSnapshotId = null;
        }

        @Override
        public Map<String, FileCollectionSnapshot> getOutputFilesSnapshot() {
            if (outputFilesSnapshot == null) {
                outputFilesSnapshot = cacheAccess.useCache("fetch output files", new Factory<Map<String, FileCollectionSnapshot>>() {
                    public Map<String, FileCollectionSnapshot> create() {
                        ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
                        for (Map.Entry<String, Long> entry : outputFilesSnapshotIds.entrySet()) {
                            String propertyName = entry.getKey();
                            builder.put(propertyName, snapshotRepository.get(entry.getValue()));
                        }
                        return builder.build();
                    }
                });
            }
            return outputFilesSnapshot;
        }

        @Override
        public void setOutputFilesSnapshot(Map<String, FileCollectionSnapshot> outputFilesSnapshot) {
            this.outputFilesSnapshot = outputFilesSnapshot;
            outputFilesSnapshotIds = null;
        }

        static class TaskHistorySerializer implements Serializer<LazyTaskExecution> {
            private final InputPropertiesSerializer inputPropertiesSerializer;
            private final StringInterner stringInterner;

            public TaskHistorySerializer(ClassLoader classLoader, StringInterner stringInterner) {
                this.inputPropertiesSerializer = new InputPropertiesSerializer(classLoader);
                this.stringInterner = stringInterner;
            }

            public LazyTaskExecution read(Decoder decoder) throws Exception {
                LazyTaskExecution execution = new LazyTaskExecution();
                execution.inputFilesSnapshotIds = readSnapshotIds(decoder);
                execution.outputFilesSnapshotIds = readSnapshotIds(decoder);
                execution.discoveredFilesSnapshotId = decoder.readLong();
                execution.setTaskClass(decoder.readString());
                if (decoder.readBoolean()) {
                    execution.setTaskClassLoaderHash(HashCode.fromBytes(decoder.readBinary()));
                }
                if (decoder.readBoolean()) {
                    execution.setTaskActionsClassLoaderHash(HashCode.fromBytes(decoder.readBinary()));
                }
                int outputFiles = decoder.readInt();
                Set<String> files = new HashSet<String>();
                for (int j = 0; j < outputFiles; j++) {
                    files.add(stringInterner.intern(decoder.readString()));
                }
                execution.setOutputFiles(files);

                boolean inputProperties = decoder.readBoolean();
                if (inputProperties) {
                    Map<String, Object> map = inputPropertiesSerializer.read(decoder);
                    execution.setInputProperties(map);
                } else {
                    execution.setInputProperties(new HashMap<String, Object>());
                }
                return execution;
            }

            public void write(Encoder encoder, LazyTaskExecution execution) throws Exception {
                writeSnapshotIds(encoder, execution.inputFilesSnapshotIds);
                writeSnapshotIds(encoder, execution.outputFilesSnapshotIds);
                encoder.writeLong(execution.discoveredFilesSnapshotId);
                encoder.writeString(execution.getTaskClass());
                HashCode classLoaderHash = execution.getTaskClassLoaderHash();
                if (classLoaderHash == null) {
                    encoder.writeBoolean(false);
                } else {
                    encoder.writeBoolean(true);
                    encoder.writeBinary(classLoaderHash.asBytes());
                }
                HashCode actionsClassLoaderHash = execution.getTaskActionsClassLoaderHash();
                if (actionsClassLoaderHash == null) {
                    encoder.writeBoolean(false);
                } else {
                    encoder.writeBoolean(true);
                    encoder.writeBinary(actionsClassLoaderHash.asBytes());
                }
                encoder.writeInt(execution.getOutputFiles().size());
                for (String outputFile : execution.getOutputFiles()) {
                    encoder.writeString(outputFile);
                }
                if (execution.getInputProperties() == null || execution.getInputProperties().isEmpty()) {
                    encoder.writeBoolean(false);
                } else {
                    encoder.writeBoolean(true);
                    inputPropertiesSerializer.write(encoder, execution.getInputProperties());
                }
            }

            private static Map<String, Long> readSnapshotIds(Decoder decoder) throws IOException {
                int count = decoder.readInt();
                ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
                for (int snapshotIdx = 0; snapshotIdx < count; snapshotIdx++) {
                    String property = decoder.readString();
                    long id = decoder.readLong();
                    builder.put(property, id);
                }
                return builder.build();
            }

            private static void writeSnapshotIds(Encoder encoder, Map<String, Long> ids) throws IOException {
                encoder.writeInt(ids.size());
                for (Map.Entry<String, Long> entry : ids.entrySet()) {
                    encoder.writeString(entry.getKey());
                    encoder.writeLong(entry.getValue());
                }
            }
        }
    }
}
