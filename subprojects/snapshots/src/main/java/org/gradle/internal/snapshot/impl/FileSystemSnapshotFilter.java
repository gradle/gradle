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
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.RelativePathSegmentsTracker;
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

    private static class FilteringVisitor implements FileSystemSnapshotVisitor {
        private final RelativePathSegmentsTracker relativePathTracker;
        private final SnapshottingFilter.FileSystemSnapshotPredicate predicate;
        private final FileSystemSnapshotVisitor delegate;
        private final AtomicBoolean hasBeenFiltered;

        public FilteringVisitor(SnapshottingFilter.FileSystemSnapshotPredicate predicate, FileSystemSnapshotVisitor delegate, AtomicBoolean hasBeenFiltered) {
            this.predicate = predicate;
            this.delegate = delegate;
            this.hasBeenFiltered = hasBeenFiltered;
            this.relativePathTracker = new RelativePathSegmentsTracker();
        }

        @Override
        public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            boolean root = relativePathTracker.isRoot();
            relativePathTracker.enter(directorySnapshot);
            if (root || predicate.test(directorySnapshot, relativePathTracker.getRelativePath())) {
                delegate.preVisitDirectory(directorySnapshot);
                return true;
            } else {
                hasBeenFiltered.set(true);
            }
            relativePathTracker.leave();
            return false;
        }

        @Override
        public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
            boolean root = relativePathTracker.isRoot();
            relativePathTracker.enter(fileSnapshot);
            Iterable<String> relativePathForFiltering = root ? ImmutableList.of(fileSnapshot.getName()) : relativePathTracker.getRelativePath();
            if (predicate.test(fileSnapshot, relativePathForFiltering)) {
                delegate.visitFile(fileSnapshot);
            } else {
                hasBeenFiltered.set(true);
            }
            relativePathTracker.leave();
        }

        @Override
        public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            relativePathTracker.leave();
            delegate.postVisitDirectory(directorySnapshot);
        }
    }
}
