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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {

    private final FileSnapshotRepository snapshotRepository;
    private final PersistentIndexedCache<String, TaskExecutionSnapshot> taskHistoryCache;
    private final StringInterner stringInterner;
    private final BuildInvocationScopeId buildInvocationScopeId;

    public CacheBackedTaskHistoryRepository(TaskHistoryStore cacheAccess, FileSnapshotRepository snapshotRepository, StringInterner stringInterner, BuildInvocationScopeId buildInvocationScopeId) {
        this.snapshotRepository = snapshotRepository;
        this.stringInterner = stringInterner;
        this.buildInvocationScopeId = buildInvocationScopeId;
        LazyTaskExecution.TaskExecutionSnapshotSerializer serializer = new LazyTaskExecution.TaskExecutionSnapshotSerializer(stringInterner);
        this.taskHistoryCache = cacheAccess.createCache("taskHistory", String.class, serializer, 10000, false);
    }

    public History getHistory(final TaskInternal task) {
        final LazyTaskExecution previousExecution = loadPreviousExecution(task);
        final LazyTaskExecution currentExecution = new LazyTaskExecution(buildInvocationScopeId.getId(), snapshotRepository);
        currentExecution.setOutputPropertyNamesForCacheKey(getOutputPropertyNamesForCacheKey(task));
        currentExecution.setDeclaredOutputFilePaths(getDeclaredOutputFilePaths(task));

        return new History() {
            public TaskExecution getPreviousExecution() {
                return previousExecution;
            }

            public TaskExecution getCurrentExecution() {
                return currentExecution;
            }

            public void update() {
                storeSnapshots(currentExecution);
                if (previousExecution != null) {
                    removeUnnecessarySnapshots(previousExecution);
                }
                taskHistoryCache.put(task.getPath(), currentExecution.snapshot());
            }

            private void storeSnapshots(LazyTaskExecution execution) {
                if (execution.inputFilesSnapshotIds == null && execution.inputFilesSnapshot != null) {
                    ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
                    for (Map.Entry<String, FileCollectionSnapshot> entry : execution.inputFilesSnapshot.entrySet()) {
                        builder.put(entry.getKey(), snapshotRepository.add(entry.getValue()));
                    }
                    execution.inputFilesSnapshotIds = builder.build();
                }
                if (execution.outputFilesSnapshotIds == null && execution.outputFilesSnapshot != null) {
                    ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
                    for (Map.Entry<String, FileCollectionSnapshot> entry : execution.outputFilesSnapshot.entrySet()) {
                        builder.put(entry.getKey(), snapshotRepository.add(entry.getValue()));
                    }
                    execution.outputFilesSnapshotIds = builder.build();
                }
                if (execution.discoveredFilesSnapshotId == null && execution.discoveredFilesSnapshot != null) {
                    execution.discoveredFilesSnapshotId = snapshotRepository.add(execution.discoveredFilesSnapshot);
                }
            }

            private void removeUnnecessarySnapshots(LazyTaskExecution execution) {
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
        };
    }

    private LazyTaskExecution loadPreviousExecution(TaskInternal task) {
        TaskExecutionSnapshot taskExecutionSnapshot = taskHistoryCache.get(task.getPath());
        if (taskExecutionSnapshot != null) {
            return new LazyTaskExecution(taskExecutionSnapshot, snapshotRepository);
        } else {
            return null;
        }
    }

    private Iterable<String> getOutputPropertyNamesForCacheKey(TaskInternal task) {
        // Find all output properties that go into the cache key
        Iterable<TaskOutputFilePropertySpec> outputPropertiesForCacheKey =
            Iterables.filter(task.getOutputs().getFileProperties(), new Predicate<TaskOutputFilePropertySpec>() {
                @Override
                public boolean apply(TaskOutputFilePropertySpec propertySpec) {
                    if (propertySpec instanceof CacheableTaskOutputFilePropertySpec) {
                        CacheableTaskOutputFilePropertySpec cacheablePropertySpec = (CacheableTaskOutputFilePropertySpec) propertySpec;
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
        ImmutableSet.Builder<String> declaredOutputFilePaths = ImmutableSortedSet.naturalOrder();
        for (File file : task.getOutputs().getFiles()) {
            declaredOutputFilePaths.add(stringInterner.intern(file.getAbsolutePath()));
        }
        return declaredOutputFilePaths.build();
    }

    private static class LazyTaskExecution extends TaskExecution {
        private ImmutableSortedMap<String, Long> inputFilesSnapshotIds;
        private ImmutableSortedMap<String, Long> outputFilesSnapshotIds;
        private Long discoveredFilesSnapshotId;
        private final FileSnapshotRepository snapshotRepository;
        private ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot;
        private ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot;
        private FileCollectionSnapshot discoveredFilesSnapshot;

        /**
         * Creates a mutable copy of the given snapshot.
         */
        LazyTaskExecution(TaskExecutionSnapshot taskExecutionSnapshot, FileSnapshotRepository snapshotRepository) {
            this(snapshotRepository);
            setSuccessful(taskExecutionSnapshot.isSuccessful());
            setBuildInvocationId(taskExecutionSnapshot.getBuildInvocationId());
            setTaskImplementation(taskExecutionSnapshot.getTaskImplementation());
            setTaskActionImplementations(taskExecutionSnapshot.getTaskActionsImplementations());
            setInputProperties(taskExecutionSnapshot.getInputProperties());
            setOutputPropertyNamesForCacheKey(taskExecutionSnapshot.getCacheableOutputProperties());
            setDeclaredOutputFilePaths(taskExecutionSnapshot.getDeclaredOutputFilePaths());
            inputFilesSnapshotIds = taskExecutionSnapshot.getInputFilesSnapshotIds();
            outputFilesSnapshotIds = taskExecutionSnapshot.getOutputFilesSnapshotIds();
            discoveredFilesSnapshotId = taskExecutionSnapshot.getDiscoveredFilesSnapshotId();
        }

        LazyTaskExecution(UniqueId buildInvocationId, FileSnapshotRepository snapshotRepository) {
            this(snapshotRepository);
            setBuildInvocationId(buildInvocationId);
        }

        private LazyTaskExecution(FileSnapshotRepository snapshotRepository) {
            this.snapshotRepository = snapshotRepository;
        }

        @Override
        public ImmutableSortedMap<String, FileCollectionSnapshot> getInputFilesSnapshot() {
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
        public void setInputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot) {
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
        public ImmutableSortedMap<String, FileCollectionSnapshot> getOutputFilesSnapshot() {
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
        public void setOutputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot) {
            this.outputFilesSnapshot = outputFilesSnapshot;
            outputFilesSnapshotIds = null;
        }

        public TaskExecutionSnapshot snapshot() {
            return new TaskExecutionSnapshot(
                isSuccessful(),
                getBuildInvocationId(),
                getTaskImplementation(),
                getTaskActionImplementations(),
                getOutputPropertyNamesForCacheKey(),
                getDeclaredOutputFilePaths(),
                getInputProperties(),
                inputFilesSnapshotIds,
                discoveredFilesSnapshotId,
                outputFilesSnapshotIds
            );
        }

        static class TaskExecutionSnapshotSerializer extends AbstractSerializer<TaskExecutionSnapshot> {
            private final InputPropertiesSerializer inputPropertiesSerializer;
            private final StringInterner stringInterner;

            TaskExecutionSnapshotSerializer(StringInterner stringInterner) {
                this.inputPropertiesSerializer = new InputPropertiesSerializer();
                this.stringInterner = stringInterner;
            }

            public TaskExecutionSnapshot read(Decoder decoder) throws Exception {
                boolean successful = decoder.readBoolean();

                UniqueId buildId = UniqueId.from(decoder.readString());

                ImmutableSortedMap<String, Long> inputFilesSnapshotIds = readSnapshotIds(decoder);
                ImmutableSortedMap<String, Long> outputFilesSnapshotIds = readSnapshotIds(decoder);
                Long discoveredFilesSnapshotId = decoder.readLong();

                ImplementationSnapshot taskImplementation = readImplementation(decoder);

                // We can't use an immutable list here because some hashes can be null
                int taskActionsCount = decoder.readSmallInt();
                ImmutableList.Builder<ImplementationSnapshot> taskActionImplementationsBuilder = ImmutableList.builder();
                for (int j = 0; j < taskActionsCount; j++) {
                    ImplementationSnapshot actionImpl = readImplementation(decoder);
                    taskActionImplementationsBuilder.add(actionImpl);
                }
                ImmutableList<ImplementationSnapshot> taskActionImplementations = taskActionImplementationsBuilder.build();

                int cacheableOutputPropertiesCount = decoder.readSmallInt();
                ImmutableSortedSet.Builder<String> cacheableOutputPropertiesBuilder = ImmutableSortedSet.naturalOrder();
                for (int j = 0; j < cacheableOutputPropertiesCount; j++) {
                    cacheableOutputPropertiesBuilder.add(decoder.readString());
                }
                ImmutableSortedSet<String> cacheableOutputProperties = cacheableOutputPropertiesBuilder.build();

                int outputFilesCount = decoder.readSmallInt();
                ImmutableSet.Builder<String> declaredOutputFilePathsBuilder = ImmutableSet.builder();
                for (int j = 0; j < outputFilesCount; j++) {
                    declaredOutputFilePathsBuilder.add(stringInterner.intern(decoder.readString()));
                }
                ImmutableSet<String> declaredOutputFilePaths = declaredOutputFilePathsBuilder.build();

                ImmutableSortedMap<String, ValueSnapshot> inputProperties = inputPropertiesSerializer.read(decoder);

                return new TaskExecutionSnapshot(
                    successful,
                    buildId,
                    taskImplementation,
                    taskActionImplementations,
                    cacheableOutputProperties,
                    declaredOutputFilePaths,
                    inputProperties,
                    inputFilesSnapshotIds,
                    discoveredFilesSnapshotId,
                    outputFilesSnapshotIds
                );
            }

            public void write(Encoder encoder, TaskExecutionSnapshot execution) throws Exception {
                encoder.writeBoolean(execution.isSuccessful());
                encoder.writeString(execution.getBuildInvocationId().asString());
                writeSnapshotIds(encoder, execution.getInputFilesSnapshotIds());
                writeSnapshotIds(encoder, execution.getOutputFilesSnapshotIds());
                encoder.writeLong(execution.getDiscoveredFilesSnapshotId());
                writeImplementation(encoder, execution.getTaskImplementation());
                encoder.writeSmallInt(execution.getTaskActionsImplementations().size());
                for (ImplementationSnapshot actionImpl : execution.getTaskActionsImplementations()) {
                    writeImplementation(encoder, actionImpl);
                }
                encoder.writeSmallInt(execution.getCacheableOutputProperties().size());
                for (String outputFile : execution.getCacheableOutputProperties()) {
                    encoder.writeString(outputFile);
                }
                encoder.writeSmallInt(execution.getDeclaredOutputFilePaths().size());
                for (String outputFile : execution.getDeclaredOutputFilePaths()) {
                    encoder.writeString(outputFile);
                }
                inputPropertiesSerializer.write(encoder, execution.getInputProperties());
            }

            private static ImplementationSnapshot readImplementation(Decoder decoder) throws IOException {
                String typeName = decoder.readString();
                HashCode classLoaderHash = decoder.readBoolean() ? HashCode.fromBytes(decoder.readBinary()) : null;
                return new ImplementationSnapshot(typeName, classLoaderHash);
            }

            private static void writeImplementation(Encoder encoder, ImplementationSnapshot implementation) throws IOException {
                encoder.writeString(implementation.getTypeName());
                if (implementation.hasUnknownClassLoader()) {
                    encoder.writeBoolean(false);
                } else {
                    encoder.writeBoolean(true);
                    encoder.writeBinary(implementation.getClassLoaderHash().asBytes());
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
