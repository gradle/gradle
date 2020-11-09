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

package org.gradle.internal.snapshot.impl;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot.FileSystemLocationSnapshotTransformer;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.snapshot.SnapshottingFilter;

import java.util.concurrent.atomic.AtomicBoolean;

public class FileSystemSnapshotFilter {

    private FileSystemSnapshotFilter() {
    }

    public static FileSystemSnapshot filterSnapshot(SnapshottingFilter.FileSystemSnapshotPredicate predicate, FileSystemSnapshot unfiltered) {
        MerkleDirectorySnapshotBuilder builder = MerkleDirectorySnapshotBuilder.noSortingRequired();
        AtomicBoolean hasBeenFiltered = new AtomicBoolean(false);
        unfiltered.accept(new FilteringVisitor(predicate, builder, hasBeenFiltered));
        if (builder.getResult() == null) {
            return FileSystemSnapshot.EMPTY;
        }
        return hasBeenFiltered.get() ? builder.getResult() : unfiltered;
    }

    private static class FilteringVisitor implements FileSystemSnapshotHierarchyVisitor {
        private final RelativePathTracker relativePathTracker = new RelativePathTracker();
        private final SnapshottingFilter.FileSystemSnapshotPredicate predicate;
        private final MerkleDirectorySnapshotBuilder builder;
        private final AtomicBoolean hasBeenFiltered;

        public FilteringVisitor(SnapshottingFilter.FileSystemSnapshotPredicate predicate, MerkleDirectorySnapshotBuilder builder, AtomicBoolean hasBeenFiltered) {
            this.predicate = predicate;
            this.builder = builder;
            this.hasBeenFiltered = hasBeenFiltered;
        }

        @Override
        public void enterDirectory(CompleteDirectorySnapshot directorySnapshot) {
            relativePathTracker.enter(directorySnapshot);
            builder.preVisitDirectory(directorySnapshot);
        }

        @Override
        public SnapshotVisitResult visitEntry(CompleteFileSystemLocationSnapshot snapshot) {
            boolean root = relativePathTracker.isRoot();
            relativePathTracker.enter(snapshot);
            Iterable<String> relativePathForFiltering = root
                ? ImmutableList.of(snapshot.getName())
                : relativePathTracker.getSegments();
            SnapshotVisitResult result;
            boolean forceInclude = snapshot.accept(new FileSystemLocationSnapshotTransformer<Boolean>() {
                @Override
                public Boolean visitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                    return root;
                }

                @Override
                public Boolean visitRegularFile(RegularFileSnapshot fileSnapshot) {
                    return false;
                }

                @Override
                public Boolean visitMissing(MissingFileSnapshot missingSnapshot) {
                    return false;
                }
            });
            if (forceInclude || predicate.test(snapshot, relativePathForFiltering)) {
                builder.visitEntry(snapshot);
                result = SnapshotVisitResult.CONTINUE;
            } else {
                hasBeenFiltered.set(true);
                result = SnapshotVisitResult.SKIP_SUBTREE;
            }
            relativePathTracker.leave();
            return result;
        }

        @Override
        public void leaveDirectory(CompleteDirectorySnapshot directorySnapshot) {
            builder.postVisitDirectory(true, directorySnapshot.getAccessType());
            relativePathTracker.leave();
        }
    }
}
