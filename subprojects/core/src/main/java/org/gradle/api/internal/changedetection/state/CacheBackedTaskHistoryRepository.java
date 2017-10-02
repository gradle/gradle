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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.changes.IncrementalTaskInputsInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.GenericFileNormalizer;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.serialize.Serializer;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;
import org.gradle.normalization.internal.InputNormalizationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import static org.gradle.api.internal.changedetection.state.InputPathNormalizationStrategy.ABSOLUTE;

@NonNullApi
public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheBackedTaskHistoryRepository.class);

    private final PersistentIndexedCache<String, HistoricalTaskExecution> taskHistoryCache;
    private final StringInterner stringInterner;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ValueSnapshotter valueSnapshotter;
    private final FileCollectionSnapshotterRegistry snapshotterRegistry;
    private final FileCollectionFactory fileCollectionFactory;
    private final BuildInvocationScopeId buildInvocationScopeId;

    public CacheBackedTaskHistoryRepository(
        TaskHistoryStore cacheAccess,
        Serializer<FileCollectionSnapshot> fileCollectionSnapshotSerializer,
        StringInterner stringInterner,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        FileCollectionSnapshotterRegistry snapshotterRegistry,
        FileCollectionFactory fileCollectionFactory,
        BuildInvocationScopeId buildInvocationScopeId
    ) {
        this.stringInterner = stringInterner;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.valueSnapshotter = valueSnapshotter;
        this.snapshotterRegistry = snapshotterRegistry;
        this.fileCollectionFactory = fileCollectionFactory;
        this.buildInvocationScopeId = buildInvocationScopeId;
        TaskExecutionSnapshotSerializer serializer = new TaskExecutionSnapshotSerializer(stringInterner, fileCollectionSnapshotSerializer);
        this.taskHistoryCache = cacheAccess.createCache("taskHistory", String.class, serializer, 10000, false);
    }

    @Override
    public History getHistory(final TaskInternal task) {
        final InputNormalizationStrategy normalizationStrategy = ((InputNormalizationHandlerInternal) task.getProject().getNormalization()).buildFinalStrategy();

        return new History() {
            private boolean previousExecutionLoadAttempted;
            private HistoricalTaskExecution previousExecution;
            private CurrentTaskExecution currentExecution;

            @Override
            public HistoricalTaskExecution getPreviousExecution() {
                if (!previousExecutionLoadAttempted) {
                    previousExecutionLoadAttempted = true;
                    previousExecution = loadPreviousExecution(task);
                }
                return previousExecution;
            }

            @Override
            public CurrentTaskExecution getCurrentExecution() {
                if (currentExecution == null) {
                    currentExecution = createExecution(task, getPreviousExecution(), normalizationStrategy);
                }
                return currentExecution;
            }

            @Override
            public void updateCurrentExecution(IncrementalTaskInputsInternal taskInputs) {
                updateExecution(getPreviousExecution(), getCurrentExecution(), task, taskInputs, normalizationStrategy);
            }

            @Override
            public void updateCurrentExecutionWithOutputs(IncrementalTaskInputsInternal taskInputs, ImmutableSortedMap<String, FileCollectionSnapshot> newOutputSnapshot) {
                updateExecution(getCurrentExecution(), task, taskInputs, newOutputSnapshot, normalizationStrategy);
            }

            @Override
            public void persist() {
                taskHistoryCache.put(task.getPath(), getCurrentExecution().archive());
            }

        };
    }

    private CurrentTaskExecution createExecution(TaskInternal task, @Nullable HistoricalTaskExecution previousExecution, InputNormalizationStrategy normalizationStrategy) {
        Class<? extends TaskInternal> taskClass = task.getClass();
        List<ContextAwareTaskAction> taskActions = task.getTaskActions();
        ImplementationSnapshot taskImplementation = new ImplementationSnapshot(taskClass.getName(), classLoaderHierarchyHasher.getClassLoaderHash(taskClass.getClassLoader()));
        ImmutableList<ImplementationSnapshot> taskActionImplementations = collectActionImplementations(taskActions, classLoaderHierarchyHasher);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", task, taskImplementation);
            LOGGER.debug("Action implementations for {}: {}", task, taskActionImplementations);
        }

        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = previousExecution == null ? ImmutableSortedMap.<String, ValueSnapshot>of() : previousExecution.getInputProperties();
        ImmutableSortedMap<String, ValueSnapshot> inputProperties = snapshotTaskInputProperties(task, previousInputProperties, valueSnapshotter);

        ImmutableSortedSet<String> outputPropertyNames = getOutputPropertyNamesForCacheKey(task);
        ImmutableSet<String> declaredOutputFilePaths = getDeclaredOutputFilePaths(task, stringInterner);

        ImmutableSortedMap<String, FileCollectionSnapshot> inputFiles = snapshotTaskFiles(task, "Input", normalizationStrategy, task.getInputs().getFileProperties(), snapshotterRegistry);

        ImmutableSortedMap<String, FileCollectionSnapshot> outputFiles = snapshotTaskFiles(task, "Output", normalizationStrategy, task.getOutputs().getFileProperties(), snapshotterRegistry);

        FileCollectionSnapshot previousDiscoveredInputs = previousExecution == null ? null : previousExecution.getDiscoveredInputFilesSnapshot();
        FileCollectionSnapshot discoveredInputs;
        if (previousDiscoveredInputs != null) {
            discoveredInputs = snapshotDiscoveredInputs(task, normalizationStrategy, previousDiscoveredInputs.getElements(), snapshotterRegistry, fileCollectionFactory);
        } else {
            discoveredInputs = FileCollectionSnapshot.EMPTY;
        }

        OverlappingOutputs overlappingOutputs = detectOverlappingOutputs(outputFiles, previousExecution);

        return new CurrentTaskExecution(
            buildInvocationScopeId.getId(),
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            outputPropertyNames,
            declaredOutputFilePaths,
            inputFiles,
            discoveredInputs,
            outputFiles,
            overlappingOutputs
        );
    }

    private void updateExecution(@Nullable final HistoricalTaskExecution previousExecution, CurrentTaskExecution currentExecution, TaskInternal task, IncrementalTaskInputsInternal taskInputs, InputNormalizationStrategy normalizationStrategy) {
        final ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesAfter = snapshotTaskFiles(task, "Output", normalizationStrategy, task.getOutputs().getFileProperties(), snapshotterRegistry);

        ImmutableSortedMap<String, FileCollectionSnapshot> newOutputSnapshot;
        if (currentExecution.getDetectedOverlappingOutputs() == null) {
            newOutputSnapshot = outputFilesAfter;
        } else {
            newOutputSnapshot = ImmutableSortedMap.copyOfSorted(Maps.transformEntries(currentExecution.getOutputFilesSnapshot(), new Maps.EntryTransformer<String, FileCollectionSnapshot, FileCollectionSnapshot>() {
                @Override
                public FileCollectionSnapshot transformEntry(String propertyName, FileCollectionSnapshot beforeExecution) {
                    FileCollectionSnapshot afterExecution = outputFilesAfter.get(propertyName);
                    FileCollectionSnapshot afterPreviousExecution = getSnapshotAfterPreviousExecution(previousExecution, propertyName);
                    return filterOutputSnapshot(afterPreviousExecution, beforeExecution, afterExecution);
                }
            }));
        }
        updateExecution(currentExecution, task, taskInputs, newOutputSnapshot, normalizationStrategy);
    }

    private void updateExecution(CurrentTaskExecution currentExecution, TaskInternal task, @Nullable IncrementalTaskInputsInternal taskInputs, ImmutableSortedMap<String, FileCollectionSnapshot> newOutputSnapshot, InputNormalizationStrategy normalizationStrategy) {
        currentExecution.setSuccessful(task.getState().getFailure() == null);

        currentExecution.setOutputFilesSnapshot(newOutputSnapshot);

        FileCollectionSnapshot discoveredFilesSnapshot;
        if (taskInputs != null) {
            discoveredFilesSnapshot = snapshotDiscoveredInputs(task, normalizationStrategy, taskInputs.getDiscoveredInputs(), snapshotterRegistry, fileCollectionFactory);
        } else {
            discoveredFilesSnapshot = FileCollectionSnapshot.EMPTY;
        }
        currentExecution.setDiscoveredInputFilesSnapshot(discoveredFilesSnapshot);
    }

    private static FileCollectionSnapshot snapshotDiscoveredInputs(Task task, InputNormalizationStrategy normalizationStrategy, Collection<File> discoveredInputs, FileCollectionSnapshotterRegistry snapshotterRegistry, FileCollectionFactory fileCollectionFactory) {
        FileCollectionSnapshotter snapshotter = snapshotterRegistry.getSnapshotter(GenericFileNormalizer.class);
        if (discoveredInputs.isEmpty()) {
            LOGGER.debug("No discovered inputs for {}", task);
            return FileCollectionSnapshot.EMPTY;
        }
        LOGGER.debug("Snapshotting discovered inputs for {}", task);
        try {
            return snapshotter.snapshot(fileCollectionFactory.fixed("Discovered input files", discoveredInputs), ABSOLUTE, normalizationStrategy);
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of discovered input files for %s during up-to-date check.", task), e);
        }
    }

    /**
     * Returns a new snapshot that filters out entries that should not be considered outputs of the task.
     */
    private static FileCollectionSnapshot filterOutputSnapshot(
        @Nullable FileCollectionSnapshot afterPreviousExecution,
        FileCollectionSnapshot beforeExecution,
        FileCollectionSnapshot afterExecution
    ) {
        FileCollectionSnapshot filesSnapshot;
        Map<String, NormalizedFileSnapshot> afterSnapshots = afterExecution.getSnapshots();
        if (!beforeExecution.getSnapshots().isEmpty() && !afterSnapshots.isEmpty()) {
            Map<String, NormalizedFileSnapshot> beforeSnapshots = beforeExecution.getSnapshots();
            Map<String, NormalizedFileSnapshot> afterPreviousSnapshots = afterPreviousExecution != null ? afterPreviousExecution.getSnapshots() : new HashMap<String, NormalizedFileSnapshot>();
            int newEntryCount = 0;
            ImmutableMap.Builder<String, NormalizedFileSnapshot> outputEntries = ImmutableMap.builder();

            for (Map.Entry<String, NormalizedFileSnapshot> entry : afterSnapshots.entrySet()) {
                final String path = entry.getKey();
                NormalizedFileSnapshot fileSnapshot = entry.getValue();
                if (isOutputEntry(path, fileSnapshot, beforeSnapshots, afterPreviousSnapshots)) {
                    outputEntries.put(entry.getKey(), fileSnapshot);
                    newEntryCount++;
                }
            }
            // Are all files snapshot after execution accounted for as new entries?
            if (newEntryCount == afterSnapshots.size()) {
                filesSnapshot = afterExecution;
            } else {
                filesSnapshot = new DefaultFileCollectionSnapshot(outputEntries.build(), TaskFilePropertyCompareStrategy.UNORDERED, true);
            }
        } else {
            filesSnapshot = afterExecution;
        }
        return filesSnapshot;
    }

    /**
     * Decide whether an entry should be considered to be part of the output. Entries that are considered outputs are:
     * <ul>
     *     <li>an entry that did not exist before the execution, but exists after the execution</li>
     *     <li>an entry that did exist before the execution, and has been changed during the execution</li>
     *     <li>an entry that did wasn't changed during the execution, but was already considered an output during the previous execution</li>
     * </ul>
     */
    private static boolean isOutputEntry(String path, NormalizedFileSnapshot fileSnapshot, Map<String, NormalizedFileSnapshot> beforeSnapshots, Map<String, NormalizedFileSnapshot> afterPreviousSnapshots) {
        if (fileSnapshot.getSnapshot().getType() == FileType.Missing) {
            return false;
        }
        NormalizedFileSnapshot beforeSnapshot = beforeSnapshots.get(path);
        // Was it created during execution?
        if (beforeSnapshot == null) {
            return true;
        }
        // Was it updated during execution?
        if (!fileSnapshot.getSnapshot().isContentAndMetadataUpToDate(beforeSnapshot.getSnapshot())) {
            return true;
        }
        // Did we already consider it as an output after the previous execution?
        return afterPreviousSnapshots.containsKey(path);
    }

    private static ImmutableList<ImplementationSnapshot> collectActionImplementations(Collection<ContextAwareTaskAction> taskActions, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        if (taskActions.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<ImplementationSnapshot> actionImplementations = ImmutableList.builder();
        for (ContextAwareTaskAction taskAction : taskActions) {
            String typeName = taskAction.getActionClassName();
            HashCode classLoaderHash = classLoaderHierarchyHasher.getClassLoaderHash(taskAction.getClassLoader());
            actionImplementations.add(new ImplementationSnapshot(typeName, classLoaderHash));
        }
        return actionImplementations.build();
    }

    private static ImmutableSortedMap<String, ValueSnapshot> snapshotTaskInputProperties(TaskInternal task, ImmutableSortedMap<String, ValueSnapshot> previousInputProperties, ValueSnapshotter valueSnapshotter) {
        ImmutableSortedMap.Builder<String, ValueSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, Object> entry : task.getInputs().getProperties().entrySet()) {
            String propertyName = entry.getKey();
            Object value = entry.getValue();
            try {
                ValueSnapshot previousSnapshot = previousInputProperties.get(propertyName);
                if (previousSnapshot == null) {
                    builder.put(propertyName, valueSnapshotter.snapshot(value));
                } else {
                    builder.put(propertyName, valueSnapshotter.snapshot(value, previousSnapshot));
                }
            } catch (Exception e) {
                throw new UncheckedIOException(String.format("Unable to store input properties for %s. Property '%s' with value '%s' cannot be serialized.", task, propertyName, value), e);
            }
        }

        return builder.build();
    }

    @VisibleForTesting
    static ImmutableSortedMap<String, FileCollectionSnapshot> snapshotTaskFiles(TaskInternal task, String title, InputNormalizationStrategy normalizationStrategy, SortedSet<? extends TaskFilePropertySpec> fileProperties, FileCollectionSnapshotterRegistry snapshotterRegistry) {
        ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (TaskFilePropertySpec propertySpec : fileProperties) {
            FileCollectionSnapshot result;
            try {
                FileCollectionSnapshotter snapshotter = snapshotterRegistry.getSnapshotter(propertySpec.getNormalizer());
                LOGGER.debug("Snapshotting property {} for {}", propertySpec, task);
                result = snapshotter.snapshot(propertySpec.getPropertyFiles(), propertySpec.getPathNormalizationStrategy(), normalizationStrategy);
            } catch (Exception e) {
                throw new UncheckedIOException(String.format("Failed to capture snapshot of %s files for %s property '%s' during up-to-date check.", title.toLowerCase(), task, propertySpec.getPropertyName()), e);
            }
            builder.put(propertySpec.getPropertyName(), result);
        }
        return builder.build();
    }

    @Nullable
    private static OverlappingOutputs detectOverlappingOutputs(ImmutableSortedMap<String, FileCollectionSnapshot> taskOutputs, @Nullable HistoricalTaskExecution previousExecution) {
        for (Map.Entry<String, FileCollectionSnapshot> entry : taskOutputs.entrySet()) {
            String propertyName = entry.getKey();
            FileCollectionSnapshot beforeExecution = entry.getValue();
            FileCollectionSnapshot afterPreviousExecution = getSnapshotAfterPreviousExecution(previousExecution, propertyName);
            OverlappingOutputs overlappingOutputs = OverlappingOutputs.detect(propertyName, afterPreviousExecution, beforeExecution);
            if (overlappingOutputs != null) {
                return overlappingOutputs;
            }
        }
        return null;
    }

    private static FileCollectionSnapshot getSnapshotAfterPreviousExecution(@Nullable HistoricalTaskExecution previousExecution, String propertyName) {
        if (previousExecution != null) {
            Map<String, FileCollectionSnapshot> previousSnapshots = previousExecution.getOutputFilesSnapshot();
            FileCollectionSnapshot afterPreviousExecution = previousSnapshots.get(propertyName);
            if (afterPreviousExecution != null) {
                return afterPreviousExecution;
            }
        }
        return FileCollectionSnapshot.EMPTY;
    }

    @Nullable
    private HistoricalTaskExecution loadPreviousExecution(TaskInternal task) {
        return taskHistoryCache.get(task.getPath());
    }

    private static ImmutableSortedSet<String> getOutputPropertyNamesForCacheKey(TaskInternal task) {
        ImmutableSortedSet<TaskOutputFilePropertySpec> fileProperties = task.getOutputs().getFileProperties();
        List<String> outputPropertyNames = Lists.newArrayListWithCapacity(fileProperties.size());
        for (TaskOutputFilePropertySpec propertySpec : fileProperties) {
            if (propertySpec instanceof CacheableTaskOutputFilePropertySpec) {
                CacheableTaskOutputFilePropertySpec cacheablePropertySpec = (CacheableTaskOutputFilePropertySpec) propertySpec;
                if (cacheablePropertySpec.getOutputFile() != null) {
                    outputPropertyNames.add(propertySpec.getPropertyName());
                }
            }
        }
        return ImmutableSortedSet.copyOf(outputPropertyNames);
    }

    private static ImmutableSet<String> getDeclaredOutputFilePaths(TaskInternal task, StringInterner stringInterner) {
        ImmutableSet.Builder<String> declaredOutputFilePaths = ImmutableSortedSet.naturalOrder();
        for (File file : task.getOutputs().getFiles()) {
            declaredOutputFilePaths.add(stringInterner.intern(file.getAbsolutePath()));
        }
        return declaredOutputFilePaths.build();
    }

}
