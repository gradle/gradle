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
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.HistoricalFileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.EmptyHistoricalFileCollectionFingerprint;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

@NonNullApi
public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheBackedTaskHistoryRepository.class);

    private final TaskHistoryCache taskHistoryCache;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ValueSnapshotter valueSnapshotter;
    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;

    public CacheBackedTaskHistoryRepository(
        TaskHistoryCache taskHistoryCache,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        FileCollectionFingerprinterRegistry fingerprinterRegistry
    ) {
        this.taskHistoryCache = taskHistoryCache;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.valueSnapshotter = valueSnapshotter;
        this.fingerprinterRegistry = fingerprinterRegistry;
    }

    @Override
    public History getHistory(final TaskInternal task, final TaskProperties taskProperties) {
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
                    currentExecution = createExecution(task, taskProperties, getPreviousExecution());
                }
                return currentExecution;
            }

            @Override
            public void updateCurrentExecution() {
                updateExecution(getPreviousExecution(), getCurrentExecution(), task, taskProperties);
            }

            @Override
            public void updateCurrentExecutionWithOutputs(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputSnapshot) {
                updateExecution(getCurrentExecution(), task, newOutputSnapshot);
            }

            @Override
            public void persist() {
                taskHistoryCache.put(task.getPath(), getCurrentExecution().archive());
            }

        };
    }

    private CurrentTaskExecution createExecution(TaskInternal task, TaskProperties taskProperties, @Nullable HistoricalTaskExecution previousExecution) {
        Class<? extends TaskInternal> taskClass = task.getClass();
        List<ContextAwareTaskAction> taskActions = task.getTaskActions();
        ImplementationSnapshot taskImplementation = ImplementationSnapshot.of(taskClass, classLoaderHierarchyHasher);
        ImmutableList<ImplementationSnapshot> taskActionImplementations = collectActionImplementations(taskActions, classLoaderHierarchyHasher);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", task, taskImplementation);
            LOGGER.debug("Action implementations for {}: {}", task, taskActionImplementations);
        }

        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = previousExecution == null ? ImmutableSortedMap.<String, ValueSnapshot>of() : previousExecution.getInputProperties();
        ImmutableSortedMap<String, ValueSnapshot> inputProperties = snapshotTaskInputProperties(task, taskProperties, previousInputProperties, valueSnapshotter);

        ImmutableSortedSet<String> outputPropertyNames = getOutputPropertyNamesForCacheKey(taskProperties);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles = fingerprintTaskFiles(task, "Input", taskProperties.getInputFileProperties(), fingerprinterRegistry);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFiles = fingerprintTaskFiles(task, "Output", taskProperties.getOutputFileProperties(), fingerprinterRegistry);

        OverlappingOutputs overlappingOutputs = detectOverlappingOutputs(outputFiles, previousExecution);

        return new CurrentTaskExecution(
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            outputPropertyNames,
            inputFiles,
            outputFiles,
            overlappingOutputs
        );
    }

    private void updateExecution(@Nullable final HistoricalTaskExecution previousExecution, CurrentTaskExecution currentExecution, TaskInternal task, TaskProperties taskProperties) {
        final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFilesAfter = fingerprintTaskFiles(task, "Output", taskProperties.getOutputFileProperties(), fingerprinterRegistry);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprint;
        if (currentExecution.getDetectedOverlappingOutputs() == null) {
            newOutputFingerprint = outputFilesAfter;
        } else {
            newOutputFingerprint = ImmutableSortedMap.copyOfSorted(Maps.transformEntries(currentExecution.getOutputFingerprints(), new Maps.EntryTransformer<String, CurrentFileCollectionFingerprint, CurrentFileCollectionFingerprint>() {
                @Override
                @SuppressWarnings("NullableProblems")
                public CurrentFileCollectionFingerprint transformEntry(String propertyName, CurrentFileCollectionFingerprint beforeExecution) {
                    CurrentFileCollectionFingerprint afterExecution = outputFilesAfter.get(propertyName);
                    HistoricalFileCollectionFingerprint afterPreviousExecution = getFingerprintAfterPreviousExecution(previousExecution, propertyName);
                    return filterOutputFingerprint(afterPreviousExecution, beforeExecution, afterExecution);
                }
            }));
        }
        updateExecution(currentExecution, task, newOutputFingerprint);
    }

    private void updateExecution(CurrentTaskExecution currentExecution, TaskInternal task, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprint) {
        currentExecution.setSuccessful(task.getState().getFailure() == null);
        currentExecution.setOutputFingerprintsAfterExecution(newOutputFingerprint);
    }

    /**
     * Returns a new fingerprint that filters out entries that should not be considered outputs of the task.
     */
    private static CurrentFileCollectionFingerprint filterOutputFingerprint(
        @Nullable HistoricalFileCollectionFingerprint afterPreviousExecution,
        CurrentFileCollectionFingerprint beforeExecution,
        CurrentFileCollectionFingerprint afterExecution
    ) {
        CurrentFileCollectionFingerprint filesFingerprint;
        final Map<String, FileSystemLocationSnapshot> beforeExecutionSnapshots = getAllSnapshots(beforeExecution);
        if (!beforeExecution.getFingerprints().isEmpty() && !afterExecution.getFingerprints().isEmpty()) {
            final Map<String, FileSystemLocationFingerprint> afterPreviousFingerprints = afterPreviousExecution != null ? afterPreviousExecution.getFingerprints() : ImmutableMap.<String, FileSystemLocationFingerprint>of();

            final List<FileSystemSnapshot> newRoots = new ArrayList<FileSystemSnapshot>();
            final MutableBoolean hasBeenFiltered = new MutableBoolean(false);

            afterExecution.accept(new FileSystemSnapshotVisitor() {
                private MerkleDirectorySnapshotBuilder merkleBuilder;
                private boolean currentRootFiltered = false;
                private DirectorySnapshot currentRoot;

                @Override
                public boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                    if (merkleBuilder == null) {
                        merkleBuilder = MerkleDirectorySnapshotBuilder.noSortingRequired();
                        currentRoot = directorySnapshot;
                        currentRootFiltered = false;
                    }
                    merkleBuilder.preVisitDirectory(directorySnapshot);
                    return true;
                }

                @Override
                public void visit(FileSystemLocationSnapshot fileSnapshot) {
                    if (!isOutputEntry(fileSnapshot, beforeExecutionSnapshots, afterPreviousFingerprints)) {
                        hasBeenFiltered.set(true);
                        currentRootFiltered = true;
                        return;
                    }
                    if (merkleBuilder == null) {
                        newRoots.add(fileSnapshot);
                    } else {
                        merkleBuilder.visit(fileSnapshot);
                    }
                }

                @Override
                public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
                    boolean isOutputDir = isOutputEntry(directorySnapshot, beforeExecutionSnapshots, afterPreviousFingerprints);
                    boolean includedDir = merkleBuilder.postVisitDirectory(isOutputDir);
                    if (!includedDir) {
                        currentRootFiltered = true;
                        hasBeenFiltered.set(true);
                    }
                    if (merkleBuilder.isRoot()) {
                        FileSystemLocationSnapshot result = merkleBuilder.getResult();
                        if (result != null) {
                            newRoots.add(currentRootFiltered ? result : currentRoot);
                        }
                        merkleBuilder = null;
                        currentRoot = null;
                    }
                }
            });


            // Are all file snapshots after execution accounted for as new entries?
            if (!hasBeenFiltered.get()) {
                filesFingerprint = afterExecution;
            } else {
                filesFingerprint = DefaultCurrentFileCollectionFingerprint.from(newRoots, AbsolutePathFingerprintingStrategy.IGNORE_MISSING);
            }
        } else {
            filesFingerprint = afterExecution;
        }
        return filesFingerprint;
    }

    private static Map<String, FileSystemLocationSnapshot> getAllSnapshots(CurrentFileCollectionFingerprint fingerprint) {
        GetAllSnapshotsVisitor afterExecutionVisitor = new GetAllSnapshotsVisitor();
        fingerprint.accept(afterExecutionVisitor);
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
    private static boolean isOutputEntry(FileSystemLocationSnapshot snapshot, Map<String, FileSystemLocationSnapshot> beforeSnapshots, Map<String, FileSystemLocationFingerprint> afterPreviousFingerprints) {
        if (snapshot.getType() == FileType.Missing) {
            return false;
        }
        FileSystemLocationSnapshot beforeSnapshot = beforeSnapshots.get(snapshot.getAbsolutePath());
        // Was it created during execution?
        if (beforeSnapshot == null) {
            return true;
        }
        // Was it updated during execution?
        if (!snapshot.isContentAndMetadataUpToDate(beforeSnapshot)) {
            return true;
        }
        // Did we already consider it as an output after the previous execution?
        return afterPreviousFingerprints.containsKey(snapshot.getAbsolutePath());
    }

    private static ImmutableList<ImplementationSnapshot> collectActionImplementations(Collection<ContextAwareTaskAction> taskActions, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        if (taskActions.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<ImplementationSnapshot> actionImplementations = ImmutableList.builder();
        for (ContextAwareTaskAction taskAction : taskActions) {
            actionImplementations.add(taskAction.getActionImplementation(classLoaderHierarchyHasher));
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
    static ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintTaskFiles(TaskInternal task, String title, SortedSet<? extends TaskFilePropertySpec> fileProperties, FileCollectionFingerprinterRegistry fingerprinterRegistry) {
        ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> builder = ImmutableSortedMap.naturalOrder();
        for (TaskFilePropertySpec propertySpec : fileProperties) {
            CurrentFileCollectionFingerprint result;
            FileCollectionFingerprinter fingerprinter = fingerprinterRegistry.getFingerprinter(propertySpec.getNormalizer());
            LOGGER.debug("Fingerprinting property {} for {}", propertySpec, task);
            result = fingerprinter.fingerprint(propertySpec.getPropertyFiles());
            builder.put(propertySpec.getPropertyName(), result);
        }
        return builder.build();
    }

    @Nullable
    private static OverlappingOutputs detectOverlappingOutputs(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> taskOutputs, @Nullable HistoricalTaskExecution previousExecution) {
        for (Map.Entry<String, CurrentFileCollectionFingerprint> entry : taskOutputs.entrySet()) {
            String propertyName = entry.getKey();
            FileCollectionFingerprint beforeExecution = entry.getValue();
            HistoricalFileCollectionFingerprint afterPreviousExecution = getFingerprintAfterPreviousExecution(previousExecution, propertyName);
            OverlappingOutputs overlappingOutputs = OverlappingOutputs.detect(propertyName, afterPreviousExecution, beforeExecution);
            if (overlappingOutputs != null) {
                return overlappingOutputs;
            }
        }
        return null;
    }

    private static HistoricalFileCollectionFingerprint getFingerprintAfterPreviousExecution(@Nullable HistoricalTaskExecution previousExecution, String propertyName) {
        if (previousExecution != null) {
            Map<String, HistoricalFileCollectionFingerprint> previousFingerprints = previousExecution.getOutputFingerprints();
            HistoricalFileCollectionFingerprint afterPreviousExecution = previousFingerprints.get(propertyName);
            if (afterPreviousExecution != null) {
                return afterPreviousExecution;
            }
        }
        return EmptyHistoricalFileCollectionFingerprint.INSTANCE;
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

    private static class GetAllSnapshotsVisitor implements FileSystemSnapshotVisitor {
        private final Map<String, FileSystemLocationSnapshot> snapshots = new HashMap<String, FileSystemLocationSnapshot>();

        @Override
        public boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
            snapshots.put(directorySnapshot.getAbsolutePath(), directorySnapshot);
            return true;
        }

        @Override
        public void visit(FileSystemLocationSnapshot fileSnapshot) {
            snapshots.put(fileSnapshot.getAbsolutePath(), fileSnapshot);
        }

        @Override
        public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
        }

        public Map<String, FileSystemLocationSnapshot> getSnapshots() {
            return snapshots;
        }
    }
}
