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

package org.gradle.internal.execution.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor;
import org.gradle.internal.snapshot.FileSystemLeafSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

import static org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.EXCLUDE_EMPTY_DIRS;
import static org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.INCLUDE_EMPTY_DIRS;

/**
 * Filters out fingerprints that are not considered outputs. Entries that are considered outputs are:
 * <ul>
 * <li>an entry that did not exist before the execution, but exists after the execution</li>
 * <li>an entry that did exist before the execution, and has been changed during the execution</li>
 * <li>an entry that did wasn't changed during the execution, but was already considered an output during the previous execution</li>
 * </ul>
 */
public class OutputFilterUtil {

    public static ImmutableList<FileSystemSnapshot> filterOutputSnapshotBeforeExecution(FileCollectionFingerprint afterLastExecutionFingerprint, FileSystemSnapshot beforeExecutionOutputSnapshot) {
        Map<String, FileSystemLocationFingerprint> fingerprints = afterLastExecutionFingerprint.getFingerprints();
        SnapshotFilteringVisitor filteringVisitor = new SnapshotFilteringVisitor((snapshot, isRoot) ->
            (!isRoot || snapshot.getType() != FileType.Missing)
                && fingerprints.containsKey(snapshot.getAbsolutePath())
        );
        beforeExecutionOutputSnapshot.accept(filteringVisitor);
        return filteringVisitor.getNewRoots();
    }

    public static ImmutableList<FileSystemSnapshot> filterOutputSnapshotAfterExecution(@Nullable FileCollectionFingerprint afterLastExecutionFingerprint, FileSystemSnapshot beforeExecutionOutputSnapshot, FileSystemSnapshot afterExecutionOutputSnapshot) {
        Map<String, CompleteFileSystemLocationSnapshot> beforeExecutionSnapshots = getAllSnapshots(beforeExecutionOutputSnapshot);
        if (beforeExecutionSnapshots.isEmpty()) {
            return ImmutableList.of(afterExecutionOutputSnapshot);
        }

        Map<String, FileSystemLocationFingerprint> afterLastExecutionFingerprints = afterLastExecutionFingerprint != null
            ? afterLastExecutionFingerprint.getFingerprints()
            : ImmutableMap.of();

        SnapshotFilteringVisitor filteringVisitor = new SnapshotFilteringVisitor((afterExecutionSnapshot, isRoot) ->
            isOutputEntry(afterLastExecutionFingerprints, beforeExecutionSnapshots, afterExecutionSnapshot, isRoot)
        );
        afterExecutionOutputSnapshot.accept(filteringVisitor);

        // Are all file snapshots after execution accounted for as new entries?
        if (filteringVisitor.hasBeenFiltered()) {
            return filteringVisitor.getNewRoots();
        } else {
            return ImmutableList.of(afterExecutionOutputSnapshot);
        }
    }

    private static Map<String, CompleteFileSystemLocationSnapshot> getAllSnapshots(FileSystemSnapshot fingerprint) {
        GetAllSnapshotsVisitor allSnapshotsVisitor = new GetAllSnapshotsVisitor();
        fingerprint.accept(allSnapshotsVisitor);
        return allSnapshotsVisitor.getSnapshots();
    }

    /**
     * Decide whether an entry should be considered to be part of the output. See class Javadoc for definition of what is considered output.
     */
    private static boolean isOutputEntry(Map<String, FileSystemLocationFingerprint> afterPreviousExecutionFingerprints, Map<String, CompleteFileSystemLocationSnapshot> beforeExecutionSnapshots, CompleteFileSystemLocationSnapshot afterExecutionSnapshot, Boolean isRoot) {
        if (isRoot) {
            switch (afterExecutionSnapshot.getType()) {
                case Missing:
                    return false;
                case Directory:
                    return true;
                default:
                    // continue
                    break;
            }
        }
        CompleteFileSystemLocationSnapshot beforeSnapshot = beforeExecutionSnapshots.get(afterExecutionSnapshot.getAbsolutePath());
        // Was it created during execution?
        if (beforeSnapshot == null) {
            return true;
        }
        // Was it updated during execution?
        if (!afterExecutionSnapshot.isContentAndMetadataUpToDate(beforeSnapshot)) {
            return true;
        }
        // Did we already consider it as an output after the previous execution?
        return afterPreviousExecutionFingerprints.containsKey(afterExecutionSnapshot.getAbsolutePath());
    }

