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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.AsyncCacheAccessContext;
import org.gradle.internal.Cast;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {
    private static final int MAX_HISTORY_ENTRIES = 3;

    private final FileSnapshotRepository snapshotRepository;
    private final PersistentIndexedCache<String, ImmutableList<TaskExecutionSnapshot>> taskHistoryCache;
    private final TaskExecutionListSerializer serializer;
    private final StringInterner stringInterner;

    public CacheBackedTaskHistoryRepository(TaskHistoryStore cacheAccess, FileSnapshotRepository snapshotRepository, StringInterner stringInterner) {
        this.snapshotRepository = snapshotRepository;
        this.stringInterner = stringInterner;
        this.serializer = new TaskExecutionListSerializer(stringInterner);
        taskHistoryCache = cacheAccess.createCache("taskArtifacts", String.class, serializer);
    }

    public History getHistory(final TaskInternal task) {
        final TaskExecutionList previousExecutions = loadPreviousExecutions(task);
        final LazyTaskExecution currentExecution = new LazyTaskExecution();
        currentExecution.snapshotRepository = snapshotRepository;
        currentExecution.setOutputPropertyNamesForCacheKey(getOutputPropertyNamesForCacheKey(task));
        currentExecution.setDeclaredOutputFilePaths(getDeclaredOutputFilePaths(task));
        final LazyTaskExecution previousExecution = findBestMatchingPreviousExecution(currentExecution, previousExecutions.executions);
        if (previousExecution != null) {
            previousExecution.snapshotRepository = snapshotRepository;
        }

        return new History() {
            public TaskExecution getPreviousExecution() {
                return previousExecution;
            }

            public TaskExecution getCurrentExecution() {
                return currentExecution;
            }

            public void update() {
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
                taskHistoryCache.put(task.getPath(), previousExecutions.snapshot());
            }
        };
    }

    private TaskExecutionList loadPreviousExecutions(final TaskInternal task) {
        boolean contextCreated = AsyncCacheAccessContext.createWhenMissing();
        ClassLoader projectClassLoader = Cast.cast(ProjectInternal.class, task.getProject()).getClassLoaderScope().getLocalClassLoader();
        try {
            serializer.setClassLoader(projectClassLoader);
            List<TaskExecutionSnapshot> history = taskHistoryCache.get(task.getPath());
            TaskExecutionList result = new TaskExecutionList();
            if (history != null) {
                for (TaskExecutionSnapshot taskExecutionSnapshot : history) {
                    result.executions.add(new LazyTaskExecution(taskExecutionSnapshot));
                }
            }
            return result;
        } finally {
            serializer.setClassLoader(null);
            if (contextCreated) {
                AsyncCacheAccessContext.remove();
            }
        }
    }

    private Iterable<String> getOutputPropertyNamesForCacheKey(TaskInternal task) {
        // Find all output properties that go into the cache key
        Iterable<TaskOutputFilePropertySpec> outputPropertiesForCacheKey =
            Iterables.filter(task.getOutputs().getFileProperties(), new Predicate<TaskOutputFilePropertySpec>() {
                @Override
                public boolean apply(TaskOutputFilePropertySpec propertySpec) {
                    if (propertySpec instanceof CacheableTaskOutputFilePropertySpec) {
                        CacheableTaskOutputFilePropertySpec cacheablePropertySpec = (CacheableTaskOutputFilePropertySpec)propertySpec;
                        return cacheablePropertySpec.getOutputFile() != null;
                    }
                    return false;
                }
        });
        // Extract the output property names
        return Iterables.transform(outputPropertiesForCacheKey, new Function<TaskOutputFilePropertySpec, String>() {
            @Override
            public String apply(TaskOutputFilePropertySpec propertySpec) {
                return propertySpec.getPropertyName();
            }
        });
    }

    private ImmutableSet<String> getDeclaredOutputFilePaths(TaskInternal task) {
        ImmutableSet.Builder<String> declaredOutputFilePaths = ImmutableSet.builder();
        for (File file : task.getOutputs().getFiles()) {
            declaredOutputFilePaths.add(stringInterner.intern(file.getAbsolutePath()));
        }
        return declaredOutputFilePaths.build();
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

    private static class TaskExecutionListSerializer implements Serializer<ImmutableList<TaskExecutionSnapshot>> {
        private static final String CONTEXT_KEY_FOR_CLASSLOADER = AsyncCacheAccessContext.createKey(TaskExecutionListSerializer.class, "classLoader");
        private final StringInterner stringInterner;

        TaskExecutionListSerializer(StringInterner stringInterner) {
            this.stringInterner = stringInterner;
        }

        public ImmutableList<TaskExecutionSnapshot> read(Decoder decoder) throws Exception {
            byte count = decoder.readByte();
            List<TaskExecutionSnapshot> executions = new ArrayList<TaskExecutionSnapshot>(count);
            LazyTaskExecution.TaskExecutionSnapshotSerializer executionSerializer = new LazyTaskExecution.TaskExecutionSnapshotSerializer(getClassLoader(), stringInterner);
            for (int i = 0; i < count; i++) {
                TaskExecutionSnapshot exec = executionSerializer.read(decoder);
                executions.add(exec);
            }
            return ImmutableList.copyOf(executions);
        }

        public void write(Encoder encoder, ImmutableList<TaskExecutionSnapshot> value) throws Exception {
            int size = value.size();
            encoder.writeByte((byte) size);
            LazyTaskExecution.TaskExecutionSnapshotSerializer executionSerializer = new LazyTaskExecution.TaskExecutionSnapshotSerializer(getClassLoader(), stringInterner);
            for (TaskExecutionSnapshot execution : value) {
                executionSerializer.write(encoder, execution);
            }
        }

        public ClassLoader getClassLoader() {
            AsyncCacheAccessContext context = AsyncCacheAccessContext.current();
            if (context != null) {
                return context.get(CONTEXT_KEY_FOR_CLASSLOADER, ClassLoader.class);
            } else {
                return getClass().getClassLoader();
            }
        }

        public void setClassLoader(ClassLoader classLoader) {
            AsyncCacheAccessContext context = AsyncCacheAccessContext.current();
            if (context != null) {
                context.put(CONTEXT_KEY_FOR_CLASSLOADER, classLoader);
            }
        }
    }

    private static class TaskExecutionList {
        private final Deque<LazyTaskExecution> executions = new ArrayDeque<LazyTaskExecution>();

        public String toString() {
            return super.toString() + "[" + executions.size() + "]";
        }

        public ImmutableList<TaskExecutionSnapshot> snapshot() {
            List<TaskExecutionSnapshot> snapshots = new ArrayList<TaskExecutionSnapshot>(executions.size());
            for (LazyTaskExecution execution : executions) {
                snapshots.add(execution.snapshot());
            }
            return ImmutableList.copyOf(snapshots);
        }
    }

    private static class LazyTaskExecution extends TaskExecution {
        private ImmutableSortedMap<String, Long> inputFilesSnapshotIds;
        private ImmutableSortedMap<String, Long> outputFilesSnapshotIds;
        private Long discoveredFilesSnapshotId;
        private FileSnapshotRepository snapshotRepository;
        private Map<String, FileCollectionSnapshot> inputFilesSnapshot;
        private Map<String, FileCollectionSnapshot> outputFilesSnapshot;
        private FileCollectionSnapshot discoveredFilesSnapshot;

        /**
         * Creates a mutable copy of the given snapshot.
         */
        LazyTaskExecution(TaskExecutionSnapshot taskExecutionSnapshot) {
            setTaskClass(taskExecutionSnapshot.getTaskClass());
            setTaskClassLoaderHash(taskExecutionSnapshot.getTaskClassLoaderHash());
            setTaskActionsClassLoaderHash(taskExecutionSnapshot.getTaskActionsClassLoaderHash());
            // Take copy of input properties map
            setInputProperties(new HashMap<String, Object>(taskExecutionSnapshot.getInputProperties()));
            setOutputPropertyNamesForCacheKey(taskExecutionSnapshot.getCacheableOutputProperties());
            setDeclaredOutputFilePaths(taskExecutionSnapshot.getDeclaredOutputFilePaths());
            inputFilesSnapshotIds = taskExecutionSnapshot.getInputFilesSnapshotIds();
            outputFilesSnapshotIds = taskExecutionSnapshot.getOutputFilesSnapshotIds();
            discoveredFilesSnapshotId = taskExecutionSnapshot.getDiscoveredFilesSnapshotId();
        }

        LazyTaskExecution() {
        }

        @Override
        public Map<String, FileCollectionSnapshot> getInputFilesSnapshot() {
            if (inputFilesSnapshot == null) {
                ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
                for (Map.Entry<String, Long> entry : inputFilesSnapshotIds.entrySet()) {
                    builder.put(entry.getKey(), snapshotRepository.get(entry.getValue()));
                }
                inputFilesSnapshot = builder.build();
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
                discoveredFilesSnapshot = snapshotRepository.get(discoveredFilesSnapshotId);
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
                ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
                for (Map.Entry<String, Long> entry : outputFilesSnapshotIds.entrySet()) {
                    String propertyName = entry.getKey();
                    builder.put(propertyName, snapshotRepository.get(entry.getValue()));
                }
                outputFilesSnapshot = builder.build();
            }
            return outputFilesSnapshot;
        }

        @Override
        public void setOutputFilesSnapshot(Map<String, FileCollectionSnapshot> outputFilesSnapshot) {
            this.outputFilesSnapshot = outputFilesSnapshot;
            outputFilesSnapshotIds = null;
        }

        public TaskExecutionSnapshot snapshot() {
            return new TaskExecutionSnapshot(
                getTaskClass(),
                getOutputPropertyNamesForCacheKey(),
                getDeclaredOutputFilePaths(),
                getTaskClassLoaderHash(),
                getTaskActionsClassLoaderHash(),
                new HashMap<String, Object>(getInputProperties()),
                inputFilesSnapshotIds,
                discoveredFilesSnapshotId,
                outputFilesSnapshotIds);
        }

        static class TaskExecutionSnapshotSerializer implements Serializer<TaskExecutionSnapshot> {
            private final InputPropertiesSerializer inputPropertiesSerializer;
            private final StringInterner stringInterner;

            TaskExecutionSnapshotSerializer(ClassLoader classLoader, StringInterner stringInterner) {
                this.inputPropertiesSerializer = new InputPropertiesSerializer(classLoader);
                this.stringInterner = stringInterner;
            }

            public TaskExecutionSnapshot read(Decoder decoder) throws Exception {
                ImmutableSortedMap<String, Long> inputFilesSnapshotIds = readSnapshotIds(decoder);
                ImmutableSortedMap<String, Long> outputFilesSnapshotIds = readSnapshotIds(decoder);
                Long discoveredFilesSnapshotId = decoder.readLong();
                String taskClass = decoder.readString();
                HashCode taskClassLoaderHash = null;
                if (decoder.readBoolean()) {
                    taskClassLoaderHash = HashCode.fromBytes(decoder.readBinary());
                }
                HashCode taskActionsClassLoaderHash = null;
                if (decoder.readBoolean()) {
                    taskActionsClassLoaderHash = HashCode.fromBytes(decoder.readBinary());
                }

                int cacheableOutputPropertiesCount = decoder.readSmallInt();
                ImmutableSet.Builder<String> cacheableOutputPropertiesBuilder = ImmutableSet.builder();
                for (int j = 0; j < cacheableOutputPropertiesCount; j++) {
                    cacheableOutputPropertiesBuilder.add(decoder.readString());
                }
                ImmutableSet<String> cacheableOutputProperties = cacheableOutputPropertiesBuilder.build();

                int outputFilesCount = decoder.readSmallInt();
                ImmutableSet.Builder<String> declaredOutputFilePathsBuilder = ImmutableSet.builder();
                for (int j = 0; j < outputFilesCount; j++) {
                    declaredOutputFilePathsBuilder.add(stringInterner.intern(decoder.readString()));
                }
                ImmutableSet<String> declaredOutputFilePaths = declaredOutputFilePathsBuilder.build();

                boolean hasInputProperties = decoder.readBoolean();
                Map<String, Object> inputProperties;
                if (hasInputProperties) {
                    inputProperties = inputPropertiesSerializer.read(decoder);
                } else {
                    inputProperties = ImmutableMap.of();
                }
                return new TaskExecutionSnapshot(
                    taskClass,
                    cacheableOutputProperties,
                    declaredOutputFilePaths,
                    taskClassLoaderHash,
                    taskActionsClassLoaderHash,
                    inputProperties,
                    inputFilesSnapshotIds,
                    discoveredFilesSnapshotId,
                    outputFilesSnapshotIds
                );
            }

            public void write(Encoder encoder, TaskExecutionSnapshot execution) throws Exception {
                writeSnapshotIds(encoder, execution.getInputFilesSnapshotIds());
                writeSnapshotIds(encoder, execution.getOutputFilesSnapshotIds());
                encoder.writeLong(execution.getDiscoveredFilesSnapshotId());
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
                encoder.writeSmallInt(execution.getCacheableOutputProperties().size());
                for (String outputFile : execution.getCacheableOutputProperties()) {
                    encoder.writeString(outputFile);
                }
                encoder.writeSmallInt(execution.getDeclaredOutputFilePaths().size());
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

            private static ImmutableSortedMap<String, Long> readSnapshotIds(Decoder decoder) throws IOException {
                int count = decoder.readSmallInt();
                ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
                for (int snapshotIdx = 0; snapshotIdx < count; snapshotIdx++) {
                    String property = decoder.readString();
                    long id = decoder.readLong();
                    builder.put(property, id);
                }
                return builder.build();
            }

            private static void writeSnapshotIds(Encoder encoder, Map<String, Long> ids) throws IOException {
                encoder.writeSmallInt(ids.size());
                for (Map.Entry<String, Long> entry : ids.entrySet()) {
                    encoder.writeString(entry.getKey());
                    encoder.writeLong(entry.getValue());
                }
            }
        }
    }
}
