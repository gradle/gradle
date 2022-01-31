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

package org.gradle.internal.execution.history.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import org.gradle.internal.snapshot.CompositeFileSystemSnapshot;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.DirectorySnapshotBuilder;
import org.gradle.internal.snapshot.DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy;
import org.gradle.internal.snapshot.FileSystemLeafSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
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
import static org.gradle.internal.snapshot.DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.EXCLUDE_EMPTY_DIRS;
import static org.gradle.internal.snapshot.DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.INCLUDE_EMPTY_DIRS;
import static org.gradle.internal.snapshot.SnapshotUtil.index;

public class OutputSnapshotUtil {

    /**
     * Finds outputs that are still present since the last execution when overlapping outputs are present.
     *
     * Note: when there are no overlapping outputs, all outputs currently existing in the output locations
     * are considered outputs of the work.
     *
     * If a property did not exist after the previous execution then all the outputs for it will be ignored.
     *
     * @param previousSnapshots snapshots of the outputs produced by the work after the previous execution, indexed by property name.
     * @param unfilteredBeforeExecutionSnapshots snapshots of the outputs currently present in the work's output locations, indexed by property name.
     */
    public static ImmutableSortedMap<String, FileSystemSnapshot> findOutputsStillPresentSincePreviousExecution(
        ImmutableSortedMap<String, FileSystemSnapshot> previousSnapshots,
        ImmutableSortedMap<String, FileSystemSnapshot> unfilteredBeforeExecutionSnapshots
    ) {
        return ImmutableSortedMap.copyOfSorted(
            Maps.transformEntries(unfilteredBeforeExecutionSnapshots, (key, unfilteredBeforeExecution) -> {
                    FileSystemSnapshot previous = previousSnapshots.get(key);
                    // If the property was null before (can only happen for tasks with optional outputs)
                    if (previous == null) {
                        return FileSystemSnapshot.EMPTY;
                    }
                    //noinspection ConstantConditions
                    return findOutputPropertyStillPresentSincePreviousExecution(previous, unfilteredBeforeExecution);
                }
            )
        );
    }

    @VisibleForTesting
    static FileSystemSnapshot findOutputPropertyStillPresentSincePreviousExecution(FileSystemSnapshot previous, FileSystemSnapshot current) {
        Map<String, FileSystemLocationSnapshot> previousIndex = index(previous);
        return filterSnapshot(current, (currentSnapshot, isRoot) ->
            // Include only outputs that we already considered outputs after the previous execution
            previousIndex.containsKey(currentSnapshot.getAbsolutePath())
        );
    }

