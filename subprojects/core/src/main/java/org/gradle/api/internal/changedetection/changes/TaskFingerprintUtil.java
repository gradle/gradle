/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.changes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

public class TaskFingerprintUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskFingerprintUtil.class);

    public static ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintAfterOutputsGenerated(
        final @Nullable ImmutableSortedMap<String, FileCollectionFingerprint> previous,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current,
        SortedSet<? extends TaskFilePropertySpec> outputProperties,
        boolean hasOverlappingOutputs,
        TaskInternal task,
        FileCollectionFingerprinterRegistry fingerprinterRegistry
    ) {
        final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFilesAfter = fingerprintTaskFiles(task, outputProperties, fingerprinterRegistry);

        if (!hasOverlappingOutputs) {
            return outputFilesAfter;
        } else {
            return ImmutableSortedMap.copyOfSorted(Maps.transformEntries(current, new Maps.EntryTransformer<String, CurrentFileCollectionFingerprint, CurrentFileCollectionFingerprint>() {
                @Override
                @SuppressWarnings("NullableProblems")
                public CurrentFileCollectionFingerprint transformEntry(String propertyName, CurrentFileCollectionFingerprint beforeExecution) {
                    CurrentFileCollectionFingerprint afterExecution = outputFilesAfter.get(propertyName);
                    FileCollectionFingerprint afterPreviousExecution = TaskFingerprintUtil.getFingerprintAfterPreviousExecution(previous, propertyName);
                    return TaskFingerprintUtil.filterOutputFingerprint(afterPreviousExecution, beforeExecution, afterExecution);
                }
            }));
        }
    }

    public static ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintTaskFiles(TaskInternal task, SortedSet<? extends TaskFilePropertySpec> fileProperties, FileCollectionFingerprinterRegistry fingerprinterRegistry) {
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

    private static FileCollectionFingerprint getFingerprintAfterPreviousExecution(@Nullable ImmutableSortedMap<String, FileCollectionFingerprint> previous, String propertyName) {
        if (previous != null) {
            FileCollectionFingerprint afterPreviousExecution = previous.get(propertyName);
            if (afterPreviousExecution != null) {
                return afterPreviousExecution;
            }
        }
        return FileCollectionFingerprint.EMPTY;
    }

    /**
     * Returns a new fingerprint that filters out entries that should not be considered outputs of the task.
     */
    public static CurrentFileCollectionFingerprint filterOutputFingerprint(
            @Nullable FileCollectionFingerprint afterPreviousExecution,
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
