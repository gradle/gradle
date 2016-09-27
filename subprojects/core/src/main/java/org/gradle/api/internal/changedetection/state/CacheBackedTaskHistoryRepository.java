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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {
    private static final int MAX_HISTORY_ENTRIES = 3;

    private final TaskArtifactStateCacheAccess cacheAccess;
    private final FileSnapshotRepository snapshotRepository;
    private final PersistentIndexedCache<String, TaskExecutionList> taskHistoryCache;
    private final TaskExecutionListSerializer serializer;
    private final StringInterner stringInterner;

    public CacheBackedTaskHistoryRepository(TaskArtifactStateCacheAccess cacheAccess, FileSnapshotRepository snapshotRepository, StringInterner stringInterner) {
        this.cacheAccess = cacheAccess;
        this.snapshotRepository = snapshotRepository;
        this.stringInterner = stringInterner;
        this.serializer = new TaskExecutionListSerializer(stringInterner);
        taskHistoryCache = cacheAccess.createCache("taskArtifacts", String.class, serializer);
    }

    public History getHistory(final TaskInternal task) {
        final TaskExecutionList previousExecutions = loadPreviousExecutions(task);
        final LazyTaskExecution currentExecution = new LazyTaskExecution();
        currentExecution.snapshotRepository = snapshotRepository;
        currentExecution.cacheAccess = cacheAccess;
        currentExecution.setDeclaredOutputFilePaths(getDeclaredOutputFilePaths(task));
        final LazyTaskExecution previousExecution = findBestMatchingPreviousExecution(currentExecution, previousExecutions.executions);
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
                        previousExecutions.executions.addFirst(currentExecution);
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
                        while (previousExecutions.executions.size() > MAX_HISTORY_ENTRIES) {
                            LazyTaskExecution execution = previousExecutions.executions.removeLast();
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
                        previousExecutions.beforeSerialized();
                        taskHistoryCache.put(task.getPath(), previousExecutions);
                    }
                });
            }
        };
    }

    private TaskExecutionList loadPreviousExecutions(final TaskInternal task) {
        return cacheAccess.useCache("Load task history", new Factory<TaskExecutionList>() {
            public TaskExecutionList create() {
                ClassLoader original = serializer.getClassLoader();
                ClassLoader projectClassLoader = Cast.cast(ProjectInternal.class, task.getProject()).getClassLoaderScope().getLocalClassLoader();
                serializer.setClassLoader(projectClassLoader);
                try {
                    TaskExecutionList history = taskHistoryCache.get(task.getPath());
                    return history == null ? new TaskExecutionList() : history;
                } finally {
                    serializer.setClassLoader(original);
                }
            }
        });
    }

    private Set<String> getDeclaredOutputFilePaths(TaskInternal task) {
        Set<String> declaredOutputFilePaths = new HashSet<String>();
        for (File file : task.getOutputs().getFiles()) {
            declaredOutputFilePaths.add(stringInterner.intern(file.getAbsolutePath()));
        }
        return declaredOutputFilePaths;
    }

    private LazyTaskExecution findBestMatchingPreviousExecution(TaskExecution currentExecution, Collection<LazyTaskExecution> previousExecutions) {
        Set<String> declaredOutputFilePaths = currentExecution.getDeclaredOutputFilePaths();
        LazyTaskExecution bestMatch = null;
        int bestMatchOverlap = 0;
        for (LazyTaskExecution previousExecution : previousExecutions) {
            Set<String> previousDeclaredOutputFilePaths = previousExecution.getDeclaredOutputFilePaths();
            if (declaredOutputFilePaths.isEmpty() && previousDeclaredOutputFilePaths.isEmpty()) {
                bestMatch = previousExecution;
                break;
            }

            Set<String> intersection = Sets.intersection(declaredOutputFilePaths, previousDeclaredOutputFilePaths);
            int overlap = intersection.size();
            if (overlap > bestMatchOverlap) {
                bestMatch = previousExecution;
                bestMatchOverlap = overlap;
            }
            if (bestMatchOverlap == declaredOutputFilePaths.size()) {
                break;
            }
        }
        return bestMatch;
    }

    private static class TaskExecutionListSerializer implements Serializer<TaskExecutionList> {

        private ClassLoader classLoader;
        private final StringInterner stringInterner;

        public TaskExecutionListSerializer(StringInterner stringInterner) {
            this.stringInterner = stringInterner;
        }

        public TaskExecutionList read(Decoder decoder) throws Exception {
            byte executions = decoder.readByte();
            TaskExecutionList history = new TaskExecutionList();
            LazyTaskExecution.LazyTaskExecutionSerializer executionSerializer = new LazyTaskExecution.LazyTaskExecutionSerializer(classLoader, stringInterner);
            for (int i = 0; i < executions; i++) {
                LazyTaskExecution exec = executionSerializer.read(decoder);
                history.executions.add(exec);
            }
            return history;
        }

        public void write(Encoder encoder, TaskExecutionList value) throws Exception {
            int size = value.executions.size();
            encoder.writeByte((byte) size);
            LazyTaskExecution.LazyTaskExecutionSerializer executionSerializer = new LazyTaskExecution.LazyTaskExecutionSerializer(classLoader, stringInterner);
            for (LazyTaskExecution execution : value.executions) {
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

    private static class TaskExecutionList {
        private final Deque<LazyTaskExecution> executions = new ArrayDeque<LazyTaskExecution>();
        public String toString() {
            return super.toString() + "[" + executions.size() + "]";
        }

        public void beforeSerialized() {
            //cleaning up the transient fields, so that any in-memory caching is happy
            for (LazyTaskExecution execution : executions) {
                execution.snapshotRepository = null;
                execution.cacheAccess = null;
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

        static class LazyTaskExecutionSerializer implements Serializer<LazyTaskExecution> {
            private final InputPropertiesSerializer inputPropertiesSerializer;
            private final StringInterner stringInterner;

            public LazyTaskExecutionSerializer(ClassLoader classLoader, StringInterner stringInterner) {
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
                execution.setDeclaredOutputFilePaths(files);

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
                encoder.writeInt(execution.getDeclaredOutputFilePaths().size());
                for (String outputFile : execution.getDeclaredOutputFilePaths()) {
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
