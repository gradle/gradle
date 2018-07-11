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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.api.internal.changedetection.state.mirror.logical.DefaultFileCollectionFingerprint;
import org.gradle.api.internal.changedetection.state.mirror.logical.FingerprintCompareStrategy;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.Serializer;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;
import org.gradle.normalization.internal.InputNormalizationStrategy;
import org.gradle.util.DeprecationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

@NonNullApi
public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheBackedTaskHistoryRepository.class);

    private final PersistentIndexedCache<String, HistoricalTaskExecution> taskHistoryCache;
    private final StringInterner stringInterner;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ValueSnapshotter valueSnapshotter;
    private final FileCollectionSnapshotterRegistry snapshotterRegistry;

    public CacheBackedTaskHistoryRepository(
        TaskHistoryStore cacheAccess,
        Serializer<FileCollectionSnapshot> fileCollectionSnapshotSerializer,
        StringInterner stringInterner,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        FileCollectionSnapshotterRegistry snapshotterRegistry
    ) {
        this.stringInterner = stringInterner;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.valueSnapshotter = valueSnapshotter;
        this.snapshotterRegistry = snapshotterRegistry;
        TaskExecutionSnapshotSerializer serializer = new TaskExecutionSnapshotSerializer(fileCollectionSnapshotSerializer);
        this.taskHistoryCache = cacheAccess.createCache("taskHistory", String.class, serializer, 10000, false);
    }

    @Override
    public History getHistory(final TaskInternal task, final TaskProperties taskProperties) {
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
                    currentExecution = createExecution(task, taskProperties, getPreviousExecution(), normalizationStrategy);
                }
                return currentExecution;
            }

            @Override
            public void updateCurrentExecution() {
                updateExecution(getPreviousExecution(), getCurrentExecution(), task, taskProperties, normalizationStrategy);
            }

            @Override
            public void updateCurrentExecutionWithOutputs(ImmutableSortedMap<String, FileCollectionSnapshot> newOutputSnapshot) {
                updateExecution(getCurrentExecution(), task, newOutputSnapshot);
            }

            @Override
            public void persist() {
                taskHistoryCache.put(task.getPath(), getCurrentExecution().archive());
            }

        };
    }

    private CurrentTaskExecution createExecution(TaskInternal task, TaskProperties taskProperties, @Nullable HistoricalTaskExecution previousExecution, InputNormalizationStrategy normalizationStrategy) {
        Class<? extends TaskInternal> taskClass = task.getClass();
        List<ContextAwareTaskAction> taskActions = task.getTaskActions();
        ImplementationSnapshot taskImplementation = new ImplementationSnapshot(taskClass.getName(), classLoaderHierarchyHasher.getClassLoaderHash(taskClass.getClassLoader()));
        ImmutableList<ImplementationSnapshot> taskActionImplementations = collectActionImplementations(taskActions, classLoaderHierarchyHasher);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", task, taskImplementation);
            LOGGER.debug("Action implementations for {}: {}", task, taskActionImplementations);
        }

        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = previousExecution == null ? ImmutableSortedMap.<String, ValueSnapshot>of() : previousExecution.getInputProperties();
        ImmutableSortedMap<String, ValueSnapshot> inputProperties = snapshotTaskInputProperties(task, taskProperties, previousInputProperties, valueSnapshotter);

        ImmutableSortedSet<String> outputPropertyNames = getOutputPropertyNamesForCacheKey(taskProperties);
        ImmutableSet<String> declaredOutputFilePaths = getDeclaredOutputFilePaths(taskProperties, stringInterner);

        ImmutableSortedMap<String, FileCollectionSnapshot> inputFiles = snapshotTaskFiles(task, "Input", normalizationStrategy, taskProperties.getInputFileProperties(), snapshotterRegistry);

        ImmutableSortedMap<String, FileCollectionSnapshot> outputFiles = snapshotTaskFiles(task, "Output", normalizationStrategy, taskProperties.getOutputFileProperties(), snapshotterRegistry);

        OverlappingOutputs overlappingOutputs = detectOverlappingOutputs(outputFiles, previousExecution);

        return new CurrentTaskExecution(
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            outputPropertyNames,
            declaredOutputFilePaths,
            inputFiles,
            outputFiles,
            overlappingOutputs
        );
    }

    private void updateExecution(@Nullable final HistoricalTaskExecution previousExecution, CurrentTaskExecution currentExecution, TaskInternal task, TaskProperties taskProperties, InputNormalizationStrategy normalizationStrategy) {
        final ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesAfter = snapshotTaskFiles(task, "Output", normalizationStrategy, taskProperties.getOutputFileProperties(), snapshotterRegistry);

        ImmutableSortedMap<String, FileCollectionSnapshot> newOutputSnapshot;
        if (currentExecution.getDetectedOverlappingOutputs() == null) {
            newOutputSnapshot = outputFilesAfter;
        } else {
            newOutputSnapshot = ImmutableSortedMap.copyOfSorted(Maps.transformEntries(currentExecution.getOutputFilesSnapshot(), new Maps.EntryTransformer<String, FileCollectionSnapshot, FileCollectionSnapshot>() {
                @Override
                @SuppressWarnings("NullableProblems")
                public FileCollectionSnapshot transformEntry(String propertyName, FileCollectionSnapshot beforeExecution) {
                    FileCollectionSnapshot afterExecution = outputFilesAfter.get(propertyName);
                    FileCollectionSnapshot afterPreviousExecution = getSnapshotAfterPreviousExecution(previousExecution, propertyName);
                    return filterOutputSnapshot(afterPreviousExecution, beforeExecution, afterExecution);
                }
            }));
        }
        updateExecution(currentExecution, task, newOutputSnapshot);
    }

    private void updateExecution(CurrentTaskExecution currentExecution, TaskInternal task, ImmutableSortedMap<String, FileCollectionSnapshot> newOutputSnapshot) {
        currentExecution.setSuccessful(task.getState().getFailure() == null);
        currentExecution.setOutputFilesSnapshot(newOutputSnapshot);
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
        Map<String, PhysicalSnapshot> beforeExecutionSnapshots = getAllSnapshots(beforeExecution);
        Map<String, PhysicalSnapshot> afterExecutionSnapshots = getAllSnapshots(afterExecution);
        if (!beforeExecutionSnapshots.isEmpty() && !afterExecutionSnapshots.isEmpty()) {
            Map<String, NormalizedFileSnapshot> afterPreviousSnapshots = afterPreviousExecution != null ? afterPreviousExecution.getSnapshots() : ImmutableMap.<String, NormalizedFileSnapshot>of();
            int newEntryCount = 0;
            ImmutableMap.Builder<String, NormalizedFileSnapshot> outputEntries = ImmutableMap.builder();

            for (PhysicalSnapshot snapshot : afterExecutionSnapshots.values()) {
                if (isOutputEntry(snapshot, beforeExecutionSnapshots, afterPreviousSnapshots)) {
                    outputEntries.put(snapshot.getAbsolutePath(), afterExecution.getSnapshots().get(snapshot.getAbsolutePath()));
                    newEntryCount++;
                }
            }
            // Are all files snapshot after execution accounted for as new entries?
            if (newEntryCount == afterExecution.getSnapshots().size()) {
                filesSnapshot = afterExecution;
            } else if (newEntryCount == 0) {
                filesSnapshot = EmptyFileCollectionSnapshot.INSTANCE;
            } else {
                filesSnapshot = new DefaultFileCollectionFingerprint(FingerprintCompareStrategy.ABSOLUTE, outputEntries.build(), null);
            }
        } else {
            filesSnapshot = afterExecution;
        }
        return filesSnapshot;
    }

    private static Map<String, PhysicalSnapshot> getAllSnapshots(FileCollectionSnapshot fingerprint) {
        GetAllSnapshotsVisitor afterExecutionVisitor = new GetAllSnapshotsVisitor();
        fingerprint.visitRoots(afterExecutionVisitor);
        return afterExecutionVisitor.getSnapshots();
    }

    /**
     * Decide whether an entry should be considered to be part of the output. Entries that are considered outputs are:
     * <ul>
     * <li>an entry that did not exist before the execution, but exists after the execution</li>
     * <li>an entry that did exist before the execution, and has been changed during the execution</li>
     * <li>an entry that did wasn't changed during the execution, but was already considered an output during the previous execution</li>
     * </ul>
     */
    private static boolean isOutputEntry(PhysicalSnapshot fileSnapshot, Map<String, PhysicalSnapshot> beforeSnapshots, Map<String, NormalizedFileSnapshot> afterPreviousSnapshots) {
        if (fileSnapshot.getType() == FileType.Missing) {
            return false;
        }
        PhysicalSnapshot beforeSnapshot = beforeSnapshots.get(fileSnapshot.getAbsolutePath());
        // Was it created during execution?
        if (beforeSnapshot == null) {
            return true;
        }
        // Was it updated during execution?
        if (!fileSnapshot.isContentAndMetadataUpToDate(beforeSnapshot)) {
            return true;
        }
        // Did we already consider it as an output after the previous execution?
        return afterPreviousSnapshots.containsKey(fileSnapshot.getAbsolutePath());
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

    private static ImmutableSortedMap<String, ValueSnapshot> snapshotTaskInputProperties(TaskInternal task, TaskProperties taskProperties, ImmutableSortedMap<String, ValueSnapshot> previousInputProperties, ValueSnapshotter valueSnapshotter) {
        ImmutableSortedMap.Builder<String, ValueSnapshot> builder = ImmutableSortedMap.naturalOrder();
        Map<String, Object> inputPropertyValues = taskProperties.getInputPropertyValues().create();
        assert inputPropertyValues != null;
        for (Map.Entry<String, Object> entry : inputPropertyValues.entrySet()) {
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
        return EmptyFileCollectionSnapshot.INSTANCE;
    }

    @Nullable
    private HistoricalTaskExecution loadPreviousExecution(TaskInternal task) {
        return taskHistoryCache.get(task.getPath());
    }

    private static ImmutableSortedSet<String> getOutputPropertyNamesForCacheKey(TaskProperties taskProperties) {
        ImmutableSortedSet<TaskOutputFilePropertySpec> fileProperties = taskProperties.getOutputFileProperties();
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

    private static ImmutableSet<String> getDeclaredOutputFilePaths(final TaskProperties taskProperties, final StringInterner stringInterner) {
        final ImmutableSet.Builder<String> declaredOutputFilePaths = ImmutableSortedSet.naturalOrder();
        FileCollectionInternal outputFiles = (FileCollectionInternal) taskProperties.getOutputFiles();
        outputFiles.visitRootElements(new FileCollectionVisitor() {
            @Override
            public void visitCollection(FileCollectionInternal fileCollection) {
                addAllPaths(fileCollection, declaredOutputFilePaths, stringInterner);
            }

            @Override
            public void visitTree(FileTreeInternal fileTree) {
                DeprecationLogger.nagUserOfDeprecated("Adding file trees which are not directory trees as output files");
                addAllPaths(fileTree, declaredOutputFilePaths, stringInterner);
            }

            @Override
            public void visitDirectoryTree(DirectoryFileTree directoryTree) {
                addPath(directoryTree.getDir(), declaredOutputFilePaths, stringInterner);
            }
        });
        return declaredOutputFilePaths.build();
    }

    private static void addAllPaths(Iterable<File> files, ImmutableSet.Builder<String> builder, StringInterner stringInterner) {
        for (File file : files) {
            addPath(file, builder, stringInterner);
        }
    }

    private static void addPath(File file, ImmutableSet.Builder<String> builder, StringInterner stringInterner) {
        builder.add(stringInterner.intern(file.getAbsolutePath()));
    }

    private static class GetAllSnapshotsVisitor implements PhysicalSnapshotVisitor {
        private final Map<String, PhysicalSnapshot> snapshots = new HashMap<String, PhysicalSnapshot>();

        @Override
        public boolean preVisitDirectory(PhysicalSnapshot directorySnapshot) {
            snapshots.put(directorySnapshot.getAbsolutePath(), directorySnapshot);
            return true;
        }

        @Override
        public void visit(PhysicalSnapshot fileSnapshot) {
            snapshots.put(fileSnapshot.getAbsolutePath(), fileSnapshot);
        }

        @Override
        public void postVisitDirectory() {
        }

        public Map<String, PhysicalSnapshot> getSnapshots() {
            return snapshots;
        }
    }
}
