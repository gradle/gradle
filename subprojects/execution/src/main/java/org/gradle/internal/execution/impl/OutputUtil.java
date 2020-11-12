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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor;
import org.gradle.internal.snapshot.CompositeFileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemLeafSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static com.google.common.collect.ImmutableSortedMap.copyOfSorted;
import static com.google.common.collect.Maps.transformEntries;
import static org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.EXCLUDE_EMPTY_DIRS;
import static org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.INCLUDE_EMPTY_DIRS;
import static org.gradle.internal.snapshot.SnapshotUtil.index;

public class OutputUtil {

    /**
     * Filters out snapshots that are not considered outputs. Entries that are considered outputs are:
     * <ul>
     * <li>an entry that did not exist before the execution, but exists after the execution</li>
     * <li>an entry that did exist before the execution, and has been changed during the execution</li>
     * <li>an entry that did wasn't changed during the execution, but was already considered an output during the previous execution</li>
     * </ul>
     */
    public static ImmutableSortedMap<String, FileSystemSnapshot> filterOutputsWithOverlapBeforeExecution(
        ImmutableSortedMap<String, FileSystemSnapshot> previousSnapshots,
        ImmutableSortedMap<String, FileSystemSnapshot> unfilteredBeforeExecutionSnapshots
    ) {
        return ImmutableSortedMap.copyOfSorted(
            Maps.transformEntries(unfilteredBeforeExecutionSnapshots, (key, unfilteredBeforeExecution) -> {
                    FileSystemSnapshot previous = previousSnapshots.get(key);
                    if (previous == null) {
                        return FileSystemSnapshot.EMPTY;
                    }
                    //noinspection ConstantConditions
                    return filterOutputWithOverlapBeforeExecution(previous, unfilteredBeforeExecution);
                }
            )
        );
    }

    @VisibleForTesting
    static FileSystemSnapshot filterOutputWithOverlapBeforeExecution(FileSystemSnapshot previous, FileSystemSnapshot beforeExecution) {
        Map<String, CompleteFileSystemLocationSnapshot> beforeExecutionIndex = index(previous);
        SnapshotFilteringVisitor filteringVisitor = new SnapshotFilteringVisitor((snapshot, isRoot) ->
            // TODO The first part of this condition is not needed, right?
            (!isRoot || snapshot.getType() != FileType.Missing)
                && beforeExecutionIndex.containsKey(snapshot.getAbsolutePath())
        );
        beforeExecution.accept(filteringVisitor);
        return CompositeFileSystemSnapshot.of(filteringVisitor.getNewRoots());
    }

    /**
     * Filters out snapshots that are not considered outputs. Entries that are considered outputs are:
     * <ul>
     * <li>an entry that did not exist before the execution, but exists after the execution</li>
     * <li>an entry that did exist before the execution, and has been changed during the execution</li>
     * <li>an entry that did wasn't changed during the execution, but was already considered an output during the previous execution</li>
     * </ul>
     */
    public static ImmutableSortedMap<String, FileSystemSnapshot> filterOutputsWithOverlapAfterExecution(
        ImmutableSortedMap<String, FileSystemSnapshot> previousSnapshots,
        ImmutableSortedMap<String, FileSystemSnapshot> unfilteredBeforeExecutionSnapshots,
        ImmutableSortedMap<String, FileSystemSnapshot> unfilteredAfterExecutionSnapshots
    ) {
        return copyOfSorted(transformEntries(
            unfilteredAfterExecutionSnapshots,
            (propertyName, unfilteredAfterExecution) -> {
                // This can never be null as it comes from an ImmutableMap's value
                assert unfilteredAfterExecution != null;

                FileSystemSnapshot previous = previousSnapshots.get(propertyName);
                FileSystemSnapshot unfilteredBeforeExecution = unfilteredBeforeExecutionSnapshots.get(propertyName);
                return filterOutputWithOverlapAfterExecution(previous, unfilteredBeforeExecution, unfilteredAfterExecution);
            }
        ));
    }

    @VisibleForTesting
    static FileSystemSnapshot filterOutputWithOverlapAfterExecution(@Nullable FileSystemSnapshot previous, FileSystemSnapshot unfilteredBeforeExecution, FileSystemSnapshot unfilteredAfterExecution) {
        Map<String, CompleteFileSystemLocationSnapshot> beforeExecutionIndex = index(unfilteredBeforeExecution);
        if (beforeExecutionIndex.isEmpty()) {
            return unfilteredAfterExecution;
        }

        Map<String, CompleteFileSystemLocationSnapshot> previousIndex = previous != null
            ? index(previous)
            : ImmutableMap.of();

        SnapshotFilteringVisitor filteringVisitor = new SnapshotFilteringVisitor((afterExecutionSnapshot, isRoot) ->
            isOutputEntry(previousIndex.keySet(), beforeExecutionIndex, afterExecutionSnapshot, isRoot)
        );
        unfilteredAfterExecution.accept(filteringVisitor);

        // Are all file snapshots after execution accounted for as new entries?
        if (filteringVisitor.hasBeenFiltered()) {
            return CompositeFileSystemSnapshot.of(filteringVisitor.getNewRoots());
        } else {
            return unfilteredAfterExecution;
        }
    }

    private static boolean isOutputEntry(Set<String> afterPreviousExecutionLocations, Map<String, CompleteFileSystemLocationSnapshot> beforeExecutionSnapshots, CompleteFileSystemLocationSnapshot afterExecutionSnapshot, Boolean isRoot) {
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
        return afterPreviousExecutionLocations.contains(afterExecutionSnapshot.getAbsolutePath());
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