    /**
     * Filters out snapshots that are not considered outputs when overlapping outputs are present.
     *
     * Note: when there are no overlapping outputs, all outputs currently existing in the output locations
     * are considered outputs of the work.
     *
     * Entries that are considered outputs are:
     *
     * <ul>
     * <li>an entry that did not exist before the execution, but exists after the execution,</li>
     * <li>an entry that did exist before the execution, and has been changed during the execution,</li>
     * <li>an entry that wasn't changed during the execution, but was already considered an output during the previous execution.</li>
     * </ul>
     */
    public static ImmutableSortedMap<String, FileSystemSnapshot> filterOutputsAfterExecution(
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
                return filterOutputAfterExecution(previous, unfilteredBeforeExecution, unfilteredAfterExecution);
            }
        ));
    }

    @VisibleForTesting
    static FileSystemSnapshot filterOutputAfterExecution(@Nullable FileSystemSnapshot previous, FileSystemSnapshot unfilteredBeforeExecution, FileSystemSnapshot unfilteredAfterExecution) {
        Map<String, FileSystemLocationSnapshot> beforeExecutionIndex = index(unfilteredBeforeExecution);
        if (beforeExecutionIndex.isEmpty()) {
            return unfilteredAfterExecution;
        }

        Map<String, FileSystemLocationSnapshot> previousIndex = previous != null
            ? index(previous)
            : ImmutableMap.of();

        return filterSnapshot(unfilteredAfterExecution, (afterExecutionSnapshot, isRoot) ->
            isOutputEntry(previousIndex.keySet(), beforeExecutionIndex, afterExecutionSnapshot, isRoot)
        );
    }

    private static boolean isOutputEntry(Set<String> previousLocations, Map<String, FileSystemLocationSnapshot> beforeExecutionSnapshots, FileSystemLocationSnapshot afterExecutionSnapshot, Boolean isRoot) {
        // A root is always an output, even when it's missing or unchanged
        if (isRoot) {
            return true;
        }
        FileSystemLocationSnapshot beforeSnapshot = beforeExecutionSnapshots.get(afterExecutionSnapshot.getAbsolutePath());
        // Was it created during execution?
        if (beforeSnapshot == null) {
            return true;
        }
        // Was it updated during execution?
        if (!afterExecutionSnapshot.isContentAndMetadataUpToDate(beforeSnapshot)) {
            return true;
        }
        // Did we already consider it as an output after the previous execution?
        return previousLocations.contains(afterExecutionSnapshot.getAbsolutePath());
    }

    private static FileSystemSnapshot filterSnapshot(FileSystemSnapshot root, BiPredicate<FileSystemLocationSnapshot, Boolean> predicate) {
        SnapshotFilteringVisitor visitor = new SnapshotFilteringVisitor(predicate);
        root.accept(visitor);

        // Are all file snapshots after execution accounted for as new entries?
        if (visitor.hasBeenFiltered()) {
            return CompositeFileSystemSnapshot.of(visitor.getNewRoots());
        } else {
            return root;
        }
    }

    private static class SnapshotFilteringVisitor extends RootTrackingFileSystemSnapshotHierarchyVisitor {
        private final BiPredicate<FileSystemLocationSnapshot, Boolean> predicate;
        private final ImmutableList.Builder<FileSystemSnapshot> newRootsBuilder = ImmutableList.builder();

        private boolean hasBeenFiltered;
        private DirectorySnapshotBuilder directorySnapshotBuilder;
        private boolean currentRootFiltered;
        private DirectorySnapshot currentRoot;

        public SnapshotFilteringVisitor(BiPredicate<FileSystemLocationSnapshot, Boolean> predicate) {
            this.predicate = predicate;
        }

        @Override
        public void enterDirectory(DirectorySnapshot directorySnapshot, boolean isRoot) {
            boolean isOutputDir = predicate.test(directorySnapshot, isRoot);
            EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy = isOutputDir
                ? INCLUDE_EMPTY_DIRS
                : EXCLUDE_EMPTY_DIRS;
            directorySnapshotBuilder.enterDirectory(directorySnapshot, emptyDirectoryHandlingStrategy);
        }

        @Override
        public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, boolean isRoot) {
            snapshot.accept(new FileSystemLocationSnapshotVisitor() {
                @Override
                public void visitDirectory(DirectorySnapshot directorySnapshot) {
                    if (directorySnapshotBuilder == null) {
                        directorySnapshotBuilder = MerkleDirectorySnapshotBuilder.noSortingRequired();
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

        private void visitNonDirectoryEntry(FileSystemLocationSnapshot snapshot, boolean isRoot) {
            if (!predicate.test(snapshot, isRoot)) {
                hasBeenFiltered = true;
                currentRootFiltered = true;
                return;
            }
            if (directorySnapshotBuilder == null) {
                newRootsBuilder.add(snapshot);
            } else {
                if (snapshot instanceof FileSystemLeafSnapshot) {
                    directorySnapshotBuilder.visitLeafElement((FileSystemLeafSnapshot) snapshot);
                }
            }
        }

        @Override
        public void leaveDirectory(DirectorySnapshot directorySnapshot, boolean isRoot) {
            boolean excludedDir = directorySnapshotBuilder.leaveDirectory() == null;
            if (excludedDir) {
                currentRootFiltered = true;
                hasBeenFiltered = true;
            }
            if (isRoot) {
                FileSystemLocationSnapshot result = directorySnapshotBuilder.getResult();
                if (result != null) {
                    newRootsBuilder.add(currentRootFiltered ? result : currentRoot);
                }
                directorySnapshotBuilder = null;
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