    private static class GetAllSnapshotsVisitor implements FileSystemSnapshotHierarchyVisitor {
        private final Map<String, CompleteFileSystemLocationSnapshot> snapshots = new HashMap<>();

        @Override
        public SnapshotVisitResult visitEntry(CompleteFileSystemLocationSnapshot snapshot) {
            snapshots.put(snapshot.getAbsolutePath(), snapshot);
            return SnapshotVisitResult.CONTINUE;
        }

        public Map<String, CompleteFileSystemLocationSnapshot> getSnapshots() {
            return snapshots;
        }
    }

    private static class SnapshotFilteringVisitor extends RootTrackingFileSystemSnapshotHierarchyVisitor {
        private final BiPredicate<CompleteFileSystemLocationSnapshot, Boolean> predicate;
        private final ImmutableList.Builder<FileSystemSnapshot> newRootsBuilder = ImmutableList.builder();

        private boolean hasBeenFiltered;
        private MerkleDirectorySnapshotBuilder merkleBuilder;
        private boolean currentRootFiltered;
        private CompleteDirectorySnapshot currentRoot;

        public SnapshotFilteringVisitor(BiPredicate<CompleteFileSystemLocationSnapshot, Boolean> predicate) {
            this.predicate = predicate;
        }

        @Override
        public void enterDirectory(CompleteDirectorySnapshot directorySnapshot, boolean isRoot) {
            boolean isOutputDir = predicate.test(directorySnapshot, isRoot);
            EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy = isOutputDir
                ? INCLUDE_EMPTY_DIRS
                : EXCLUDE_EMPTY_DIRS;
            merkleBuilder.enterDirectory(directorySnapshot, emptyDirectoryHandlingStrategy);
        }

        @Override
        public SnapshotVisitResult visitEntry(CompleteFileSystemLocationSnapshot snapshot, boolean isRoot) {
            snapshot.accept(new FileSystemLocationSnapshotVisitor() {
                @Override
                public void visitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                    if (merkleBuilder == null) {
                        merkleBuilder = MerkleDirectorySnapshotBuilder.noSortingRequired();
                        currentRoot = directorySnapshot;
                        currentRootFiltered = false;
                    }
                }

                @Override
                public void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                    visitNonDirectoryEntry(snapshot, isRoot);
                }

                @Override
                public void visitMissing(MissingFileSnapshot missingSnapshot) {
                    visitNonDirectoryEntry(snapshot, isRoot);
                }
            });
            return SnapshotVisitResult.CONTINUE;
        }

        private void visitNonDirectoryEntry(CompleteFileSystemLocationSnapshot snapshot, boolean isRoot) {
            if (!predicate.test(snapshot, isRoot)) {
                hasBeenFiltered = true;
                currentRootFiltered = true;
                return;
            }
            if (merkleBuilder == null) {
                newRootsBuilder.add(snapshot);
            } else {
                if (snapshot instanceof FileSystemLeafSnapshot) {
                    merkleBuilder.visitLeafElement((FileSystemLeafSnapshot) snapshot);
                }
            }
        }

        @Override
        public void leaveDirectory(CompleteDirectorySnapshot directorySnapshot, boolean isRoot) {
            boolean includedDir = merkleBuilder.leaveDirectory();
            if (!includedDir) {
                currentRootFiltered = true;
                hasBeenFiltered = true;
            }
            if (isRoot) {
                CompleteFileSystemLocationSnapshot result = merkleBuilder.getResult();
                if (result != null) {
                    newRootsBuilder.add(currentRootFiltered ? result : currentRoot);
                }
                merkleBuilder = null;
                currentRoot = null;
            }
        }

        public ImmutableList<FileSystemSnapshot> getNewRoots() {
            return newRootsBuilder.build();
        }

        public boolean hasBeenFiltered() {
            return hasBeenFiltered;
        }
    }
}
